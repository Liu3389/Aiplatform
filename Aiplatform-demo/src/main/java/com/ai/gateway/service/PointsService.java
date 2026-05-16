package com.ai.gateway.service;

import com.ai.gateway.entity.PointsBill;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.PointsBillMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 点数管理服务 - 统一使用点数作为AI对话消耗单位
 */
@Slf4j
@Service
public class PointsService {

    private final PointsBillMapper pointsBillMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final PlatformModelService platformModelService;

    public PointsService(PointsBillMapper pointsBillMapper,
                         StringRedisTemplate redisTemplate,
                         UserMapper userMapper,
                         PlatformModelService platformModelService) {
        this.pointsBillMapper = pointsBillMapper;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.platformModelService = platformModelService;
    }
    
    private static final String USER_POINTS_PREFIX = "points:user:";
    private static final String POINTS_PRECISION_KEY = "points:precision";
    private static final String POINTS_PRE_DEDUCT_PREFIX = "points:pre_deduct:";

    private DefaultRedisScript<List> pointsBillingScript;
    private DefaultRedisScript<List> settlementScript;

    @PostConstruct
    public void init() {
        pointsBillingScript = new DefaultRedisScript<>();
        pointsBillingScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/points_billing.lua")));
        pointsBillingScript.setResultType((Class<List>) (Class<?>) List.class);

        settlementScript = new DefaultRedisScript<>();
        settlementScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/settlement.lua")));
        settlementScript.setResultType((Class<List>) (Class<?>) List.class);

        log.info("点数计费Lua脚本加载成功");
        log.info("点数结算Lua脚本加载成功");
    }

