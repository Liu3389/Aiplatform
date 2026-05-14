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

    public BillingService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
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
}
