package com.ai.gateway.service;

import com.ai.gateway.entity.PointsBill;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.PointsBillMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 点数管理服务 - 统一使用点数作为AI对话消耗单位
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {

    private final PointsBillMapper pointsBillMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    
    private static final String USER_POINTS_PREFIX = "points:user:";
    private static final String POINTS_PRECISION_KEY = "points:precision";

    /**
     * 获取用户当前点数（从Redis缓存）
     * @param userId 用户ID
     * @return 点数，保留两位小数
     */
    public BigDecimal getUserPoints(Long userId) {
        String key = USER_POINTS_PREFIX + userId;
        String pointsStr = redisTemplate.opsForValue().get(key);
        
        if (pointsStr == null) {
            // 缓存未命中，从数据库查询并同步到Redis
            return syncUserPointsFromDB(userId);
        }
        
        try {
            return new BigDecimal(pointsStr).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.error("解析点数失败: userId={}, pointsStr={}", userId, pointsStr);
            return syncUserPointsFromDB(userId);
        }
    }

    /**
     * 扣除点数（对话消耗）
     * @param userId 用户ID
     * @param points 扣减点数（保留三位小数精度）
     * @param businessId 业务ID（如requestId）
     * @param modelName 模型名称
     * @return 是否成功
     */
    @Transactional
    public boolean deductPoints(Long userId, BigDecimal points, String businessId, String modelName) {
        // 保留三位小数精度进行计算
        BigDecimal deductPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        
        String key = USER_POINTS_PREFIX + userId;
        String currentPointsStr = redisTemplate.opsForValue().get(key);
        
        if (currentPointsStr == null) {
            log.error("用户点数缓存不存在: userId={}", userId);
            return false;
        }
        
        BigDecimal currentPoints = new BigDecimal(currentPointsStr);
        
        // 检查余额是否充足
        if (currentPoints.compareTo(deductPoints) < 0) {
            log.warn("点数不足: userId={}, current={}, need={}", userId, currentPoints, deductPoints);
            return false;
        }
        
        // 计算新余额（保留三位小数）
        BigDecimal newBalance = currentPoints.subtract(deductPoints).setScale(3, RoundingMode.HALF_DOWN);
        
        // 更新Redis
        redisTemplate.opsForValue().set(key, newBalance.toString());
        
        // 记录账单（返回给前端时保留两位小数）
        recordBill(userId, "DEDUCT", deductPoints.negate(), currentPoints, newBalance, 
                   businessId, "对话消耗: " + modelName, modelName, null);
        
        log.info("点数扣除成功: userId={}, deduct={}, balance={}", userId, deductPoints, newBalance);
        return true;
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
    @Transactional
    public BigDecimal addPoints(Long userId, BigDecimal points, String changeType, 
                                String businessId, String description, LocalDateTime expireTime) {
        // 保留三位小数精度
        BigDecimal addPoints = points.setScale(3, RoundingMode.HALF_DOWN);
        
        String key = USER_POINTS_PREFIX + userId;
        String currentPointsStr = redisTemplate.opsForValue().get(key);
        
        BigDecimal currentPoints;
        if (currentPointsStr == null) {
            currentPoints = syncUserPointsFromDB(userId);
        } else {
            currentPoints = new BigDecimal(currentPointsStr);
        }
        
        // 计算新余额（保留三位小数）
        BigDecimal newBalance = currentPoints.add(addPoints).setScale(3, RoundingMode.HALF_DOWN);
        
        // 更新Redis
        redisTemplate.opsForValue().set(key, newBalance.toString());
        
        // 记录账单
        recordBill(userId, changeType, addPoints, currentPoints, newBalance, 
                   businessId, description, null, expireTime);
        
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
    @Transactional
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
    @Transactional
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
