package com.ai.gateway.service;

import com.ai.gateway.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class BillingService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PointsService pointsService;
    private final CacheService cacheService;

    public BillingService(StringRedisTemplate stringRedisTemplate, 
                         PointsService pointsService,
                         CacheService cacheService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.pointsService = pointsService;
        this.cacheService = cacheService;
    }

    private DefaultRedisScript<List> billingScript;
    private DefaultRedisScript<List> settlementScript;

    @PostConstruct
    public void init() {
        billingScript = new DefaultRedisScript<>();
        billingScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/billing.lua")));
        billingScript.setResultType(List.class);

        settlementScript = new DefaultRedisScript<>();
        settlementScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/settlement.lua")));
        settlementScript.setResultType(List.class);

        log.info("计费Lua脚本加载成功");
    }

    public boolean preDeductBalance(Long userId, String requestId, BigDecimal preDeductAmount, int expireTime) {
        if (preDeductAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        try {
            String balanceKey = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            String preDeductKey = Constants.REDIS_BILLING_PRE_DEDUCT_PREFIX + requestId;

            List<String> keys = java.util.Arrays.asList(balanceKey, preDeductKey);

            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) stringRedisTemplate.execute(
                billingScript, keys,
                userId.toString(), preDeductAmount.toPlainString(), requestId, String.valueOf(expireTime)
            );

            if (result != null && !result.isEmpty()) {
                Object successObj = result.get(0);
                long success = successObj instanceof Number ? ((Number) successObj).longValue() : Long.parseLong(successObj.toString());
                log.info("预扣余额: userId={}, amount={}, success={}", userId, preDeductAmount, success);
                return success == 1;
            }
            log.warn("预扣余额返回空: userId={}", userId);
            return false;
        } catch (Exception e) {
            log.error("预扣余额失败: userId={}, requestId={}", userId, requestId, e);
            return false;
        }
    }

    public BigDecimal settleBalance(Long userId, String requestId, BigDecimal actualCost) {
        if (actualCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        try {
            String balanceKey = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            String preDeductKey = Constants.REDIS_BILLING_PRE_DEDUCT_PREFIX + requestId;

            List<String> keys = java.util.Arrays.asList(balanceKey, preDeductKey);

            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) stringRedisTemplate.execute(
                settlementScript, keys,
                userId.toString(), actualCost.toPlainString(), requestId
            );

            if (result != null && result.size() >= 2) {
                Object successObj = result.get(0);
                long success = successObj instanceof Number ? ((Number) successObj).longValue() : Long.parseLong(successObj.toString());
                if (success == 1) {
                    BigDecimal refund = new BigDecimal(result.get(2).toString());
                    log.info("结算成功: userId={}, cost={}, refund={}", userId, actualCost, refund);
                    return refund;
                }
            }
            log.warn("结算返回空: userId={}", userId);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("结算失败: userId={}", userId, e);
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getUserBalance(Long userId) {
        try {
            String key = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            String val = stringRedisTemplate.opsForValue().get(key);
            if (val == null || val.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(val);
        } catch (Exception e) {
            log.error("获取余额失败: userId={}", userId, e);
            return BigDecimal.ZERO;
        }
    }

    public void syncUserBalanceToRedis(Long userId, BigDecimal balance) {
        try {
            String key = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            stringRedisTemplate.opsForValue().set(key, balance.toPlainString());
            log.info("同步余额到Redis: userId={}, balance={}", userId, balance);
        } catch (Exception e) {
            log.error("同步余额失败: userId={}", userId, e);
        }
    }

    /**
     * 根据模型配置计算点数消耗
     * @param modelName 模型名称
     * @return 点数消耗（保留三位小数）
     */
    public BigDecimal calculatePointsCost(String modelName) {
        com.ai.gateway.entity.ModelConfig config = cacheService.getModelConfig(modelName);
        
        if (config == null) {
            log.warn("模型配置不存在: modelName={}", modelName);
            return java.math.BigDecimal.ONE; // 默认1点
        }
        
        // 如果配置了点每次消耗，直接使用
        if (config.getPointsPerRequest() != null) {
            return config.getPointsPerRequest();
        }
        
        // 否则根据pointsMode判断
        String pointsMode = config.getPointsMode();
        if (pointsMode == null) {
            pointsMode = "STANDARD"; // 默认中端模型
        }
        
        switch (pointsMode.toUpperCase()) {
            case "FREE":
                return java.math.BigDecimal.ZERO;
            case "STANDARD":
                return java.math.BigDecimal.ONE; // 1点/次
            case "PREMIUM":
                return new java.math.BigDecimal("3.000"); // 3点/次
            default:
                return java.math.BigDecimal.ONE;
        }
    }

    /**
     * 使用点数进行预扣（对话前检查）
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param modelName 模型名称
     * @return 是否成功
     */
    public boolean preDeductPoints(Long userId, String requestId, String modelName) {
        BigDecimal pointsCost = calculatePointsCost(modelName);
        
        // 免费模型不需要扣费
        if (pointsCost.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return true;
        }
        
        // 检查用户点数是否充足
        BigDecimal currentPoints = pointsService.getUserPoints(userId);
        if (currentPoints.compareTo(pointsCost) < 0) {
            log.warn("点数不足: userId={}, current={}, need={}, model={}", 
                    userId, currentPoints, pointsCost, modelName);
            return false;
        }
        
        log.info("点数预扣检查通过: userId={}, cost={}, model={}", userId, pointsCost, modelName);
        return true;
    }

    /**
     * 结算点数消耗（对话后扣除）
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param modelName 模型名称
     * @return 是否成功
     */
    public boolean settlePoints(Long userId, String requestId, String modelName) {
        BigDecimal pointsCost = calculatePointsCost(modelName);
        
        // 免费模型不需要扣费
        if (pointsCost.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return true;
        }
        
        // 扣除点数
        boolean success = pointsService.deductPoints(userId, pointsCost, requestId, modelName);
        
        if (success) {
            // 更新缓存
            BigDecimal newBalance = pointsService.getUserPoints(userId);
            cacheService.updateUserBalanceCache(userId, newBalance);
        }
        
        return success;
    }

    /**
     * 获取用户点数（格式化用于前端展示）
     * @param userId 用户ID
     * @return 点数（保留两位小数）
     */
    public java.math.BigDecimal getUserPoints(Long userId) {
        BigDecimal points = pointsService.getUserPoints(userId);
        return pointsService.formatPointsForFrontend(points);
    }
}
