package com.ai.gateway.service;

import com.ai.gateway.entity.MarketplaceProvider;
import com.ai.gateway.entity.MarketplaceUsageLog;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserMarketplaceKey;
import com.ai.gateway.mapper.MarketplaceProviderMapper;
import com.ai.gateway.mapper.MarketplaceUsageLogMapper;
import com.ai.gateway.mapper.UserMarketplaceKeyMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final UserMarketplaceKeyMapper userMarketplaceKeyMapper;
    private final MarketplaceProviderMapper providerMapper;
    private final MarketplaceUsageLogMapper usageLogMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 记录商店调用日志并更新统计（原子性更新）
     * 修复 P2-12: 使用 SQL 级别原子更新防止计数丢失
     * 修复 P2-13: 增加余额检查逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordUsage(Long userKeyId, Long userId, String model, 
                            int inputTokens, int outputTokens, BigDecimal cost, int duration) {
        
        // 1. 修复 P2-13: 检查用户余额是否足够
        User user = userMapper.selectById(userId);
        if (user == null || user.getBalance().compareTo(cost) < 0) {
            throw new RuntimeException("余额不足，无法完成模型商店调用");
        }

        // 2. 扣除余额
        user.setBalance(user.getBalance().subtract(cost));
        userMapper.updateById(user);

        // 3. 写入日志
        MarketplaceUsageLog logEntity = new MarketplaceUsageLog();
        logEntity.setUserKeyId(userKeyId);
        logEntity.setUserId(userId);
        logEntity.setModel(model);
        logEntity.setInputTokens(inputTokens);
        logEntity.setOutputTokens(outputTokens);
        logEntity.setCost(cost);
        logEntity.setDuration(duration);
        usageLogMapper.insert(logEntity);

        // 4. 原子性更新 Key 统计信息（修复 P1-2: 使用占位符防止注入）
        LambdaUpdateWrapper<UserMarketplaceKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserMarketplaceKey::getId, userKeyId)
               .setSql("total_requests = total_requests + 1")
               .setSql("total_input_tokens = total_input_tokens + {0}", inputTokens)
               .setSql("total_output_tokens = total_output_tokens + {0}", outputTokens)
               .setSql("total_cost = total_cost + {0}", cost.toPlainString());
        
        userMarketplaceKeyMapper.update(null, wrapper);
        log.info("模型商店用量已记录: keyId={}, cost={}", userKeyId, cost);
    }
}
