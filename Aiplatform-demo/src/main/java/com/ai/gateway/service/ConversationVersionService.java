package com.ai.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 对话版本管理服务 - 实现服务端对话存储和版本控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationVersionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String CONVERSATION_DATA_PREFIX = "conversation:data:";
    private static final String CONVERSATION_VERSION_PREFIX = "conversation:version:";
    private static final long CONVERSATION_TTL_DAYS = 90;

    /**
     * 保存对话数据到服务端
     * @param userId 用户ID
     * @param conversations 对话列表
     * @return 版本号
     */
    public long saveConversations(Long userId, List<Map<String, Object>> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return 0;
        }
        
        String dataKey = CONVERSATION_DATA_PREFIX + userId;
        String versionKey = CONVERSATION_VERSION_PREFIX + userId;
        
        try {
            // 序列化对话数据
            String jsonData = objectMapper.writeValueAsString(conversations);
            
            // 生成新版本号（使用时间戳）
            long newVersion = System.currentTimeMillis();
            
            // 保存到Redis
            redisTemplate.opsForValue().set(dataKey, jsonData, CONVERSATION_TTL_DAYS, TimeUnit.DAYS);
            redisTemplate.opsForValue().set(versionKey, String.valueOf(newVersion), CONVERSATION_TTL_DAYS, TimeUnit.DAYS);
            
            log.info("对话数据保存成功: userId={}, conversationCount={}, version={}", 
                    userId, conversations.size(), newVersion);
            
            return newVersion;
        } catch (Exception e) {
            log.error("保存对话数据失败: userId={}", userId, e);
            throw new RuntimeException("保存对话数据失败", e);
        }
    }

    /**
     * 获取对话数据
     * @param userId 用户ID
     * @return 对话列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getConversations(Long userId) {
        String dataKey = CONVERSATION_DATA_PREFIX + userId;
        String jsonData = redisTemplate.opsForValue().get(dataKey);
        
        if (jsonData == null || jsonData.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(jsonData, List.class);
        } catch (Exception e) {
            log.error("获取对话数据失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取当前版本号
     * @param userId 用户ID
     * @return 版本号，如果没有则返回0
     */
    public long getVersion(Long userId) {
        String versionKey = CONVERSATION_VERSION_PREFIX + userId;
        String versionStr = redisTemplate.opsForValue().get(versionKey);
        
        if (versionStr == null) {
            return 0;
        }
        
        try {
            return Long.parseLong(versionStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 检查本地缓存是否过期
     * @param userId 用户ID
     * @param localVersion 本地版本号
     * @return true-需要更新，false-无需更新
     */
    public boolean isCacheExpired(Long userId, long localVersion) {
        long serverVersion = getVersion(userId);
        return serverVersion > localVersion;
    }

    /**
     * 清空用户对话数据（账号切换时调用）
     * @param userId 用户ID
     */
    public void clearConversations(Long userId) {
        String dataKey = CONVERSATION_DATA_PREFIX + userId;
        String versionKey = CONVERSATION_VERSION_PREFIX + userId;
        
        redisTemplate.delete(dataKey);
        redisTemplate.delete(versionKey);
        
        log.info("对话数据已清空: userId={}", userId);
    }

    /**
     * 增量更新对话数据
     * @param userId 用户ID
     * @param newConversations 新增或更新的对话
     * @return 新版本号
     */
    @SuppressWarnings("unchecked")
    public long updateConversations(Long userId, List<Map<String, Object>> newConversations) {
        if (newConversations == null || newConversations.isEmpty()) {
            return getVersion(userId);
        }
        
        // 获取现有对话
        List<Map<String, Object>> existingConversations = getConversations(userId);
        
        // 合并对话（根据conversationId去重和更新）
        for (Map<String, Object> newConv : newConversations) {
            String newConvId = (String) newConv.get("id");
            boolean found = false;
            
            for (int i = 0; i < existingConversations.size(); i++) {
                String existingConvId = (String) existingConversations.get(i).get("id");
                if (existingConvId != null && existingConvId.equals(newConvId)) {
                    // 更新现有对话
                    existingConversations.set(i, newConv);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                // 添加新对话
                existingConversations.add(newConv);
            }
        }
        
        // 保存更新后的对话
        return saveConversations(userId, existingConversations);
    }
}
