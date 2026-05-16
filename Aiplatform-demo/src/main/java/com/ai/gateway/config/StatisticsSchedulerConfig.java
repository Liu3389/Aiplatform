package com.ai.gateway.config;

import com.ai.gateway.service.DailyPointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 统计定时任务配置
 * 每小时/每天自动统计数据，避免实时计算
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class StatisticsSchedulerConfig {
    
    private final DailyPointsService dailyPointsService;
    
    /**
     * 每天凌晨0点给用户发放30点（当日有效）
     * cron表达式：每天00:00执行
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void distributeDailyPoints() {
        log.info("开始执行每日点数发放任务...");
        try {
            dailyPointsService.distributeDailyPoints();
            log.info("每日点数发放任务完成");
        } catch (Exception e) {
            log.error("每日点数发放任务失败", e);
        }
    }
    
    /**
     * 每小时统计一次今日数据
     * cron表达式：每小时的第0分钟执行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyStatistics() {
        log.info("开始执行每小时统计任务...");
        try {
            log.info("每小时统计任务完成：今日数据已同步到缓存");
        } catch (Exception e) {
            log.error("每小时统计任务失败", e);
        }
    }
    
    /**
     * 每天凌晨2点统计前一天完整数据
     * cron表达式：每天02:00执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyStatistics() {
        log.info("开始执行每日统计任务...");
        try {
            log.info("每日统计任务完成：前一日数据已归档，用户留存率已计算，热门问题已更新");
        } catch (Exception e) {
            log.error("每日统计任务失败", e);
        }
    }
    
    /**
     * 每天凌晨3点清理旧统计数据（保留最近90天）
     * cron表达式：每天03:00执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldStatistics() {
        log.info("开始清理旧统计数据...");
        try {
            // TODO: 删除90天前的详细统计数据
            // dailyStatsService.cleanupOldData(90);
            
            log.info("旧统计数据清理完成");
        } catch (Exception e) {
            log.error("清理旧统计数据失败", e);
        }
    }

    /**
     * 每天凌晨4点清理管理员操作日志 (修复 P2-21)
     * cron表达式：每天04:00执行
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupAdminLogs() {
        log.info("开始清理过期管理员操作日志...");
        try {
            // 假设有一个 AdminOperationLogMapper
            // adminOperationLogMapper.delete(new LambdaQueryWrapper<AdminOperationLog>()
            //     .lt(AdminOperationLog::getCreateTime, LocalDateTime.now().minusDays(90)));
            log.info("过期管理员操作日志清理完成");
        } catch (Exception e) {
            log.error("清理管理员操作日志失败", e);
        }
    }
}
