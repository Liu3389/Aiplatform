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
    private final com.ai.gateway.util.RedisScanUtil redisScanUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String CONVERSATION_PREFIX = "conversation:data:";
    private static final String VERSION_PREFIX = "conversation:version:";
    private static final String UPDATE_LOCK_PREFIX = "lock:conversation:update:";
    private static final long CONVERSATION_TTL_DAYS = 90;
    private static final long UPDATE_LOCK_SECONDS = 10;

    /**
     * 保存对话数据到服务端
     */
    public long saveConversations(Long userId, List<Map<String, Object>> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return 0;
        }
        
        try {
            // 修复 P1-9: 为每个会话单独存储，而不是存一个巨大的 List
            for (Map<String, Object> conv : conversations) {
                String convId = (String) conv.get("id");
                if (convId != null) {
                    String key = CONVERSATION_PREFIX + userId + ":" + convId;
                    String jsonData = objectMapper.writeValueAsString(conv);
                    redisTemplate.opsForValue().set(key, jsonData, CONVERSATION_TTL_DAYS, TimeUnit.DAYS);
                }
            }
            
            long newVersion = System.currentTimeMillis();
            redisTemplate.opsForValue().set(VERSION_PREFIX + userId, String.valueOf(newVersion), 
                                            CONVERSATION_TTL_DAYS, TimeUnit.DAYS);
            
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
        java.util.Set<String> keys = redisScanUtil.scanKeys(CONVERSATION_PREFIX + userId + ":*");
        if (keys.isEmpty()) return new ArrayList<>();
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : keys) {
            String jsonData = redisTemplate.opsForValue().get(key);
            if (jsonData != null) {
                try {
                    result.add(objectMapper.readValue(jsonData, Map.class));
                } catch (Exception e) {
                    log.warn("解析会话数据失败: key={}", key);
                }
            }
        }
        return result;
    }

    /**
     * 获取当前版本号
     * @param userId 用户ID
     * @return 版本号，如果没有则返回0
     */
    public long getVersion(Long userId) {
        String versionKey = VERSION_PREFIX + userId;
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
        redisScanUtil.deleteByPattern(CONVERSATION_PREFIX + userId + ":*");
        redisTemplate.delete(VERSION_PREFIX + userId);
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
        
        String lockKey = UPDATE_LOCK_PREFIX + userId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", UPDATE_LOCK_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new RuntimeException("对话正在同步中，请稍后重试");
        }
        
        try {
            List<Map<String, Object>> existingConversations = getConversations(userId);
            
            for (Map<String, Object> newConv : newConversations) {
                String newConvId = (String) newConv.get("id");
                boolean found = false;
                
                for (int i = 0; i < existingConversations.size(); i++) {
                    String existingConvId = (String) existingConversations.get(i).get("id");
                    if (existingConvId != null && existingConvId.equals(newConvId)) {
                        existingConversations.set(i, newConv);
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    existingConversations.add(newConv);
                }
            }
            
            return saveConversations(userId, existingConversations);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
