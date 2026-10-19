package com.ai.gateway.service;

import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.ChatMessageHistory;
import com.ai.gateway.mapper.ChatMessageHistoryMapper;
import com.ai.gateway.mapper.ChatMessageMapper;
import com.ai.gateway.mapper.FileParseTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageHistoryMapper chatMessageHistoryMapper;
    private final com.ai.gateway.mapper.ConversationMapper conversationMapper;
    private final AiChargeIntentService aiChargeIntentService;
    private final FileParseTaskMapper fileParseTaskMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.ai.gateway.util.RedisScanUtil redisScanUtil;

    // v2 同时包含 userId 和时间游标，避免旧版错误游标缓存继续污染历史加载。
    private static final String CACHE_PREFIX = "chat:messages:v2:";
    private static final long CACHE_TTL_MINUTES = 30;
    private static final int HOT_DATA_DAYS = 30;

    public ChatMessageService(ChatMessageMapper chatMessageMapper,
                              ChatMessageHistoryMapper chatMessageHistoryMapper,
                              com.ai.gateway.mapper.ConversationMapper conversationMapper,
                              AiChargeIntentService aiChargeIntentService,
                              FileParseTaskMapper fileParseTaskMapper,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              com.ai.gateway.util.RedisScanUtil redisScanUtil) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageHistoryMapper = chatMessageHistoryMapper;
        this.conversationMapper = conversationMapper;
        this.aiChargeIntentService = aiChargeIntentService;
        this.fileParseTaskMapper = fileParseTaskMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisScanUtil = redisScanUtil;
    }

    /**
     * 保存消息
     * 
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param role 角色
     * @param content 内容
     * @param model 模型
     * @param tokens Token数量
     * @return 消息ID
     */
    public Long saveMessage(Long conversationId, Long userId, String role, String content, String model, Integer tokens) {
        return saveMessage(conversationId, userId, role, content, model, tokens, null);
    }

    /**
     * 保存消息（带思考耗时）
     * @param thinkingTime 思考耗时（秒），仅 AI 消息有值，用户消息传 null
     */
    public Long saveMessage(Long conversationId, Long userId, String role, String content, String model, Integer tokens, Double thinkingTime) {
        return saveMessage(conversationId, userId, role, content, model, tokens, thinkingTime, null, null);
    }

    /**
     * 保存消息（带文件元数据，用于持久化用户上传的文件卡片）
     * @param fileUrls  文件 OSS URL 列表，逗号分隔
     * @param metadata  文件详情 JSON（含文件名、大小、类型、OSS URL、上传时间）
     */
    public Long saveMessage(Long conversationId, Long userId, String role, String content, String model,
                            Integer tokens, Double thinkingTime, String fileUrls, String metadata) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setTokens(tokens != null ? tokens : 0);
        message.setThinkingTime(thinkingTime);
        message.setFileUrls(fileUrls);
        message.setMetadata(metadata);
        message.setCreateTime(LocalDateTime.now());

        chatMessageMapper.insert(message);

        // 清除该会话的缓存
        clearCache(conversationId, userId);

        log.info("保存消息成功: conversationId={}, messageId={}, role={}, thinkingTime={}, hasFiles={}",
                conversationId, message.getId(), role, thinkingTime, fileUrls != null);
        return message.getId();
    }

    /**
     * Persist one complete user/assistant turn atomically with the billing intent marker.
     * A message insert, conversation count update, or intent transition failure rolls back
     * the entire turn, leaving recovery free to refund the reservation.
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveConversationTurn(String requestId, Long conversationId, Long userId,
                                     String userContent, String assistantContent, String model,
                                     int totalTokens, Double thinkingTime,
                                     String fileUrls, String metadata) {
        if (requestId == null || requestId.isBlank() || userContent == null || userContent.isBlank()
                || assistantContent == null || assistantContent.isBlank()) {
            throw new IllegalArgumentException("AI 对话需要幂等键、用户消息和非空回复");
        }

        ChatMessage existingUser = findByRequestIdAndRole(requestId, "user");
        ChatMessage existingAssistant = findByRequestIdAndRole(requestId, "assistant");
        if (existingUser != null || existingAssistant != null) {
            if (existingUser == null || existingAssistant == null
                    || !sameTurn(existingUser, conversationId, userId, userContent, model)
                    || !sameTurn(existingAssistant, conversationId, userId, assistantContent, model)) {
                throw new IllegalStateException("幂等请求已有不完整或不匹配的消息记录");
            }
            if (!aiChargeIntentService.markMessagesPersisted(requestId)) {
                throw new IllegalStateException("消息已存在，但点数请求状态不允许标记为已持久化");
            }
            clearCacheAfterCommit(conversationId, userId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        ChatMessage userMessage = newTurnMessage(requestId, conversationId, userId, "user",
                userContent, model, 0, null, fileUrls, metadata, now);
        ChatMessage assistantMessage = newTurnMessage(requestId, conversationId, userId, "assistant",
                assistantContent, model, totalTokens, thinkingTime, null, null, now);
        if (chatMessageMapper.insert(userMessage) != 1 || chatMessageMapper.insert(assistantMessage) != 1) {
            throw new IllegalStateException("聊天消息写入数据库失败");
        }
        if (conversationMapper.incrementConversationMessageCount(conversationId, userId, 2, now) != 1) {
            throw new IllegalStateException("会话不存在或无权写入，消息事务已回滚");
        }
        if (!aiChargeIntentService.markMessagesPersisted(requestId)) {
            throw new IllegalStateException("点数请求状态更新失败，消息事务已回滚");
        }

        clearCacheAfterCommit(conversationId, userId);
        log.info("聊天轮次已原子持久化: conversationId={}, requestId={}, tokens={}",
                conversationId, requestId, totalTokens);
    }

    /** Returns the hot or archived row for recovery and idempotent response replay. */
    public ChatMessage findByRequestIdAndRole(String requestId, String role) {
        if (requestId == null || requestId.isBlank()) return null;
        ChatMessage hot = chatMessageMapper.selectByRequestIdAndRole(requestId, role);
        if (hot != null) return hot;
        ChatMessageHistory history = chatMessageHistoryMapper.selectByRequestIdAndRole(requestId, role);
        if (history == null) return null;
        ChatMessage archived = new ChatMessage();
        archived.setId(history.getId());
        archived.setConversationId(history.getConversationId());
        archived.setUserId(history.getUserId());
        archived.setRequestId(history.getRequestId());
        archived.setRole(history.getRole());
        archived.setContent(history.getContent());
        archived.setModel(history.getModel());
        archived.setTokens(history.getTokens());
        archived.setFileUrls(history.getFileUrls());
        archived.setFileIds(history.getFileIds());
        archived.setMetadata(history.getMetadata());
        archived.setThinkingTime(history.getThinkingTime());
        archived.setCreateTime(history.getOriginalCreateTime());
        return archived;
    }

    private ChatMessage newTurnMessage(String requestId, Long conversationId, Long userId, String role,
                                       String content, String model, int tokens, Double thinkingTime,
                                       String fileUrls, String metadata, LocalDateTime now) {
        ChatMessage message = new ChatMessage();
        message.setRequestId(requestId);
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setTokens(tokens);
        message.setThinkingTime(thinkingTime);
        message.setFileUrls(fileUrls);
        message.setMetadata(metadata);
        message.setCreateTime(now);
        return message;
    }

    private boolean sameTurn(ChatMessage message, Long conversationId, Long userId, String content, String model) {
        return conversationId.equals(message.getConversationId()) && userId.equals(message.getUserId())
                && content.equals(message.getContent()) && model.equals(message.getModel());
    }

    private void clearCacheAfterCommit(Long conversationId, Long userId) {
        Runnable clear = () -> {
            try {
                clearCache(conversationId, userId);
            } catch (Exception e) {
                // Cache eviction must not turn an already committed/chargeable turn into an error.
                log.warn("聊天消息已提交，但缓存失效失败: conversationId={}", conversationId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear.run();
                }
            });
        } else {
            clear.run();
        }
    }

    /**
     * 游标分页查询聊天记录（支持冷热分离）
     * 
     * @param conversationId 会话ID
     * @param lastId 游标ID（上一页最后一条记录的ID，首次查询传null）
     * @param limit 每页条数
     * @param userId 用户ID（用于权限校验）
     * @return 分页结果
     */
    public List<ChatMessage> getMessagesByCursor(Long conversationId, Long lastId, Integer limit, Long userId) {
        return getMessagesByCursor(conversationId, lastId, null, limit, userId);
    }

    /**
     * 跨热表与历史表按时间游标分页。
     *
     * chat_message 和 chat_message_history 的自增主键彼此独立，不能直接用
     * 一个 id 在两张表上向前翻页，否则切到冷数据时会漏掉消息。时间游标作为
     * 主边界，lastId 只用于同一时间戳内的兼容性细分。
     */
    public List<ChatMessage> getMessagesByCursor(Long conversationId, Long lastId,
                                                 LocalDateTime lastCreateTime, Integer limit, Long userId) {
        // 修复 P2-7: 增加会话归属权深度校验，防止越权访问
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ai.gateway.entity.Conversation> convWrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        convWrapper.eq(com.ai.gateway.entity.Conversation::getId, conversationId)
                   .eq(com.ai.gateway.entity.Conversation::getUserId, userId);
        if (conversationMapper.selectCount(convWrapper) == 0) {
            log.warn("非法访问尝试: userId={}, conversationId={}", userId, conversationId);
            return new ArrayList<>();
        }

        int safeLimit = Math.min(Math.max(limit == null ? 15 : limit, 1), 50);
        String cacheKey = buildCacheKey(userId, conversationId, lastId, lastCreateTime, safeLimit);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("[]".equals(cached)) {
                log.info("命中空值缓存: conversationId={}, lastId={}, limit={}", conversationId, lastId, limit);
                return new ArrayList<>();
            }
            try {
                List<ChatMessage> messages = objectMapper.readValue(cached, new TypeReference<List<ChatMessage>>() {});
                log.info("命中缓存: conversationId={}, lastId={}, limit={}, size={}", conversationId, lastId, limit, messages.size());
                return messages;
            } catch (Exception e) {
                log.warn("缓存反序列化失败，回退到数据库查询: {}", e.getMessage());
            }
        }

        LocalDateTime hotDataThreshold = LocalDateTime.now().minusDays(HOT_DATA_DAYS);
        int queryLimit = Math.min(safeLimit + 1, 51);

        // 两张表都按同一个时间边界查询，再在内存中合并排序。多查一条由
        // controller 判断 hasMore，避免“刚好整页”时出现空的下一页。
        List<ChatMessage> hotMessages = queryHotMessages(
                conversationId, lastId, lastCreateTime, queryLimit, userId, hotDataThreshold);
        List<ChatMessage> coldMessages = queryColdMessages(
                conversationId, lastId, lastCreateTime, queryLimit, userId, hotDataThreshold);
        List<ChatMessage> messages = new ArrayList<>(hotMessages.size() + coldMessages.size());
        messages.addAll(hotMessages);
        messages.addAll(coldMessages);
        messages.sort((left, right) -> {
            LocalDateTime leftTime = left.getCreateTime();
            LocalDateTime rightTime = right.getCreateTime();
            if (leftTime == null && rightTime == null) return Long.compare(right.getId(), left.getId());
            if (leftTime == null) return 1;
            if (rightTime == null) return -1;
            int byTime = rightTime.compareTo(leftTime);
            return byTime != 0 ? byTime : Long.compare(right.getId(), left.getId());
        });
        if (messages.size() > queryLimit) {
            messages = new ArrayList<>(messages.subList(0, queryLimit));
        }
        Collections.reverse(messages);

        if (!messages.isEmpty()) {
            cacheMessages(cacheKey, messages);
        } else {
            redisTemplate.opsForValue().set(cacheKey, "[]", 5, TimeUnit.MINUTES);
        }

        log.info("查询聊天记录: conversationId={}, lastId={}, lastCreateTime={}, limit={}, resultSize={}",
                conversationId, lastId, lastCreateTime, safeLimit, messages.size());

        return messages;
    }

    /**
     * 查询热数据（最近30天）
     */
    private List<ChatMessage> queryHotMessages(Long conversationId, Long lastId, LocalDateTime lastCreateTime, Integer limit,
                                                Long userId, LocalDateTime hotDataThreshold) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .eq(ChatMessage::getUserId, userId)
               .ge(ChatMessage::getCreateTime, hotDataThreshold);

        // 新客户端按时间游标，旧客户端仍按主键游标。
        if (lastCreateTime != null) {
            if (lastId != null) {
                // 使用显式 SQL 片段表达 (time,id) 复合游标，避免 LambdaWrapper
                // 在多层 and/or 嵌套时生成错误的括号或把条件串成恒假。
                wrapper.apply("(create_time < {0} OR (create_time = {0} AND id < {1}))",
                        lastCreateTime, lastId);
            } else {
                wrapper.lt(ChatMessage::getCreateTime, lastCreateTime);
            }
        } else if (lastId != null) {
            wrapper.lt(ChatMessage::getId, lastId);
        }

        // 以创建时间为主序，ID只作为同一时间戳内的稳定次序。
        // 历史导入或冷热迁移后，ID不一定再严格代表真实创建时间。
        wrapper.orderByDesc(ChatMessage::getCreateTime)
               .orderByDesc(ChatMessage::getId);

        int safeLimit = Math.min(Math.max(limit == null ? 1 : limit, 1), 51);
        Page<ChatMessage> page = new Page<>(1, safeLimit);
        return chatMessageMapper.selectList(page, wrapper);
    }

    /**
     * 查询冷数据（30天前）
     */
    private List<ChatMessage> queryColdMessages(Long conversationId, Long lastId, LocalDateTime lastCreateTime,
                                                 int limit, Long userId, LocalDateTime hotDataThreshold) {
        try {
            // 从 chat_message_history 表查询
            LambdaQueryWrapper<ChatMessageHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessageHistory::getConversationId, conversationId)
                   .eq(ChatMessageHistory::getUserId, userId);

            LocalDateTime cursorTime = lastCreateTime != null ? lastCreateTime : LocalDateTime.now();
            if (lastCreateTime != null && lastId != null) {
                wrapper.apply("(original_create_time < {0} OR (original_create_time = {0} AND id < {1}))",
                        cursorTime, lastId);
            } else if (lastCreateTime != null) {
                wrapper.lt(ChatMessageHistory::getOriginalCreateTime, cursorTime);
            } else {
                // 旧客户端只传 lastId。历史表主键独立，无法做精确跨表
                // 游标，此处仍以归档时间边界返回首批冷数据，新的客户端不会走到这里。
                wrapper.lt(ChatMessageHistory::getOriginalCreateTime, cursorTime);
            }

            // 按原始创建时间倒序
            wrapper.orderByDesc(ChatMessageHistory::getOriginalCreateTime)
                   .orderByDesc(ChatMessageHistory::getId);

            int safeLimit = Math.min(Math.max(limit, 1), 51);
            Page<ChatMessageHistory> page = new Page<>(1, safeLimit);
            List<ChatMessageHistory> historyMessages = chatMessageHistoryMapper.selectList(page, wrapper);

            // 转换为 ChatMessage 对象
            return historyMessages.stream().map(history -> {
                ChatMessage message = new ChatMessage();
                message.setId(history.getId());
                message.setConversationId(history.getConversationId());
                message.setUserId(history.getUserId());
                message.setRole(history.getRole());
                message.setContent(history.getContent());
                message.setModel(history.getModel());
                message.setTokens(history.getTokens());
                message.setFileUrls(history.getFileUrls());
                message.setMetadata(history.getMetadata());
                message.setCreateTime(history.getOriginalCreateTime());
                return message;
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("查询冷数据失败: conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取会话的最新N条消息（首页加载）
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param limit 条数（默认10）
     * @return 消息列表（时间倒序）
     */
    public List<ChatMessage> getLatestMessages(Long conversationId, Long userId, Integer limit) {
        return getMessagesByCursor(conversationId, null, null, limit, userId);
    }

    /**
     * 删除单条消息（按 conversationId + userId + role + content 精确定位最近一条）
     * 用于前端"删除这条提问/回复"功能，确保跨设备同步时消息真正从 MySQL 移除
     *
     * @param conversationId 会话ID
     * @param userId 用户ID（权限校验）
     * @param role 消息角色
     * @param content 消息完整内容
     * @return 是否删除成功
     */
    public boolean deleteMessage(Long conversationId, Long userId, String role, String content) {
        if (conversationId == null || userId == null || role == null || content == null) {
            return false;
        }
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .eq(ChatMessage::getUserId, userId)
               .eq(ChatMessage::getRole, role)
               .eq(ChatMessage::getContent, content)
               .orderByDesc(ChatMessage::getId)
               .last("LIMIT 1");
        List<ChatMessage> targets = chatMessageMapper.selectList(wrapper);
        if (targets.isEmpty()) {
            log.info("未找到匹配消息: conversationId={}, role={}, contentLen={}",
                    conversationId, role, content.length());
            return false;
        }
        int rows = chatMessageMapper.deleteById(targets.get(0).getId());
        if (rows > 0) {
            clearCache(conversationId, userId);
            log.info("删除消息成功: conversationId={}, messageId={}, role={}",
                    conversationId, targets.get(0).getId(), role);
        }
        return rows > 0;
    }

    /**
     * 清除会话缓存
     */
    private void clearCache(Long conversationId, Long userId) {
        String pattern = CACHE_PREFIX + userId + ":" + conversationId + ":*";
        redisScanUtil.deleteByPattern(pattern);
        log.debug("清除会话缓存: conversationId={}", conversationId);
    }

    /**
     * 构建缓存Key
     */
    private String buildCacheKey(Long userId, Long conversationId, Long lastId,
                                 LocalDateTime lastCreateTime, Integer limit) {
        String cursor = lastCreateTime != null
                ? "time-" + lastCreateTime
                : (lastId != null ? "id-" + lastId : "first");
        return CACHE_PREFIX + userId + ":" + conversationId + ":" + cursor + ":" + limit;
    }

    /**
     * 缓存消息列表
     */
    private void cacheMessages(String cacheKey, List<ChatMessage> messages) {
        try {
            String json = objectMapper.writeValueAsString(messages);
            long randomOffset = ThreadLocalRandom.current().nextInt(0, (int)(CACHE_TTL_MINUTES * 0.2));
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_MINUTES + randomOffset, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("缓存消息失败: cacheKey={}", cacheKey, e);
        }
    }

    public List<String> getFileUrlsByConversationId(Long conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .isNotNull(ChatMessage::getFileUrls)
               .select(ChatMessage::getFileUrls);
        
        java.util.Set<String> allUrls = new java.util.HashSet<>();
        for (ChatMessage msg : chatMessageMapper.selectList(wrapper)) {
            if (msg.getFileUrls() != null) {
                try {
                    List<String> urls = objectMapper.readValue(msg.getFileUrls(), new TypeReference<List<String>>() {});
                    allUrls.addAll(urls);
                } catch (Exception e) {
                    log.warn("解析 file_urls 失败: conversationId={}", conversationId, e);
                }
            }
        }
        return new ArrayList<>(allUrls);
    }

    /**
     * 获取会话关联的文件ID列表（去重）
     */
    public List<String> getFileIdsByConversationId(Long conversationId) {
        // 从热数据表查询
        LambdaQueryWrapper<ChatMessage> hotWrapper = new LambdaQueryWrapper<>();
        hotWrapper.eq(ChatMessage::getConversationId, conversationId)
                .isNotNull(ChatMessage::getFileIds)
                .select(ChatMessage::getFileIds);

        // 从冷数据表查询
        LambdaQueryWrapper<ChatMessageHistory> coldWrapper = new LambdaQueryWrapper<>();
        coldWrapper.eq(ChatMessageHistory::getConversationId, conversationId)
                .isNotNull(ChatMessageHistory::getFileIds)
                .select(ChatMessageHistory::getFileIds);

        java.util.Set<String> allFileIds = new java.util.HashSet<>();

        // 合并热数据
        for (ChatMessage msg : chatMessageMapper.selectList(hotWrapper)) {
            if (msg.getFileIds() != null) {
                try {
                    List<String> fileIds = objectMapper.readValue(msg.getFileIds(), new TypeReference<List<String>>() {
                    });
                    allFileIds.addAll(fileIds);
                } catch (Exception e) {
                    log.warn("解析 file_ids 失败: conversationId={}", conversationId, e);
                }
            }
        }

        // 合并冷数据
        for (ChatMessageHistory msg : chatMessageHistoryMapper.selectList(coldWrapper)) {
            if (msg.getFileIds() != null) {
                try {
                    List<String> fileIds = objectMapper.readValue(msg.getFileIds(), new TypeReference<List<String>>() {
                    });
                    allFileIds.addAll(fileIds);
                } catch (Exception e) {
                    log.warn("解析冷数据 file_ids 失败: conversationId={}", conversationId, e);
                }
            }
        }

        return new ArrayList<>(allFileIds);
    }
}
