package com.ai.gateway.service;

import com.ai.gateway.util.RedisScanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 统计数据缓存服务
 * 使用Redis缓存统计结果，避免频繁查询数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsCacheService {
    
    private final StringRedisTemplate redisTemplate;
    private final RedisScanUtil redisScanUtil;
    private final ObjectMapper objectMapper;
    
    // Redis Key前缀
    private static final String CACHE_PREFIX = "stats:";
    private static final String DASHBOARD_CACHE = CACHE_PREFIX + "dashboard:";
    private static final String USER_STATS_CACHE = CACHE_PREFIX + "user:";
    private static final String CONVERSATION_STATS_CACHE = CACHE_PREFIX + "conversation:";
    private static final String RETENTION_CACHE = CACHE_PREFIX + "retention:";
    
    // 默认缓存时间（秒）
    private static final long DEFAULT_TTL = 3600; // 1小时
    private static final long DAILY_TTL = 86400;  // 24小时
    
    /**
     * 缓存仪表盘数据
     */
    public void cacheDashboardStats(String period, Object data) {
        String key = DASHBOARD_CACHE + period;
        try {
            String json = objectMapper.writeValueAsString(data);
            long randomOffset = ThreadLocalRandom.current().nextInt(0, (int)(DAILY_TTL * 0.1));
            redisTemplate.opsForValue().set(key, json, DAILY_TTL + randomOffset, TimeUnit.SECONDS);
            log.debug("缓存仪表盘数据: {}", key);
        } catch (JsonProcessingException e) {
            log.error("缓存仪表盘数据失败", e);
        }
    }
    
    /**
     * 获取缓存的仪表盘数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getDashboardStats(String period, Class<T> clazz) {
        String key = DASHBOARD_CACHE + period;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, clazz);
            }
        } catch (Exception e) {
            log.error("获取缓存仪表盘数据失败", e);
        }
        return null;
    }
    
    /**
     * 缓存用户统计数据
     */
    public void cacheUserStats(Object data) {
        String key = USER_STATS_CACHE + "summary";
        cacheObject(key, data, DEFAULT_TTL);
    }
    
    /**
     * 获取缓存的用户统计数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getUserStats(Class<T> clazz) {
        String key = USER_STATS_CACHE + "summary";
        return getObject(key, clazz);
    }
    
    /**
     * 缓存对话统计数据
     */
    public void cacheConversationStats(Object data) {
        String key = CONVERSATION_STATS_CACHE + "summary";
        cacheObject(key, data, DEFAULT_TTL);
    }
    
    /**
     * 获取缓存的对话统计数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getConversationStats(Class<T> clazz) {
        String key = CONVERSATION_STATS_CACHE + "summary";
        return getObject(key, clazz);
    }
    
    /**
     * 缓存留存统计数据
     */
    public void cacheRetentionStats(String period, Object data) {
        String key = RETENTION_CACHE + period;
        cacheObject(key, data, DAILY_TTL);
    }
    
    /**
     * 获取缓存的留存统计数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getRetentionStats(String period, Class<T> clazz) {
        String key = RETENTION_CACHE + period;
        return getObject(key, clazz);
    }
    
    /**
     * 通用缓存对象方法
     */
    private void cacheObject(String key, Object data, long ttl) {
        try {
            String json = objectMapper.writeValueAsString(data);
            long randomOffset = ThreadLocalRandom.current().nextInt(0, (int)(ttl * 0.1));
            redisTemplate.opsForValue().set(key, json, ttl + randomOffset, TimeUnit.SECONDS);
            log.debug("缓存数据: {}, TTL: {}s", key, ttl);
        } catch (JsonProcessingException e) {
            log.error("缓存数据失败: {}", key, e);
        }
    }
    
    /**
     * 通用获取对象方法
     */
    @SuppressWarnings("unchecked")
    private <T> T getObject(String key, Class<T> clazz) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, clazz);
            }
        } catch (Exception e) {
            log.error("获取缓存数据失败: {}", key, e);
        }
        return null;
    }
    
    /**
     * 清除所有统计缓存
     */
    public void clearAllCache() {
        redisScanUtil.deleteByPattern(CACHE_PREFIX + "*");
        log.info("清除所有统计缓存");
    }
    
    /**
     * 清除指定类型的缓存
     */
    public void clearCache(String type) {
        String pattern = CACHE_PREFIX + type + ":*";
        redisScanUtil.deleteByPattern(pattern);
        log.info("清除缓存: {}", type);
    }
}
