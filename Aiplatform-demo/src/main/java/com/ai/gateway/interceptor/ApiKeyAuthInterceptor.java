package com.ai.gateway.interceptor;

import com.ai.gateway.common.Constants;
import com.ai.gateway.common.Result;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.entity.User;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.service.ApiKeyService;
import com.ai.gateway.service.RateLimitService;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        log.info("========== API Key鉴权拦截器开始 ==========");
        
        String apiKey = request.getHeader(Constants.HEADER_API_KEY);
        log.info("请求路径: {}, API Key: {}", request.getRequestURI(), apiKey != null ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "null");
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("API Key为空");
            writeErrorResponse(response, ResultCode.INVALID_API_KEY);
            return false;
        }

        try {
            // 1. 验证API Key
            log.info("步骤1: 验证API Key...");
            ApiKey apiKeyEntity = apiKeyService.validateApiKey(apiKey);
            log.info("API Key验证通过: id={}, userId={}", apiKeyEntity.getId(), apiKeyEntity.getUserId());
            
            // 2. 查询用户信息
            log.info("步骤2: 查询用户信息...");
            User user = userService.getUserEntityById(apiKeyEntity.getUserId());
            log.info("用户信息查询成功: id={}, username={}, status={}", user.getId(), user.getUsername(), user.getStatus());

            if (user.getStatus() == Constants.USER_STATUS_DISABLED) {
                log.warn("用户已被禁用: userId={}", user.getId());
                writeErrorResponse(response, ResultCode.USER_DISABLED);
                return false;
            }

            // 3. 限流检查
            log.info("步骤3: 限流检查...");
            if (!rateLimitService.allowRequest(apiKey, apiKeyEntity.getRateLimit())) {
                log.warn("限流检查失败: apiKey={}, rateLimit={}", apiKey, apiKeyEntity.getRateLimit());
                writeErrorResponse(response, ResultCode.RATE_LIMIT_EXCEEDED);
                return false;
            }
            log.info("限流检查通过");

            String requestId = UUID.randomUUID().toString().replace("-", "");

            request.setAttribute("userId", user.getId());
            request.setAttribute("apiKey", apiKey);
            request.setAttribute("requestId", requestId);

            log.info("========== API Key鉴权通过 ==========");
            return true;

        } catch (BusinessException e) {
            log.error("业务异常: code={}, message={}", e.getCode(), e.getMessage());
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
