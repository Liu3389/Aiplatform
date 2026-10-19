package com.ai.gateway.interceptor;

import cn.hutool.json.JSONUtil;
import com.ai.gateway.common.Result;
import com.ai.gateway.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IP 限流拦截器 — 滑动窗口算法
 * 
 * 使用 Redis ZSet 实现常规 IP 滑动窗口限流，并对验证码签发接口单独限流。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitInterceptor implements HandlerInterceptor {

    private static final String IP_LIMIT_PREFIX = "ai_gateway:ip_limit:";
    private static final int IP_MAX_REQUESTS = 600;
    private static final long IP_WINDOW_MILLIS = 60_000;
    private static final String CAPTCHA_LIMIT_PREFIX = "ai_gateway:captcha_issue_limit:";
    private static final int CAPTCHA_MAX_ISSUES_PER_MINUTE = 10;
    private static final DefaultRedisScript<Long> CAPTCHA_ISSUE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

        String ip = getClientIp(request);
        String uri = request.getRequestURI();
        String servletPath = getApplicationPath(request);

        if (isCaptchaIssueEndpoint(servletPath)) {
            return limitCaptchaIssue(ip, servletPath, response);
        }

        if (servletPath.startsWith("/auth/") || uri.startsWith("/api/auth/") || uri.contains(".")) {
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

    private String getApplicationPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (requestUri == null) {
            return "";
        }
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private boolean isCaptchaIssueEndpoint(String servletPath) {
        return "/auth/login/captcha".equals(servletPath)
                || "/auth/register/captcha".equals(servletPath);
    }

    private boolean limitCaptchaIssue(String ip, String path, HttpServletResponse response) throws Exception {
        String key = CAPTCHA_LIMIT_PREFIX + ip;
        Long count = redisTemplate.execute(CAPTCHA_ISSUE_LIMIT_SCRIPT, List.of(key), "60");
        if (count != null && count > CAPTCHA_MAX_ISSUES_PER_MINUTE) {
            log.warn("验证码签发频率过高: ip={}, count={}, limit={}, path={}",
                    ip, count, CAPTCHA_MAX_ISSUES_PER_MINUTE, path);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(429);
            Result<Void> result = Result.error(429, "验证码请求过于频繁，请稍后再试");
            response.getWriter().write(JSONUtil.toJsonStr(result));
            return false;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        // Nginx overwrites X-Real-IP with $remote_addr. X-Forwarded-For is appended
        // and therefore may begin with a client-controlled value; never use it as identity.
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && realIp.matches("[0-9a-fA-F:.]{2,45}")) {
            return realIp;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "0.0.0.0";
    }
}
