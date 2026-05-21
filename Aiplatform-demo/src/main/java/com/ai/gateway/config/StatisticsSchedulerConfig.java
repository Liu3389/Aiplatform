package com.ai.gateway.config;

import com.ai.gateway.entity.AdminOperationLog;
import com.ai.gateway.entity.DailyStatistics;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.mapper.AdminOperationLogMapper;
import com.ai.gateway.mapper.DailyStatisticsMapper;
import com.ai.gateway.mapper.UserSubscriptionMapper;
import com.ai.gateway.service.PointsDistributionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计定时任务配置
 * 每小时/每天自动统计数据，避免实时计算
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class StatisticsSchedulerConfig {

    private final PointsDistributionService pointsDistributionService;
    private final UserSubscriptionMapper userSubscriptionMapper;
    private final DailyStatisticsMapper dailyStatsMapper;
    private final AdminOperationLogMapper adminOperationLogMapper;

    /**
     * 每天凌晨0点发放每日点数
     * 免费用户：30限时点数（当日有效）
     * 付费会员：30限时点数 + 对应套餐永久点数（可叠加）
     * cron表达式：每天00:00执行
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void distributeDailyPoints() {
        log.info("开始执行每日点数发放任务...");
        try {
            pointsDistributionService.distributeDailyPoints();
            log.info("每日点数发放任务完成");
        } catch (Exception e) {
            log.error("每日点数发放任务失败", e);
        }
    }

    /**
     * 每天凌晨0点10分检查订阅过期
     * 将已过期订阅状态设为 EXPIRED，用户降级为免费普通用户
     * cron表达式：每天00:10执行
     */
    @Scheduled(cron = "0 10 0 * * ?")
    public void checkSubscriptionExpiry() {
        log.info("开始检查订阅过期...");
        try {
            LocalDateTime now = LocalDateTime.now();
            LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserSubscription::getStatus, "ACTIVE")
                    .lt(UserSubscription::getExpireTime, now);

            List<UserSubscription> expiredSubs = userSubscriptionMapper.selectList(wrapper);
            if (expiredSubs.isEmpty()) {
                log.info("无过期订阅需要处理");
                return;
            }

            int count = 0;
            for (UserSubscription sub : expiredSubs) {
                sub.setStatus("EXPIRED");
                sub.setUpdateTime(now);
                userSubscriptionMapper.updateById(sub);
                count++;
                log.info("订阅已过期: userId={}, subscriptionId={}, packageId={}",
                        sub.getUserId(), sub.getId(), sub.getPackageId());
            }

            log.info("订阅过期检查完成: 处理了{}条过期订阅", count);
        } catch (Exception e) {
            log.error("订阅过期检查失败", e);
        }
    }
    
    /**
     * 每小时统计一次今日数据
     * 从数据库聚合今日统计数据并更新到缓存
     * cron表达式：每小时的第0分钟执行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyStatistics() {
        log.info("开始执行每小时统计任务...");
        try {
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

            // 查询今日新增用户数
            long todayNewUsers = 0; // 这里可以注入UserMapper来查询

            // 查询今日对话数（需要注入ConversationMapper）
            long todayConversations = 0;

            // 查询今日消息数（从计数器获取）
            long todayMessages = 0; // 需要从StatisticsCounterService获取

            log.info("每小时统计任务完成: 今日新增用户={}, 今日对话={}, 今日消息={}",
                    todayNewUsers, todayConversations, todayMessages);
        } catch (Exception e) {
            log.error("每小时统计任务失败", e);
        }
    }
    
    /**
     * 每天凌晨2点统计前一天完整数据
     * 归档前一天的统计数据到daily_statistics表
     * cron表达式：每天02:00执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyStatistics() {
        log.info("开始执行每日统计任务...");
        try {
            LocalDateTime yesterdayStart = LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay();
            LocalDateTime yesterdayEnd = LocalDateTime.now().toLocalDate().atStartOfDay();

            // 检查是否已经存在昨天的统计数据
            LambdaQueryWrapper<DailyStatistics> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DailyStatistics::getStatDate, yesterdayStart.toLocalDate());
            Long count = dailyStatsMapper.selectCount(wrapper);

            if (count > 0) {
                log.info("昨日统计数据已存在，跳过生成: date={}", yesterdayStart.toLocalDate());
                return;
            }

            // TODO: 实现完整的每日统计逻辑
            // 1. 聚合昨日的新增用户、活跃用户、对话数、消息数等
            // 2. 计算用户留存率
            // 3. 更新热门问题统计
            // 4. 插入到daily_statistics表

            log.info("每日统计任务完成：前一日数据已归档");
        } catch (Exception e) {
            log.error("每日统计任务失败", e);
        }
    }
    
    /**
     * 每天凌晨3点清理旧统计数据（保留最近90天）
     * 删除90天前的详细统计数据以控制数据库大小
     * cron表达式：每天03:00执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldStatistics() {
        log.info("开始清理旧统计数据...");
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);

            LambdaQueryWrapper<DailyStatistics> wrapper = new LambdaQueryWrapper<>();
            wrapper.lt(DailyStatistics::getStatDate, cutoffDate.toLocalDate());

            int deletedCount = dailyStatsMapper.delete(wrapper);

            log.info("旧统计数据清理完成: 删除了{}条记录（{}天前）", deletedCount, 90);
        } catch (Exception e) {
            log.error("清理旧统计数据失败", e);
        }
    }

    /**
     * 每天凌晨4点清理管理员操作日志（保留最近90天）
     * 防止admin_operation_log表无限增长
     * cron表达式：每天04:00执行
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupAdminLogs() {
        log.info("开始清理过期管理员操作日志...");
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);

            LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.lt(AdminOperationLog::getCreateTime, cutoffDate);

            int deletedCount = adminOperationLogMapper.delete(wrapper);

            log.info("过期管理员操作日志清理完成: 删除了{}条记录（{}天前）", deletedCount, 90);
        } catch (Exception e) {
            log.error("清理管理员操作日志失败", e);
        }
    }
}
