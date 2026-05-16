package com.ai.gateway.service;

import com.ai.gateway.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class BillingService {

    private final StringRedisTemplate stringRedisTemplate;

    public BillingService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 同步用户余额到 Redis（用于注册/登录/充值后刷新缓存）
     */
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
