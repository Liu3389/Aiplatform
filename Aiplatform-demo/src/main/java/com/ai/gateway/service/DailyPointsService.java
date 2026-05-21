package com.ai.gateway.service;

import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.mapper.PackageTemplateMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserSubscriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日点数发放服务
 * 已被 PointsDistributionService 替代，保留手动发放功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPointsService {

    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final UserSubscriptionMapper userSubscriptionMapper;
    private final PackageTemplateMapper packageTemplateMapper;

    /**
     * 每日凌晨0点执行，根据用户会员等级发放对应点数
     * 注意：此方法不再由 @Scheduled 自动调用，
     * 定时任务已迁移至 StatisticsSchedulerConfig -> PointsDistributionService
     */
    @Transactional(rollbackFor = Exception.class)
    public void distributeDailyPoints() {
        log.info("开始执行每日点数发放任务...");
        
        // 查询所有状态为启用的用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 1); // 只给启用状态的用户发放
        
        List<User> users = userMapper.selectList(wrapper);
        
        // 修复 P2-7: 采用分批处理，防止万级用户阻塞定时任务
        int batchSize = 500;
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < users.size(); i += batchSize) {
            List<User> batch = users.subList(i, Math.min(i + batchSize, users.size()));
            for (User user : batch) {
                try {
                    LocalDateTime expireTime = LocalDate.now().atTime(23, 59, 59);
                    String businessId = "daily_" + LocalDate.now() + "_" + user.getId();

                    // 检查用户当前生效的订阅，获取对应的每日点数
                    BigDecimal dailyPoints = getDailyPointsForUser(user.getId());
                    
                    pointsService.addPoints(
                            user.getId(),
                            dailyPoints,
                            "GRANT",
                            businessId,
                            "每日免费点数（当日有效）",
                            expireTime,
                            1
                    );
                    
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("用户 {} 每日点数发放失败", user.getUsername(), e);
                }
            }
        }
        
        log.info("每日点数发放任务完成: 成功={}, 失败={}, 总计={}", 
                successCount, failCount, users.size());
    }

    /**
     * 根据用户当前生效的订阅获取每日应发放的点数
     * 有活跃订阅 -> 返回 package_template.daily_points
     * 无活跃订阅 -> 返回 30（免费版）
     */
    private BigDecimal getDailyPointsForUser(Long userId) {
        // 查询用户当前生效的订阅
        LambdaQueryWrapper<UserSubscription> subWrapper = new LambdaQueryWrapper<>();
        subWrapper.eq(UserSubscription::getUserId, userId)
                .eq(UserSubscription::getStatus, "ACTIVE")
                .orderByDesc(UserSubscription::getStartTime)
                .last("LIMIT 1");
        UserSubscription subscription = userSubscriptionMapper.selectOne(subWrapper);

        if (subscription != null && subscription.getExpireTime() != null
                && subscription.getExpireTime().isAfter(LocalDateTime.now())) {
            // 查询套餐模板获取每日点数
            PackageTemplate pkg = packageTemplateMapper.selectById(subscription.getPackageId());
            if (pkg != null && pkg.getDailyPoints() != null && pkg.getDailyPoints().compareTo(BigDecimal.ZERO) > 0) {
                log.debug("用户 {} 当前为付费会员({}), 每日发放 {} 点", userId, pkg.getPackageName(), pkg.getDailyPoints());
                return pkg.getDailyPoints();
            }
        }

        // 免费用户默认30点
        log.debug("用户 {} 为免费用户, 每日发放 30 点", userId);
        return new BigDecimal("30.000");
    }
    
    /**
     * 手动触发每日点数发放（用于测试或补发）
     * @param userId 用户ID，如果为null则给所有用户发放
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualDistributePoints(Long userId) {
        if (userId != null) {
            // 给指定用户发放
            User user = userMapper.selectById(userId);
            if (user == null || user.getStatus() != 1) {
                throw new RuntimeException("用户不存在或已禁用");
            }
            
            LocalDateTime expireTime = LocalDate.now().atTime(23, 59, 59);
            String businessId = "manual_" + LocalDate.now() + "_" + userId;

            // 根据会员等级获取对应的每日点数
            BigDecimal dailyPoints = getDailyPointsForUser(userId);
            
            pointsService.addPoints(
                    userId,
                    dailyPoints,
                    "GRANT",
                    businessId,
                    "手动发放每日点数（当日有效）",
                    expireTime,
                    1
            );

            log.info("手动给用户 {} 发放 {} 点成功", userId, dailyPoints);
        } else {
            // 给所有用户发放
            distributeDailyPoints();
        }
    }
}
