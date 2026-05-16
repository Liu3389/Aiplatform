package com.ai.gateway.interceptor;

import com.ai.gateway.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 用户限流拦截器 — 滑动窗口算法
 * 
 * 使用 Redis ZSet 实现滑动窗口限流，相比固定窗口计数器：
 * 1. 支持突发流量（窗口边界平滑过渡）
 * 2. 不会出现窗口切换时的双倍请求
 * 3. 更精确的请求频率控制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String USER_RATE_LIMIT_PREFIX = "rate_limit:user:";
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final long WINDOW_MILLIS = 60_000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return true;
        }
        
        String key = USER_RATE_LIMIT_PREFIX + userId;
        long now = System.currentTimeMillis();
        
        redisTemplate.opsForZSet().add(key, String.valueOf(now), (double) now);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - WINDOW_MILLIS);
        
        Long count = redisTemplate.opsForZSet().zCard(key);
        redisTemplate.expire(key, 65, TimeUnit.SECONDS);
        
        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            log.warn("用户请求频率过高(滑动窗口): userId={}, count={}", userId, count);
            
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            
            Result<Void> result = Result.error(429, 
                "请求过于频繁，请稍后再试（限制：" + MAX_REQUESTS_PER_MINUTE + "次/分钟）");
            
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }
        
        return true;
    }
}
