package com.ai.gateway.service;

import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.ChatMessageHistory;
import com.ai.gateway.mapper.ChatMessageMapper;
import com.ai.gateway.mapper.ChatMessageHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.ai.gateway.util.RedisScanUtil redisScanUtil;

    private static final String CACHE_PREFIX = "chat:messages:";
    private static final long CACHE_TTL_MINUTES = 30;
    private static final int HOT_DATA_DAYS = 30;

    public ChatMessageService(ChatMessageMapper chatMessageMapper,
                              ChatMessageHistoryMapper chatMessageHistoryMapper,
                              com.ai.gateway.mapper.ConversationMapper conversationMapper,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              com.ai.gateway.util.RedisScanUtil redisScanUtil) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageHistoryMapper = chatMessageHistoryMapper;
        this.conversationMapper = conversationMapper;
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
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setTokens(tokens != null ? tokens : 0);
        message.setCreateTime(LocalDateTime.now());

        chatMessageMapper.insert(message);

        // 清除该会话的缓存
        clearCache(conversationId);

        log.info("保存消息成功: conversationId={}, messageId={}, role={}", conversationId, message.getId(), role);
        return message.getId();
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
        // 修复 P2-7: 增加会话归属权深度校验，防止越权访问
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ai.gateway.entity.Conversation> convWrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        convWrapper.eq(com.ai.gateway.entity.Conversation::getId, conversationId)
                   .eq(com.ai.gateway.entity.Conversation::getUserId, userId);
        if (conversationMapper.selectCount(convWrapper) == 0) {
            log.warn("非法访问尝试: userId={}, conversationId={}", userId, conversationId);
            return new ArrayList<>();
        }

        String cacheKey = buildCacheKey(conversationId, lastId, limit);
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

        List<ChatMessage> messages = queryHotMessages(conversationId, lastId, limit, userId, hotDataThreshold);

        if (messages.size() < limit && lastId == null) {
            List<ChatMessage> coldMessages = queryColdMessages(conversationId, messages, limit - messages.size(), userId);
            messages.addAll(coldMessages);
        }

        Collections.reverse(messages);

        if (!messages.isEmpty()) {
            cacheMessages(cacheKey, messages);
        } else {
            redisTemplate.opsForValue().set(cacheKey, "[]", 5, TimeUnit.MINUTES);
        }

        log.info("查询聊天记录: conversationId={}, lastId={}, limit={}, resultSize={}", 
                conversationId, lastId, limit, messages.size());

        return messages;
    }

    /**
     * 查询热数据（最近30天）
     */
    private List<ChatMessage> queryHotMessages(Long conversationId, Long lastId, Integer limit, 
                                                Long userId, LocalDateTime hotDataThreshold) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .eq(ChatMessage::getUserId, userId)
               .ge(ChatMessage::getCreateTime, hotDataThreshold);

        // 游标分页：查询比lastId更早的记录
        if (lastId != null) {
            wrapper.lt(ChatMessage::getId, lastId);
        }

        // 按ID倒序（等同于时间倒序，因为ID是自增的）
        wrapper.orderByDesc(ChatMessage::getId);

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Page<ChatMessage> page = new Page<>(1, safeLimit);
        return chatMessageMapper.selectList(page, wrapper);
    }

    /**
     * 查询冷数据（30天前）
     */
    private List<ChatMessage> queryColdMessages(Long conversationId, List<ChatMessage> existingMessages, 
                                                 int needCount, Long userId) {
        if (needCount <= 0) {
            return new ArrayList<>();
        }

        try {
            // 从 chat_message_history 表查询
            LambdaQueryWrapper<ChatMessageHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessageHistory::getConversationId, conversationId)
                   .eq(ChatMessageHistory::getUserId, userId);

            // 优化：使用最早的热数据时间作为游标，避免使用 notIn
            if (!existingMessages.isEmpty()) {
                LocalDateTime earliestHotTime = existingMessages.stream()
                        .map(ChatMessage::getCreateTime)
                        .min(LocalDateTime::compareTo)
                        .orElse(LocalDateTime.now());
                wrapper.lt(ChatMessageHistory::getOriginalCreateTime, earliestHotTime);
            }

            // 按原始创建时间倒序
            wrapper.orderByDesc(ChatMessageHistory::getOriginalCreateTime);

            int safeLimit = Math.min(Math.max(needCount, 1), 100);
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
        return getMessagesByCursor(conversationId, null, limit, userId);
    }

    /**
     * 清除会话缓存
     */
    private void clearCache(Long conversationId) {
        String pattern = CACHE_PREFIX + conversationId + ":*";
        redisScanUtil.deleteByPattern(pattern);
        log.debug("清除会话缓存: conversationId={}", conversationId);
    }

    /**
     * 构建缓存Key
     */
    private String buildCacheKey(Long conversationId, Long lastId, Integer limit) {
        return CACHE_PREFIX + conversationId + ":" + (lastId != null ? lastId : "first") + ":" + limit;
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
}
