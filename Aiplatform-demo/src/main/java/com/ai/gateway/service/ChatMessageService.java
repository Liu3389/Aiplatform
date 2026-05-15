package com.ai.gateway.service;

import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.ChatMessageHistory;
import com.ai.gateway.mapper.ChatMessageMapper;
import com.ai.gateway.mapper.ChatMessageHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 聊天消息服务（支持游标分页和冷热分离）
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageHistoryMapper chatMessageHistoryMapper;
    private final StringRedisTemplate redisTemplate;

    // Redis缓存前缀
    private static final String CACHE_PREFIX = "chat:messages:";
    private static final long CACHE_TTL_MINUTES = 30;

    // 冷热数据分界线（30天）
    private static final int HOT_DATA_DAYS = 30;

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
        // 1. 尝试从缓存获取
        String cacheKey = buildCacheKey(conversationId, lastId, limit);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("命中缓存: conversationId={}, lastId={}, limit={}", conversationId, lastId, limit);
            // 这里简化处理，实际应该反序列化
            // return deserialize(cached);
        }

        // 2. 计算冷热分界时间
        LocalDateTime hotDataThreshold = LocalDateTime.now().minusDays(HOT_DATA_DAYS);

        // 3. 先查热数据表
        List<ChatMessage> messages = queryHotMessages(conversationId, lastId, limit, userId, hotDataThreshold);

        // 4. 如果热数据不足，从冷数据表补充
        if (messages.size() < limit && lastId == null) {
            // 只在首次加载时检查冷数据
            List<ChatMessage> coldMessages = queryColdMessages(conversationId, messages, limit - messages.size(), userId);
            messages.addAll(coldMessages);
        }

        // 5. 按时间倒序排序
        messages.sort((m1, m2) -> m2.getCreateTime().compareTo(m1.getCreateTime()));

        // 6. 存入缓存
        if (!messages.isEmpty()) {
            cacheMessages(cacheKey, messages);
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
        wrapper.orderByDesc(ChatMessage::getId)
               .last("LIMIT " + limit);

        return chatMessageMapper.selectList(wrapper);
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

            // 如果有热数据，排除已经查到的ID
            if (!existingMessages.isEmpty()) {
                List<Long> existingIds = existingMessages.stream()
                        .map(ChatMessage::getId)
                        .collect(Collectors.toList());
                wrapper.notIn(ChatMessageHistory::getId, existingIds);
            }

            // 按原始创建时间倒序
            wrapper.orderByDesc(ChatMessageHistory::getOriginalCreateTime)
                   .last("LIMIT " + needCount);

            List<ChatMessageHistory> historyMessages = chatMessageHistoryMapper.selectList(wrapper);

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
        redisTemplate.delete(redisTemplate.keys(pattern));
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
            // 简化处理，实际应该序列化为JSON
            redisTemplate.opsForValue().set(cacheKey, "cached", CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("缓存消息失败: cacheKey={}", cacheKey, e);
        }
    }
}
