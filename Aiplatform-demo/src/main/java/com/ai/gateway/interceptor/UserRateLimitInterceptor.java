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
 * 用户限流拦截器 - 防止恶意刷接口和爬虫
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String USER_RATE_LIMIT_PREFIX = "rate_limit:user:";
    private static final int MAX_REQUESTS_PER_MINUTE = 60; // 每分钟最大请求数
    private static final long WINDOW_SECONDS = 60; // 时间窗口60秒

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取用户ID（从Token中解析，这里简化处理）
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            // 如果没有用户ID，跳过限流（由其他拦截器处理认证）
            return true;
        }
        
        String key = USER_RATE_LIMIT_PREFIX + userId;
        
        // 使用Redis实现滑动窗口限流
        Long currentCount = redisTemplate.opsForValue().increment(key);
        
        if (currentCount != null && currentCount == 1) {
            // 第一次请求，设置过期时间
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        
        if (currentCount != null && currentCount > MAX_REQUESTS_PER_MINUTE) {
            // 超过限流阈值
            log.warn("用户请求频率过高: userId={}, count={}", userId, currentCount);
            
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
