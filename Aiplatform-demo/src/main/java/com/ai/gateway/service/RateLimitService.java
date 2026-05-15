package com.ai.gateway.service;

import cn.hutool.core.io.IoUtil;
import com.ai.gateway.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;

/**
 * 限流服务 - 基于Redis Lua脚本实现分布式限流
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> rateLimitScript;

    /**
     * 初始化Lua脚本
     */
    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        rateLimitScript.setResultType(Long.class);
        log.info("限流Lua脚本加载成功");
    }

    /**
     * 检查是否允许请求通过（令牌桶算法）
     * 
     * @param apiKey API Key
     * @param rateLimit 限流阈值（每分钟请求数）
     * @return true-允许通过，false-拒绝请求
     */
    public boolean allowRequest(String apiKey, Integer rateLimit) {
        try {
            String key = Constants.REDIS_RATE_LIMIT_PREFIX + apiKey;
            long currentTime = System.currentTimeMillis() / 1000; // 转换为秒
            
            // 使用 StringRedisTemplate 执行Lua脚本，确保参数为纯字符串
            Long result = stringRedisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                rateLimit.toString(),
                String.valueOf(currentTime),
                String.valueOf(Constants.RATE_LIMIT_WINDOW_SECONDS)
            );

            return result != null && result == 1;
        } catch (Exception e) {
            log.error("限流检查失败: apiKey={}", maskApiKey(apiKey), e);
            // 发生异常时放行，避免影响正常业务
            return true;
        }
    }

    /**
     * 获取当前剩余请求次数
     * 
     * @param apiKey API Key
     * @param rateLimit 限流阈值
     * @return 剩余请求次数
     */
    public Integer getRemainingRequests(String apiKey, Integer rateLimit) {
        try {
            String key = Constants.REDIS_RATE_LIMIT_PREFIX + apiKey;
            Object currentCount = redisTemplate.opsForValue().get(key);
            
            if (currentCount == null) {
                return rateLimit;
            }
            
            int count = Integer.parseInt(currentCount.toString());
            return Math.max(0, rateLimit - count);
        } catch (Exception e) {
            log.error("获取剩余请求次数失败: apiKey={}", maskApiKey(apiKey), e);
            return 0;
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
