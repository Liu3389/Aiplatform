package com.ai.gateway.interceptor;

import cn.hutool.json.JSONUtil;
import com.ai.gateway.common.Constants;
import com.ai.gateway.common.Result;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.entity.User;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.service.ApiKeyService;
import com.ai.gateway.service.BillingService;
import com.ai.gateway.service.RateLimitService;
import com.ai.gateway.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * API Key鉴权拦截器
 * 负责验证API Key、限流检查、余额预扣
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
    private final BillingService billingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        log.info("========== API Key鉴权拦截器开始 ==========");
        
        // 获取API Key
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

            // 检查用户状态
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

            // 4. 余额检查和预扣（估算最大费用）
            log.info("步骤4: 余额检查...");
            BigDecimal userBalance = billingService.getUserBalance(user.getId());
            log.info("当前余额: userId={}, balance={}", user.getId(), userBalance);
            
            if (userBalance.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("余额不足: userId={}, balance={}", user.getId(), userBalance);
                writeErrorResponse(response, ResultCode.INSUFFICIENT_BALANCE);
                return false;
            }

            // 预扣余额（估算值，实际费用在请求结束后结算）
            String requestId = UUID.randomUUID().toString().replace("-", "");
            BigDecimal preDeductAmount = new BigDecimal("0.1"); // 预设最大费用0.1美元
            
            log.info("步骤5: 开始预扣余额: userId={}, requestId={}, amount={}", user.getId(), requestId, preDeductAmount);
            boolean preDeductSuccess = billingService.preDeductBalance(
                    user.getId(), requestId, preDeductAmount, 300);
            
            log.info("预扣结果: success={}", preDeductSuccess);
            
            if (!preDeductSuccess) {
                log.warn("预扣余额失败: userId={}, requestId={}", user.getId(), requestId);
                writeErrorResponse(response, ResultCode.INSUFFICIENT_BALANCE);
                return false;
            }
            
            log.info("预扣余额成功: userId={}, requestId={}", user.getId(), requestId);

            // 将用户信息和请求ID存入request属性，供后续使用
            request.setAttribute("userId", user.getId());
            request.setAttribute("apiKey", apiKey);
            request.setAttribute("requestId", requestId);
            request.setAttribute("preDeductAmount", preDeductAmount);

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
        response.getWriter().write(JSONUtil.toJsonStr(result));
    }
}
