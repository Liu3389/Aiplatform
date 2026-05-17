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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.UUID;

/**
 * API Key鉴权拦截器
 * 负责验证API Key、限流检查
 * 计费逻辑由 OpenAiChatService 按模式分别处理
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final ApiKeyService apiKeyService;
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getRequestURI();

        // 优先检查 X-User-Token（平台对话点数制，无需 API Key）
        String userToken = request.getHeader(Constants.HEADER_USER_TOKEN);
        if (userToken != null && !userToken.isEmpty()) {
            Long userId = tokenService.getUserIdFromToken(userToken);
            if (userId != null) {
                User user = userService.getUserEntityById(userId);
                if (user != null && user.getStatus() != Constants.USER_STATUS_DISABLED) {
                    String requestId = UUID.randomUUID().toString().replace("-", "");
                    request.setAttribute("userId", user.getId());
                    request.setAttribute("requestId", requestId);
                    log.debug("UserToken鉴权通过: userId={}, path={}", userId, path);
                    return true;
                }
            }
            writeErrorResponse(response, ResultCode.INVALID_TOKEN);
            return false;
        }

        // 降级到 X-API-Key 认证（模型商店功能）
        String apiKey = request.getHeader(Constants.HEADER_API_KEY);
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("认证失败: 缺少 X-User-Token 或 X-API-Key, path={}", path);
            writeErrorResponse(response, ResultCode.INVALID_API_KEY);
            return false;
        }

        try {
            ApiKey apiKeyEntity = apiKeyService.validateApiKey(apiKey);
            User user = userService.getUserEntityById(apiKeyEntity.getUserId());

            if (user.getStatus() == Constants.USER_STATUS_DISABLED) {
                writeErrorResponse(response, ResultCode.USER_DISABLED);
                return false;
            }

            if (!rateLimitService.allowRequest(apiKey, apiKeyEntity.getRateLimit())) {
                writeErrorResponse(response, ResultCode.RATE_LIMIT_EXCEEDED);
                return false;
            }

            String requestId = UUID.randomUUID().toString().replace("-", "");
            request.setAttribute("userId", user.getId());
            request.setAttribute("apiKey", apiKey);
            request.setAttribute("requestId", requestId);

            return true;

        } catch (BusinessException e) {
            writeErrorResponse(response, e.getCode(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("API Key鉴权异常", e);
            writeErrorResponse(response, ResultCode.ERROR);
            return false;
        }
    }

    /**
     * 写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, ResultCode resultCode) 
            throws IOException {
        writeErrorResponse(response, resultCode.getCode(), resultCode.getMessage());
    }

    private void writeErrorResponse(HttpServletResponse response, Integer code, String message) 
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        
        Result<Void> result = Result.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
