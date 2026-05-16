package com.ai.gateway.interceptor;

import cn.hutool.json.JSONUtil;
import com.ai.gateway.common.Result;
import com.ai.gateway.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * IP 限流拦截器 — 滑动窗口算法
 * 
 * 使用 Redis ZSet 实现滑动窗口限流，对静态资源和认证接口跳过限流。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitInterceptor implements HandlerInterceptor {

    private static final String IP_LIMIT_PREFIX = "ai_gateway:ip_limit:";
    private static final int IP_MAX_REQUESTS = 600;
    private static final long IP_WINDOW_MILLIS = 60_000;

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);
        String uri = request.getRequestURI();

        if (uri.startsWith("/api/auth/") || uri.contains(".")) {
            return true;
        }

        String key = IP_LIMIT_PREFIX + ip;
        long now = System.currentTimeMillis();
        
        redisTemplate.opsForZSet().add(key, String.valueOf(now), (double) now);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - IP_WINDOW_MILLIS);
        
        Long count = redisTemplate.opsForZSet().zCard(key);
        redisTemplate.expire(key, 65, TimeUnit.SECONDS);

        if (count != null && count > IP_MAX_REQUESTS) {
            log.warn("IP限流触发(滑动窗口): ip={}, count={}, uri={}", ip, count, uri);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(429);
            Result<Void> result = Result.error(429, "请求过于频繁，请稍后重试 (IP: " + ip + ")");
            response.getWriter().write(JSONUtil.toJsonStr(result));
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "0.0.0.0";
    }
}
