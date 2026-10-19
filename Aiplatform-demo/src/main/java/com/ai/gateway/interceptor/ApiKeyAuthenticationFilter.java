package com.ai.gateway.interceptor;

import com.ai.gateway.common.Constants;
import com.ai.gateway.common.Result;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.entity.User;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.service.ApiKeyService;
import com.ai.gateway.service.RateLimitService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 在 Spring Security Filter Chain 内校验平台用户 Token 或 API Key。
 * Controller 仍可从 request attribute 读取兼容字段，同时业务代码可用 SecurityContext 获取主体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHENTICATED_USER_ID_ATTRIBUTE = ApiKeyAuthenticationFilter.class.getName() + ".authenticatedUserId";

    private final ApiKeyService apiKeyService;
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !(path.startsWith("/chat/") || path.equals("/chat")
                || path.startsWith("/v1/ai-doc/") || path.equals("/v1/ai-doc")
                || path.startsWith("/v1/file/") || path.equals("/v1/file"));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // SSE 的 Servlet 异步分派会再次经过 Spring Security；在该分派中从原请求属性恢复
        // 已认证主体，否则响应内容虽已写出，异步完成分派仍会被 AuthorizationFilter 拒绝。
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        try {
            Long userId;
            if (isAsyncDispatch(request)) {
                Object authenticatedUserId = request.getAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE);
                if (!(authenticatedUserId instanceof Long)) {
                    writeError(response, ResultCode.UNAUTHORIZED);
                    return;
                }
                // 异步续派来自已通过认证的同一 Servlet 请求，无需再次消费 API Key 限流额度。
                userId = (Long) authenticatedUserId;
            } else {
                String userToken = request.getHeader(Constants.HEADER_USER_TOKEN);
                userId = userToken != null && !userToken.isBlank()
                        ? authenticateUserToken(request, response, path)
                        : authenticateApiKey(request, response, path);
            }
            if (userId == null) return;

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } finally {
            // 请求主体只在当前 Servlet 请求线程有效，SSE 后台任务通过显式参数接收 userId。
            SecurityContextHolder.clearContext();
        }
    }

    private Long authenticateUserToken(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        String userToken = request.getHeader(Constants.HEADER_USER_TOKEN);
        if (userToken == null || userToken.isBlank()) return null;

        Long userId = tokenService.getUserIdFromToken(userToken);
        User user = userId == null ? null : userService.getUserEntityById(userId);
        if (user == null || user.getStatus() == Constants.USER_STATUS_DISABLED) {
            writeError(response, ResultCode.INVALID_TOKEN);
            return null;
        }
        attachRequestIdentity(request, user.getId(), null);
        log.debug("Spring Security 用户令牌认证通过: userId={}, path={}", userId, path);
        return user.getId();
    }

    private Long authenticateApiKey(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        String apiKey = request.getHeader(Constants.HEADER_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            writeError(response, ResultCode.INVALID_API_KEY);
            return null;
        }
        try {
            ApiKey apiKeyEntity = apiKeyService.validateApiKey(apiKey);
            User user = userService.getUserEntityById(apiKeyEntity.getUserId());
            if (user == null || user.getStatus() == Constants.USER_STATUS_DISABLED) {
                writeError(response, ResultCode.USER_DISABLED);
                return null;
            }
            if (!rateLimitService.allowRequest(apiKey, apiKeyEntity.getRateLimit())) {
                writeError(response, ResultCode.RATE_LIMIT_EXCEEDED);
                return null;
            }
            attachRequestIdentity(request, user.getId(), apiKey);
            log.debug("Spring Security API Key 认证通过: userId={}, path={}", user.getId(), path);
            return user.getId();
        } catch (BusinessException e) {
            writeError(response, e.getCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("API Key 认证异常: path={}", path, e);
            writeError(response, ResultCode.ERROR);
            return null;
        }
    }

    private void attachRequestIdentity(HttpServletRequest request, Long userId, String apiKey) {
        request.setAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE, userId);
        request.setAttribute("userId", userId);
        request.setAttribute("requestId", UUID.randomUUID().toString().replace("-", ""));
        if (apiKey != null) request.setAttribute("apiKey", apiKey);
    }

    private void writeError(HttpServletResponse response, ResultCode code) throws IOException {
        writeError(response, code.getCode(), code.getMessage());
    }

    private void writeError(HttpServletResponse response, Integer code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK); // 与现有 API 业务错误响应协议保持兼容
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> body = Result.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
