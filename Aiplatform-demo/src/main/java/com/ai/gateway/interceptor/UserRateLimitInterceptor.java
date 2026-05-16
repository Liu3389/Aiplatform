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
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return true;
        }
        
        String key = USER_RATE_LIMIT_PREFIX + userId;
        
        Long currentCount = redisTemplate.opsForValue().increment(key);
        
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        
        if (currentCount != null && currentCount > MAX_REQUESTS_PER_MINUTE) {
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
