package com.ai.gateway.interceptor;

import cn.hutool.json.JSONUtil;
import com.ai.gateway.common.Result;
import com.ai.gateway.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitInterceptor implements HandlerInterceptor {

    private static final String IP_LIMIT_PREFIX = "ai_gateway:ip_limit:";
    private static final int IP_MAX_REQUESTS = 600;
    private static final long IP_WINDOW_SECONDS = 60;

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);
        String uri = request.getRequestURI();

        // Skip static resources and auth endpoints from IP limiting
        if (uri.startsWith("/api/auth/") || uri.contains(".")) {
            return true;
        }

        String key = IP_LIMIT_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, IP_WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count != null && count > IP_MAX_REQUESTS) {
            log.warn("IP限流触发: ip={}, count={}, uri={}", ip, count, uri);
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
