package com.ai.gateway.service;

import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.PlatformStatistics;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserSubscriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户订阅服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSubscriptionService {

    private final UserSubscriptionMapper userSubscriptionMapper;
    private final PackageTemplateService packageTemplateService;
    private final PointsService pointsService;
    private final CouponService couponService;
    private final UserMapper userMapper;
    private final com.ai.gateway.mapper.PlatformStatisticsMapper platformStatisticsMapper;

    /**
     * 获取用户当前生效的订阅
     */
    public UserSubscription getCurrentSubscription(Long userId) {
        LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSubscription::getUserId, userId)
               .eq(UserSubscription::getStatus, "ACTIVE")
               .orderByDesc(UserSubscription::getStartTime);
        
        Page<UserSubscription> page = new Page<>(1, 1);
        List<UserSubscription> list = userSubscriptionMapper.selectList(page, wrapper);
        UserSubscription subscription = list.isEmpty() ? null : list.get(0);
        
        // 检查是否过期
        if (subscription != null && subscription.getExpireTime() != null 
            && subscription.getExpireTime().isBefore(LocalDateTime.now())) {
            // 标记为已过期
            subscription.setStatus("EXPIRED");
            userSubscriptionMapper.updateById(subscription);
            return null;
        }
        
        return subscription;
    }

    /**
     * 获取用户的订阅历史
     */
    public List<UserSubscription> getSubscriptionHistory(Long userId) {
        LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSubscription::getUserId, userId)
               .orderByDesc(UserSubscription::getStartTime);
        return userSubscriptionMapper.selectList(wrapper);
    }

    /**
     * 购买套餐（支持优惠券）
     * @param userId 用户ID
     * @param packageCode 套餐代码
     * @param cycle 订阅周期：MONTHLY/QUARTERLY/YEARLY
     * @param couponId 优惠券ID（可选）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> purchasePackage(Long userId, String packageCode, String cycle, Long couponId) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 获取套餐信息
        PackageTemplate pkg = packageTemplateService.getByCode(packageCode);
        if (pkg == null || pkg.getStatus() != 1) {
            throw new RuntimeException("套餐不存在或已下架: " + packageCode);
        }

        // 检查当前订阅状态
        UserSubscription currentSubscription = getCurrentSubscription(userId);
        boolean isRenewal = false;
        boolean isUpgrade = false;

        if (currentSubscription != null) {
            // 如果购买的是同一套餐，提示续费
            if (currentSubscription.getPackageCode().equals(packageCode)) {
                isRenewal = true;
                log.info("用户续费同一套餐: userId={}, package={}", userId, packageCode);
            } else {
                // 比较套餐等级，判断是否升级
                int currentLevel = getPackageLevel(currentSubscription.getPackageCode());
                int newLevel = getPackageLevel(packageCode);
                isUpgrade = newLevel > currentLevel;
                log.info("用户{}套餐: userId={}, from={}, to={}",
                        isUpgrade ? "升级" : "降级", userId, currentSubscription.getPackageCode(), packageCode);
            }
        }

        // 默认订阅周期为月度
        if (cycle == null || cycle.isEmpty()) {
            cycle = "MONTHLY";
        }

        // 2. 计算价格和优惠（根据订阅周期选择价格）
        BigDecimal originalPrice;
        switch (cycle) {
            case "QUARTERLY":
                originalPrice = pkg.getPriceQuarterly() != null ? pkg.getPriceQuarterly() : pkg.getPriceMonthly().multiply(new BigDecimal("3")).multiply(new BigDecimal("0.9"));
                break;
            case "YEARLY":
                originalPrice = pkg.getPriceYearly() != null ? pkg.getPriceYearly() : pkg.getPriceMonthly().multiply(new BigDecimal("12")).multiply(new BigDecimal("0.7"));
                break;
            default: // MONTHLY
                originalPrice = pkg.getPriceMonthly();
                break;
        }

        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("套餐价格配置错误");
        }
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal actualPrice = originalPrice;
        Long usedCouponId = null;

        if (couponId != null) {
            try {
                String orderId = "SUB_" + System.currentTimeMillis();
                discount = couponService.useCoupon(userId, couponId, originalPrice, packageCode, "SUBSCRIPTION", orderId);
                actualPrice = originalPrice.subtract(discount);
                usedCouponId = couponId;
                
                log.info("购买套餐使用优惠券: userId={}, couponId={}, discount={}", userId, couponId, discount);
            } catch (Exception e) {
                log.warn("优惠券使用失败，按原价购买: {}", e.getMessage());
            }
        }

        // 修复 P1-1: 使用 SQL 级别的原子更新防止并发超卖
        if (actualPrice.compareTo(BigDecimal.ZERO) > 0) {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            wrapper.eq(User::getId, userId)
                   .ge(User::getBalance, actualPrice)
                   .setSql("balance = balance - {0}", actualPrice.toPlainString());
            
            int rows = userMapper.update(null, wrapper);
            if (rows == 0) {
                throw new RuntimeException("余额不足或扣款失败，请重试");
            }
            log.info("购买套餐原子扣费成功: userId={}, amount={}", userId, actualPrice);
        }
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime expireTime = null;
        // 根据订阅周期计算过期时间
        switch (cycle) {
            case "QUARTERLY":
                expireTime = startTime.plusMonths(3);
                break;
            case "YEARLY":
                expireTime = startTime.plusYears(1);
                break;
            default: // MONTHLY
                expireTime = startTime.plusMonths(1);
                break;
        }

        // 4. 创建订阅记录
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setPackageId(pkg.getId());
        subscription.setPackageCode(pkg.getPackageCode());
        subscription.setPackageName(pkg.getPackageName());
        subscription.setIdentityLabel(pkg.getIdentityLabel());
        subscription.setPointsGranted(pkg.getPointsGranted());
        subscription.setPricePaid(actualPrice);
        subscription.setStartTime(startTime);
        subscription.setExpireTime(expireTime);
        subscription.setStatus("ACTIVE");
        subscription.setAutoRenew(false);
        subscription.setOrderId("SUB_" + System.currentTimeMillis());
        subscription.setCouponId(usedCouponId);
        subscription.setDiscountAmount(discount);
        subscription.setCreateTime(LocalDateTime.now());
        subscription.setUpdateTime(LocalDateTime.now());
        
        userSubscriptionMapper.insert(subscription);

        // 5. 授予开通赠送点数（永久有效）
        if (pkg.getPointsGranted() != null && pkg.getPointsGranted().compareTo(BigDecimal.ZERO) > 0) {
            String businessId = "sub_" + subscription.getId();
            String description = "购买套餐[" + pkg.getPackageName() + "]赠送点数（永久有效）";
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                description += "（使用优惠券优惠" + discount + "元）";
            }
            pointsService.addPermanentPoints(userId, pkg.getPointsGranted(), "SUBSCRIPTION_GRANT", description, businessId);
        }

        // 6. 更新用户会员标识（使用 packageCode 作为标准化标识）
        updateUserMembership(userId, pkg.getPackageCode(), expireTime);

        // 7. 构建返回结果
        result.put("success", true);
        result.put("subscriptionId", subscription.getId());
        result.put("packageCode", pkg.getPackageCode());
        result.put("packageName", pkg.getPackageName());
        result.put("identityLabel", pkg.getIdentityLabel());
        result.put("pointsGranted", pkg.getPointsGranted());
        result.put("dailyPoints", pkg.getDailyPoints());
        result.put("originalPrice", originalPrice);
        result.put("discount", discount);
        result.put("actualPrice", actualPrice);
        result.put("startTime", startTime);
        result.put("expireTime", expireTime);
        result.put("maxInputTokens", pkg.getMaxInputTokens());
        result.put("maxOutputTokens", pkg.getMaxOutputTokens());
        result.put("modelAccessLevel", pkg.getModelAccessLevel());
        result.put("priorityLevel", pkg.getPriorityLevel());
        result.put("isRenewal", isRenewal);
        result.put("isUpgrade", isUpgrade);

        log.info("用户购买套餐成功: userId={}, package={}, price={}, discount={}, isRenewal={}, isUpgrade={}",
                userId, packageCode, actualPrice, discount, isRenewal, isUpgrade);

        recordRevenue(actualPrice);

        return result;
    }

    /**
     * 取消订阅
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelSubscription(Long userId, Long subscriptionId) {
        UserSubscription subscription = userSubscriptionMapper.selectById(subscriptionId);
        
        if (subscription == null || !subscription.getUserId().equals(userId)) {
            throw new RuntimeException("订阅记录不存在");
        }
        
        if (!"ACTIVE".equals(subscription.getStatus())) {
            throw new RuntimeException("订阅状态不是生效中，无法取消");
        }
        
        subscription.setStatus("CANCELLED");
        subscription.setAutoRenew(false);
        userSubscriptionMapper.updateById(subscription);
        
        log.info("用户取消订阅: userId={}, subscriptionId={}", userId, subscriptionId);
    }

    /**
     * 更新用户会员标识
     */
    private void updateUserMembership(Long userId, String identityLabel, LocalDateTime expireTime) {
        if (identityLabel == null || identityLabel.isEmpty()) {
            return;
        }
        
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setMembership(identityLabel);
            user.setMembershipExpireTime(expireTime);
            userMapper.updateById(user);
            
            log.info("更新用户会员标识: userId={}, label={}, expireTime={}", 
                    userId, identityLabel, expireTime);
        }
    }

    /**
     * 检查并更新过期订阅（定时任务调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkAndExpireSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        
        LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSubscription::getStatus, "ACTIVE")
               .isNotNull(UserSubscription::getExpireTime)
               .lt(UserSubscription::getExpireTime, now);
        
        List<UserSubscription> expiredSubscriptions = userSubscriptionMapper.selectList(wrapper);
        
        for (UserSubscription sub : expiredSubscriptions) {
            sub.setStatus("EXPIRED");
            userSubscriptionMapper.updateById(sub);
            
            // 重置用户会员标识
            User user = userMapper.selectById(sub.getUserId());
            if (user != null) {
                user.setMembership(null);
                user.setMembershipExpireTime(null);
                userMapper.updateById(user);
            }
            
            log.info("订阅已过期: subscriptionId={}, userId={}", sub.getId(), sub.getUserId());
        }
        
        if (!expiredSubscriptions.isEmpty()) {
            log.info("检查并更新过期订阅完成: count={}", expiredSubscriptions.size());
        }
    }

    /**
     * 获取套餐等级（用于比较升级/降级）
     */
    private int getPackageLevel(String packageCode) {
        switch (packageCode) {
            case "free":
                return 0;
            case "silver":
                return 1;
            case "gold":
                return 2;
            case "diamond":
                return 3;
            default:
                return 0;
        }
    }

    /**
     * 记录套餐购买营收到 platform_statistics 表
     */
    private void recordRevenue(BigDecimal amount) {
        try {
            LocalDate today = LocalDate.now();

            LambdaQueryWrapper<PlatformStatistics> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PlatformStatistics::getStatDate, today);
            PlatformStatistics todayStats = platformStatisticsMapper.selectOne(wrapper);

            if (todayStats != null) {
                BigDecimal currentRevenue = todayStats.getTotalRevenue() != null ? todayStats.getTotalRevenue() : BigDecimal.ZERO;
                BigDecimal currentRecharge = todayStats.getRechargeAmount() != null ? todayStats.getRechargeAmount() : BigDecimal.ZERO;
                todayStats.setTotalRevenue(currentRevenue.add(amount));
                todayStats.setRechargeAmount(currentRecharge.add(amount));
                platformStatisticsMapper.updateById(todayStats);
            } else {
                PlatformStatistics newStats = new PlatformStatistics();
                newStats.setStatDate(today);
                newStats.setTotalRevenue(amount);
                newStats.setRechargeAmount(amount);
                newStats.setTotalUsers(0);
                newStats.setNewUsers(0);
                newStats.setActiveUsers(0);
                newStats.setRetainedUsers(0);
                newStats.setTotalCalls(0L);
                newStats.setTotalTokens(0L);
                newStats.setTotalPointsConsumed(BigDecimal.ZERO);
                newStats.setPackageSales(1);
                newStats.setAvgOrderValue(amount);
                platformStatisticsMapper.insert(newStats);
            }

            log.info("套餐购买营收记录已更新: date={}, amount={}", today, amount);
        } catch (Exception e) {
            log.error("记录套餐购买营收数据失败: amount={}, error={}", amount, e.getMessage(), e);
        }
    }
}