    /**
     * 获取用户当前点数（从Redis缓存，带防击穿机制）
     * @param userId 用户ID
     * @return 点数，保留两位小数
     */
    public BigDecimal getUserPoints(Long userId) {
        String key = USER_POINTS_PREFIX + userId;
        String pointsStr = redisTemplate.opsForValue().get(key);
        
        if (pointsStr != null) {
            try {
                return new BigDecimal(pointsStr).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                log.error("解析点数失败: userId={}, pointsStr={}", userId, pointsStr);
            }
        }
        
        // 缓存未命中，使用分布式锁防止击穿
        String lockKey = "lock:points:" + userId;
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, java.util.concurrent.TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(isLocked)) {
            try {
                // 双重检查
                pointsStr = redisTemplate.opsForValue().get(key);
                if (pointsStr != null) {
                    return new BigDecimal(pointsStr).setScale(2, RoundingMode.HALF_UP);
                }
                return syncUserPointsFromDB(userId);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // 等待一小段时间后重试获取缓存
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            pointsStr = redisTemplate.opsForValue().get(key);
            if (pointsStr != null) {
                return new BigDecimal(pointsStr).setScale(2, RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO; // 极端情况下返回0，避免阻塞
        }
    }

    /**
     * 预扣点数（对话前检查 + 原子性预扣）
     * 使用 Lua 脚本保证原子性：检查余额 -> 预扣 -> 记录预扣标记
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param modelName 模型名称
     * @return 是否成功
     */
    public boolean preDeductPoints(Long userId, String requestId, String modelName) {
        BigDecimal pointsCost = calculatePointsCost(modelName);
        if (pointsCost.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return doPreDeduct(userId, requestId, pointsCost);
    }

    /**
     * 执行预扣（Lua 原子操作）
     */
    private boolean doPreDeduct(Long userId, String requestId, BigDecimal points) {
        BigDecimal deductPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + requestId;

        try {
            List<Object> result = redisTemplate.execute(
                pointsBillingScript,
                List.of(pointsKey, preDeductKey),
                userId.toString(), deductPoints.toPlainString(), requestId, "120"
            );

            if (result != null && !result.isEmpty()) {
                long success = ((Number) result.get(0)).longValue();
                if (success == 1) {
                    String currentPointsStr = result.get(1).toString();
                    BigDecimal balanceAfter = new BigDecimal(currentPointsStr);
                    log.info("点数预扣成功(Lua): userId={}, deduct={}, requestId={}, balance={}",
                            userId, deductPoints, requestId, balanceAfter);
                    return true;
                } else {
                    log.warn("点数预扣失败(余额不足): userId={}, need={}, requestId={}",
                            userId, deductPoints, requestId);
                    return false;
                }
            }
            log.warn("点数预扣返回空: userId={}, requestId={}", userId, requestId);
            return false;
        } catch (Exception e) {
            log.error("点数预扣异常: userId={}, requestId={}", userId, requestId, e);
            return false;
        }
    }

    /**
     * 结算点数（对话后根据实际消耗结算）
     * 如果预扣金额 > 实际消耗，退还差额
     * 如果预扣金额 < 实际消耗，补扣差额
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param actualPoints 实际消耗点数
     * @param modelName 模型名称
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean settlePoints(Long userId, String requestId, BigDecimal actualPoints, String modelName) {
        BigDecimal actual = actualPoints.setScale(3, RoundingMode.HALF_DOWN);
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + requestId;

        try {
            String preDeductStr = redisTemplate.opsForValue().get(preDeductKey);
            if (preDeductStr == null) {
                log.warn("结算时预扣记录不存在: userId={}, requestId={}", userId, requestId);
                return false;
            }
            BigDecimal preDeductAmount = new BigDecimal(preDeductStr);

            List<Object> result = redisTemplate.execute(
                settlementScript,
                List.of(pointsKey, preDeductKey),
                actual.toPlainString()
            );

            if (result == null || result.isEmpty()) {
                log.error("结算Lua脚本返回空: userId={}, requestId={}", userId, requestId);
                return false;
            }

            long success = ((Number) result.get(0)).longValue();
            if (success != 1) {
                log.warn("结算失败: userId={}, requestId={}, reason={}", userId, requestId, result.get(1));
                return false;
            }

            String balanceAfterStr = result.get(1).toString();
            BigDecimal balanceAfter = new BigDecimal(balanceAfterStr).setScale(3, RoundingMode.HALF_DOWN);
            BigDecimal difference = preDeductAmount.subtract(actual);

            recordBill(userId, "SETTLE", actual.negate(), balanceAfter.subtract(difference), balanceAfter,
                       requestId, "对话结算: " + modelName + " (预扣:" + preDeductAmount + ", 实际:" + actual + ")",
                       modelName, null);

            updateUserPointsInDB(userId, balanceAfter);

            log.info("点数结算成功(Lua): userId={}, preDeduct={}, actual={}, difference={}, balance={}",
                    userId, preDeductAmount, actual, difference, balanceAfter);
            return true;
        } catch (Exception e) {
            log.error("点数结算异常: userId={}, requestId={}", userId, requestId, e);
            return false;
        }
    }

    /**
     * 回滚预扣（对话失败时调用 - 修复 P1-3: 使用 Lua 脚本保证原子性）
     */
    public void rollbackPreDeduct(Long userId, String requestId) {
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + requestId;
        
        try {
            // 简单的原子回滚：如果预扣 Key 存在，则将其值加回余额并删除
            List<Object> result = redisTemplate.execute(
                pointsBillingScript,
                List.of(pointsKey, preDeductKey),
                userId.toString(), "0", requestId, "0" // 传入 0 表示回滚逻辑
            );
            log.info("点数预扣回滚成功: userId={}, requestId={}", userId, requestId);
        } catch (Exception e) {
            log.error("点数预扣回滚异常: userId={}, requestId={}", userId, requestId, e);
        }
    }

    /**
     * 计算模型点数消耗
     */
    private BigDecimal calculatePointsCost(String modelName) {
        PlatformModelConfig config = getModelConfigByName(modelName);
        if (config == null) {
            return BigDecimal.ONE;
        }
        if (config.getFixedPoints() != null) {
            return config.getFixedPoints();
        }
        return BigDecimal.ONE;
    }

    private PlatformModelConfig getModelConfigByName(String modelName) {
        try {
            return platformModelService.getByDisplayName(modelName);
        } catch (Exception e) {
            log.warn("获取模型配置失败: modelName={}", modelName, e);
            return null;
        }
    }

    /**
     * 扣除点数（对话消耗 - 原子性操作）
     * @deprecated 使用 preDeductPoints + settlePoints 替代
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean deductPoints(Long userId, BigDecimal points, String businessId, String modelName) {
        // 保留三位小数精度进行计算
        BigDecimal deductPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        
        String pointsKey = USER_POINTS_PREFIX + userId;
        String preDeductKey = POINTS_PRE_DEDUCT_PREFIX + businessId;

        try {
            List<Object> result = redisTemplate.execute(
                pointsBillingScript,
                List.of(pointsKey, preDeductKey),
                userId.toString(), deductPoints.toPlainString(), businessId, "60"
            );

            if (result != null && !result.isEmpty()) {
                long success = ((Number) result.get(0)).longValue();
                if (success == 1) {
                    // 记录账单
                    String currentPointsStr = result.get(1).toString();
                    BigDecimal balanceAfter = new BigDecimal(currentPointsStr);
                    BigDecimal balanceBefore = balanceAfter.add(deductPoints);
                    recordBill(userId, "DEDUCT", deductPoints.negate(), balanceBefore, balanceAfter, 
                               businessId, "对话消耗: " + modelName, modelName, null);
                    
                    // 同步数据库
                    updateUserPointsInDB(userId, balanceAfter);
                    
                    log.info("点数扣除成功(Lua): userId={}, deduct={}, balance={}", userId, deductPoints, balanceAfter);
                    return true;
                }
            }
            log.warn("点数扣除失败(Lua): userId={}, deduct={}", userId, deductPoints);
            return false;
        } catch (Exception e) {
            log.error("点数扣除异常: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 增加点数（充值或发放）
     * @param userId 用户ID
     * @param points 增加点数（保留三位小数精度）
     * @param changeType 变动类型：RECHARGE-充值, GRANT-发放
     * @param businessId 业务ID
     * @param description 描述
     * @param expireTime 有效期（可选）
     * @return 新余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal addPoints(Long userId, BigDecimal points, String changeType, 
                                String businessId, String description, LocalDateTime expireTime) {
        BigDecimal addPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        
        String key = USER_POINTS_PREFIX + userId;
        
        BigDecimal currentPoints = syncUserPointsFromDB(userId);
        
        recordBill(userId, changeType, addPoints, currentPoints, currentPoints.add(addPoints), 
                   businessId, description, null, expireTime);
        
        String currentStr = redisTemplate.opsForValue().get(key);
        BigDecimal current = currentStr != null ? new BigDecimal(currentStr) : BigDecimal.ZERO;
        BigDecimal newBalance = current.add(addPoints).setScale(3, RoundingMode.HALF_DOWN);
        redisTemplate.opsForValue().set(key, newBalance.toPlainString());
        
        updateUserPointsInDB(userId, newBalance);
        
        log.info("点数增加成功: userId={}, add={}, type={}, balance={}", 
                userId, addPoints, changeType, newBalance);
        
        return newBalance;
    }

    /**
     * 同步用户点数从数据库到Redis
     * @param userId 用户ID
     * @return 点数
     */
    private BigDecimal syncUserPointsFromDB(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("用户不存在: userId={}", userId);
            return BigDecimal.ZERO;
        }
        
        BigDecimal points = user.getPoints() != null ? user.getPoints() : BigDecimal.ZERO;
        String key = USER_POINTS_PREFIX + userId;
        redisTemplate.opsForValue().set(key, points.toString());
        
        log.info("同步用户点数到Redis: userId={}, points={}", userId, points);
        return points;
    }

    /**
     * 更新用户点数到数据库
     * @param userId 用户ID
     * @param newPoints 新点数
     */
    private void updateUserPointsInDB(Long userId, BigDecimal newPoints) {
        User user = new User();
        user.setId(userId);
        user.setPoints(newPoints);
        userMapper.updateById(user);
        
        log.debug("更新用户点数到数据库: userId={}, points={}", userId, newPoints);
    }

    /**
     * 记录点数账单
     * @param userId 用户ID
     * @param changeType 变动类型
     * @param pointsChange 变动点数
     * @param balanceBefore 变动前余额
     * @param balanceAfter 变动后余额
     * @param businessId 业务ID
     * @param description 描述
     * @param modelName 模型名称
     * @param expireTime 有效期
     */
    private void recordBill(Long userId, String changeType, BigDecimal pointsChange,
                           BigDecimal balanceBefore, BigDecimal balanceAfter,
                           String businessId, String description, String modelName,
                           LocalDateTime expireTime) {
        PointsBill bill = new PointsBill();
        bill.setUserId(userId);
        bill.setChangeType(changeType);
        bill.setPointsChange(pointsChange);
        bill.setBalanceBefore(balanceBefore);
        bill.setBalanceAfter(balanceAfter);
        bill.setBusinessId(businessId);
        bill.setDescription(description);
        bill.setModelName(modelName);
        bill.setExpireTime(expireTime);
        bill.setCreateTime(LocalDateTime.now());
        
        pointsBillMapper.insert(bill);
        
        log.debug("点数账单记录: userId={}, type={}, change={}, balance={}->{}", 
                 userId, changeType, pointsChange, balanceBefore, balanceAfter);
    }

    /**
     * 获取用户点数账单列表
     * @param userId 用户ID
     * @param page 页码
     * @param pageSize 每页大小
     * @return 账单列表
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsBill> getPointsBillList(
            Long userId, int page, int pageSize, String changeType) {
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsBill> pageParam = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        
        LambdaQueryWrapper<PointsBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsBill::getUserId, userId);
        
        // 如果指定了变动类型，进行筛选
        if (changeType != null && !changeType.isEmpty()) {
            wrapper.eq(PointsBill::getChangeType, changeType);
        }
        
        wrapper.orderByDesc(PointsBill::getCreateTime);
        
        return pointsBillMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 每日免费点数发放
     * @param userId 用户ID
     * @param dailyFreePoints 每日免费点数
     * @return 发放后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal grantDailyFreePoints(Long userId, BigDecimal dailyFreePoints) {
        String grantKey = "daily_grant:" + userId + ":" + LocalDateTime.now().toLocalDate();
        
        // 检查今天是否已经发放
        Boolean exists = redisTemplate.hasKey(grantKey);
        if (Boolean.TRUE.equals(exists)) {
            log.info("今日已发放免费点数: userId={}", userId);
            return getUserPoints(userId);
        }
        
        // 发放免费点数
        String description = "每日免费点数发放";
        LocalDateTime expireTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        
        BigDecimal newBalance = addPoints(userId, dailyFreePoints, "GRANT", 
                                          "daily_" + LocalDateTime.now().toLocalDate(),
                                          description, expireTime);
        
        // 标记今日已发放（24小时过期）
        redisTemplate.opsForValue().set(grantKey, "1", 25, java.util.concurrent.TimeUnit.HOURS);
        
        log.info("每日免费点数发放成功: userId={}, points={}, expireTime={}", 
                userId, dailyFreePoints, expireTime);
        
        return newBalance;
    }

    /**
     * 清理过期点数
     * @param userId 用户ID
     * @return 清理的点数
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal cleanExpiredPoints(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        
        // 查询已过期的GRANT记录
        LambdaQueryWrapper<PointsBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsBill::getUserId, userId)
               .eq(PointsBill::getChangeType, "GRANT")
               .isNotNull(PointsBill::getExpireTime)
               .lt(PointsBill::getExpireTime, now)
               .gt(PointsBill::getPointsChange, 0); // 只处理正数（增加的点数）
        
        List<PointsBill> expiredBills = pointsBillMapper.selectList(wrapper);
        
        if (expiredBills.isEmpty()) {
            log.debug("无过期点数需要清理: userId={}", userId);
            return BigDecimal.ZERO;
        }
        
        // 计算需要扣除的总点数
        BigDecimal totalExpired = expiredBills.stream()
                .map(PointsBill::getPointsChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 扣除过期点数
        if (totalExpired.compareTo(BigDecimal.ZERO) > 0) {
            deductPoints(userId, totalExpired, "expire_clean_" + now, "点数过期清理");
            
            // 标记这些账单为已处理
            for (PointsBill bill : expiredBills) {
                bill.setDescription(bill.getDescription() + " [已过期清理]");
                pointsBillMapper.updateById(bill);
            }
        }
        
        log.info("清理过期点数完成: userId={}, expired={}", userId, totalExpired);
        return totalExpired;
    }

    /**
     * 格式化点数用于前端展示（保留两位小数）
     * @param points 点数（内部三位小数）
     * @return 格式化后的点数
     */
    public BigDecimal formatPointsForFrontend(BigDecimal points) {
        if (points == null) {
            return BigDecimal.ZERO;
        }
        return points.setScale(2, RoundingMode.HALF_UP);
    }
}
