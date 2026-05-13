package com.ai.gateway.service;

import com.ai.gateway.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

/**
 * 计费服务 - 基于Redis Lua脚本实现分布式原子性计费
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<List> billingScript;
    private DefaultRedisScript<List> settlementScript;

    /**
     * 初始化Lua脚本
     */
    @PostConstruct
    public void init() {
        // 加载预扣脚本
        billingScript = new DefaultRedisScript<>();
        billingScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/billing.lua")));
        billingScript.setResultType(java.util.List.class);
        
        // 加载结算脚本
        settlementScript = new DefaultRedisScript<>();
        settlementScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/settlement.lua")));
        settlementScript.setResultType(java.util.List.class);
        
        log.info("计费Lua脚本加载成功");
    }

    /**
     * 预扣余额（在请求开始前调用）
     * 
     * @param userId 用户ID
     * @param requestId 请求ID（用于幂等性）
     * @param preDeductAmount 预扣金额
     * @param expireTime 过期时间（秒）
     * @return true-预扣成功，false-余额不足
     */
    public boolean preDeductBalance(Long userId, String requestId, BigDecimal preDeductAmount, int expireTime) {
        try {
            String balanceKey = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            String preDeductKey = Constants.REDIS_BILLING_PRE_DEDUCT_PREFIX + requestId;

            // 使用 StringRedisTemplate 执行 Lua 脚本，确保参数为纯字符串
            List<String> keys = java.util.Arrays.asList(balanceKey, preDeductKey);
            
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) stringRedisTemplate.execute(
                billingScript,
                keys,
                userId.toString(),
                preDeductAmount.toString(),
                requestId,
                String.valueOf(expireTime)
            );

            if (result != null && !result.isEmpty()) {
                // Lua脚本返回的是数字，需要转换
                Object successObj = result.get(0);
                Long success = successObj instanceof Long ? (Long) successObj : Long.parseLong(successObj.toString());
                log.debug("预扣余额结果: userId={}, success={}, result={}", userId, success, result);
                return success == 1;
            }

            log.warn("预扣余额返回空结果: userId={}", userId);
            return false;
        } catch (Exception e) {
            log.error("预扣余额失败: userId={}, requestId={}", userId, requestId, e);
            return false;
        }
    }

    /**
     * 结算实际费用（在请求结束后调用）
     * 
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param actualCost 实际消费金额
     * @return 退还金额（可能为负数表示需要补扣）
     */
    public BigDecimal settleBalance(Long userId, String requestId, BigDecimal actualCost) {
        try {
            String balanceKey = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            String preDeductKey = Constants.REDIS_BILLING_PRE_DEDUCT_PREFIX + requestId;

            // 使用 StringRedisTemplate 执行 Lua 脚本
            List<String> keys = java.util.Arrays.asList(balanceKey, preDeductKey);
            
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) stringRedisTemplate.execute(
                settlementScript,
                keys,
                userId.toString(),
                actualCost.toString(),
                requestId
            );

            if (result != null && result.size() >= 2) {
                Object successObj = result.get(0);
                Long success = successObj instanceof Long ? (Long) successObj : Long.parseLong(successObj.toString());
                
                if (success == 1 && result.size() >= 3) {
                    // 返回退还金额
                    BigDecimal refund = new BigDecimal(result.get(2).toString());
                    log.debug("结算余额成功: userId={}, refund={}", userId, refund);
                    return refund;
                }
            }

            log.warn("结算余额返回异常结果: userId={}, result={}", userId, result);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("结算余额失败: userId={}, requestId={}", userId, requestId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取用户余额
     * 
     * @param userId 用户ID
     * @return 用户余额
     */
    public BigDecimal getUserBalance(Long userId) {
        try {
            String key = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            log.debug("尝试从Redis获取余额: key={}", key);
            
            // 使用 RedisTemplate 读取
            Object balanceObj = redisTemplate.opsForValue().get(key);
            
            log.debug("从Redis读取的余额: key={}, value={}", key, balanceObj);
            
            if (balanceObj == null) {
                log.warn("Redis中余额为null: key={}", key);
                return BigDecimal.ZERO;
            }
            
            // 处理可能的序列化问题：如果是字符串带引号，去除引号
            String balanceStr = balanceObj.toString();
            if (balanceStr.startsWith("\"") && balanceStr.endsWith("\"")) {
                balanceStr = balanceStr.substring(1, balanceStr.length() - 1);
            }
            
            BigDecimal result = new BigDecimal(balanceStr);
            log.debug("解析后的余额: userId={}, balance={}", userId, result);
            return result;
        } catch (Exception e) {
            log.error("获取用户余额失败: userId={}", userId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 同步用户余额到Redis（从数据库加载）
     * 
     * @param userId 用户ID
     * @param balance 余额
     */
    public void syncUserBalanceToRedis(Long userId, BigDecimal balance) {
        try {
            String key = Constants.REDIS_USER_BALANCE_PREFIX + userId;
            // 使用 RedisTemplate 写入，存储为纯字符串
            redisTemplate.opsForValue().set(key, balance.toString());
            log.debug("同步余额到Redis: userId={}, balance={}", userId, balance);
        } catch (Exception e) {
            log.error("同步用户余额到Redis失败: userId={}", userId, e);
        }
    }
}
