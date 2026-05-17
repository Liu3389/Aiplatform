package com.ai.gateway.service;

import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.mapper.PackageTemplateMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserSubscriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 点数发放服务
 * 负责每日定时发放点数和套餐开通赠送点数
 *
 * 发放规则：
 * - 免费用户：30限时点数（当日有效）
 * - 白银会员：30限时点数 + 80永久点数（可叠加）
 * - 黄金会员：30限时点数 + 150永久点数（可叠加）
 * - 钻石会员：30限时点数 + 300永久点数（可叠加）
 */
@Slf4j
@Service
public class PointsDistributionService {

    private static final BigDecimal FREE_TEMPORARY_POINTS = new BigDecimal("30");

    private final UserMapper userMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;
    private final PackageTemplateMapper packageTemplateMapper;
    private final PointsService pointsService;

    public PointsDistributionService(UserMapper userMapper,
                                     UserSubscriptionMapper userSubscriptionMapper,
                                     PackageTemplateMapper packageTemplateMapper,
                                     PointsService pointsService) {
        this.userMapper = userMapper;
        this.userSubscriptionMapper = userSubscriptionMapper;
        this.packageTemplateMapper = packageTemplateMapper;
        this.pointsService = pointsService;
    }

    /**
     * 每日发放点数（由定时任务调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void distributeDailyPoints() {
        log.info("========== 开始执行每日点数发放任务 ==========");

        try {
            List<User> activeUsers = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                    .eq(User::getStatus, 1)
            );

            log.info("查询到活跃用户数: {}", activeUsers.size());

            int successCount = 0;
            int failCount = 0;

            for (User user : activeUsers) {
                try {
                    distributePointsToUser(user);
                    successCount++;
                } catch (Exception e) {
                    log.error("用户点数发放失败: userId={}", user.getId(), e);
                    failCount++;
                }
            }

            log.info("========== 每日点数发放完成: 成功={}, 失败={} ==========", successCount, failCount);

        } catch (Exception e) {
            log.error("每日点数发放任务执行失败", e);
        }
    }

    /**
     * 为单个用户发放点数
     * 规则：所有用户发放30限时点数，付费会员额外发放套餐对应永久点数
     */
    private void distributePointsToUser(User user) {
        Long userId = user.getId();
        LocalDateTime expireTime = LocalDate.now().plusDays(1).atStartOfDay().minusSeconds(1);

        // 1. 所有用户发放30限时点数
        pointsService.addTemporaryPoints(
            userId,
            FREE_TEMPORARY_POINTS,
            "DAILY_GRANT",
            "每日免费限时点数发放（当日有效）",
            expireTime
        );

        // 2. 获取用户当前有效订阅
        UserSubscription subscription = userSubscriptionMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .eq(UserSubscription::getStatus, "ACTIVE")
                .orderByDesc(UserSubscription::getCreateTime)
                .last("LIMIT 1")
        );

        if (subscription == null) {
            log.debug("免费用户点数发放完成: userId={}", userId);
            return;
        }

        // 3. 付费会员额外发放永久点数
        PackageTemplate pkg = packageTemplateMapper.selectById(subscription.getPackageId());
        if (pkg == null || pkg.getDailyPoints() == null) {
            log.debug("付费用户无每日永久点数配置: userId={}, packageId={}", userId, subscription.getPackageId());
            return;
        }

        BigDecimal permanentPoints = pkg.getDailyPoints();
        if (permanentPoints.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        pointsService.addPermanentPoints(
            userId,
            permanentPoints,
            "DAILY_PERMANENT_GRANT",
            "每日" + pkg.getPackageName() + "永久点数发放（可叠加）",
            "daily_permanent_" + LocalDate.now() + "_" + userId
        );

        log.info("付费会员点数发放完成: userId={}, tempPoints={}, permanentPoints={}, package={}",
                userId, FREE_TEMPORARY_POINTS, permanentPoints, pkg.getPackageName());
    }

    /**
     * 套餐开通赠送点数（手动调用）
     * 用户购买套餐后立即执行
     */
    @Transactional(rollbackFor = Exception.class)
    public void grantSubscriptionPoints(Long userId, Long packageId, String orderId) {
        log.info("开始发放套餐赠送点数: userId={}, packageId={}", userId, packageId);

        PackageTemplate pkg = packageTemplateMapper.selectById(packageId);
        if (pkg == null) {
            throw new RuntimeException("套餐不存在: packageId=" + packageId);
        }

        BigDecimal pointsGranted = pkg.getPointsGranted();
        if (pointsGranted == null || pointsGranted.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("套餐无赠送点数: packageId={}", packageId);
            return;
        }

        pointsService.addPermanentPoints(
            userId,
            pointsGranted,
            "SUBSCRIPTION_GRANT",
            "购买" + pkg.getPackageName() + "赠送点数（永久有效）",
            orderId
        );

        log.info("套餐赠送点数发放成功: userId={}, points={}, package={}",
                userId, pointsGranted, pkg.getPackageName());
    }
}
