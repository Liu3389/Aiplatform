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
    private final PlatformModelService platformModelService;

    public BillingService(StringRedisTemplate stringRedisTemplate, 
                         PointsService pointsService,
                         PlatformModelService platformModelService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.pointsService = pointsService;
        this.platformModelService = platformModelService;
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
        // 修复：全平台统一使用点数结算，不再扣除余额（元）
        log.warn("系统已禁用余额扣费逻辑，请使用点数模式: userId={}", userId);
        return false;
    }

    public BigDecimal settleBalance(Long userId, String requestId, BigDecimal actualCost) {
        // 修复：全平台统一使用点数结算，不再扣除余额（元）
        log.warn("系统已禁用余额结算逻辑，请使用点数模式: userId={}", userId);
        return BigDecimal.ZERO;
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
        // 修复 P2-11: 使用 PlatformModelService 替代旧的 ModelConfig
        com.ai.gateway.entity.PlatformModelConfig config = platformModelService.getByDisplayName(modelName);
        
        if (config == null) {
            log.warn("模型配置不存在: modelName={}", modelName);
            return java.math.BigDecimal.ONE; // 默认1点
        }
        
        return platformModelService.getModelPointsCost(config);
    }

    /**
     * 使用点数进行预扣（对话前检查）
     * @deprecated 请直接使用 PointsService.preDeductPoints
     */
    @Deprecated
    public boolean preDeductPoints(Long userId, String requestId, String modelName) {
        return pointsService.preDeductPoints(userId, requestId, modelName);
    }

    /**
     * 结算点数消耗（对话后扣除）- 已废弃，请使用 PointsService.settlePoints
     * @deprecated 使用 PointsService 进行原子性点数操作
     */
    @Deprecated
    public boolean settlePoints(Long userId, String requestId, String modelName) {
        BigDecimal pointsCost = calculatePointsCost(modelName);
        if (pointsCost.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return true;
        }
        return pointsService.deductPoints(userId, pointsCost, requestId, modelName);
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
