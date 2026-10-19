package com.ai.gateway.config;

import com.ai.gateway.entity.AdminOperationLog;
import com.ai.gateway.entity.CallLog;
import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.Conversation;
import com.ai.gateway.entity.DailyStatistics;
import com.ai.gateway.entity.PlatformStatistics;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.mapper.AdminOperationLogMapper;
import com.ai.gateway.mapper.CallLogMapper;
import com.ai.gateway.mapper.ChatMessageMapper;
import com.ai.gateway.mapper.ConversationMapper;
import com.ai.gateway.mapper.DailyStatisticsMapper;
import com.ai.gateway.mapper.PlatformStatisticsMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserSubscriptionMapper;
import com.ai.gateway.service.PointsDistributionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final CallLogMapper callLogMapper;
    private final PlatformStatisticsMapper platformStatisticsMapper;

    /**
     * 每天凌晨0点发放每日点数
     * 免费用户：30限时点数（当日有效）
     * 付费会员：30限时点数 + 对应套餐永久点数（可叠加）
     * cron表达式：每天00:00执行
     */
    @Scheduled(cron = "${app.points.daily-grant-cron:0 0 0 * * ?}")
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
     * 每小时统计一次，更新 Redis 计数缓存供 Dashboard 使用。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyStatistics() {
        log.info("开始执行每小时统计任务...");
        try {
            long apiKeyCount = 0; // API Key 数变化少，不每小时查
            long callLogCount = callLogMapper.selectCount(null);
            long totalUsers = userMapper.selectCount(null);

            log.info("每小时统计完成: totalUsers={}, callLogCount={}", totalUsers, callLogCount);
        } catch (Exception e) {
            log.error("每小时统计任务失败", e);
        }
    }
    
    /**
     * 每天凌晨2点统计前一天完整数据，归档到 daily_statistics 表并更新 platform_statistics。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyStatistics() {
        log.info("开始执行每日统计任务...");
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDateTime yesterdayStart = yesterday.atStartOfDay();
            LocalDateTime yesterdayEnd = LocalDate.now().atStartOfDay();

            // 检查是否已存在昨日统计
            LambdaQueryWrapper<DailyStatistics> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(DailyStatistics::getStatDate, yesterday);
            if (dailyStatsMapper.selectCount(checkWrapper) > 0) {
                log.info("昨日统计数据已存在，跳过生成: date={}", yesterday);
                return;
            }

            // 1) 新增用户
            int newUsers = (int) userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .ge(User::getCreateTime, yesterdayStart)
                    .lt(User::getCreateTime, yesterdayEnd)).longValue();

            // 2) 总用户数（截至昨日）
            int totalUsers = (int) userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .le(User::getCreateTime, yesterdayEnd)).longValue();

            // 3) 活跃用户（昨日有对话的独立用户数）
            LambdaQueryWrapper<Conversation> activeUserWrapper = new LambdaQueryWrapper<Conversation>()
                    .ge(Conversation::getCreateTime, yesterdayStart)
                    .lt(Conversation::getCreateTime, yesterdayEnd)
                    .select(Conversation::getUserId);
            activeUserWrapper.last("GROUP BY user_id");
            int activeUsers = conversationMapper.selectObjs(activeUserWrapper).size();

            // 4) 新增对话数
            int newConversations = (int) conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                    .ge(Conversation::getCreateTime, yesterdayStart)
                    .lt(Conversation::getCreateTime, yesterdayEnd)).longValue();

            // 5) 总对话数（截至昨日）
            int totalConversations = (int) conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                    .le(Conversation::getCreateTime, yesterdayEnd)).longValue();

            // 6) 消息数
            int totalMessages = (int) chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                    .ge(ChatMessage::getCreateTime, yesterdayStart)
                    .lt(ChatMessage::getCreateTime, yesterdayEnd)).longValue();

            // 7) API 调用统计
            int apiCallTotal = (int) callLogMapper.selectCount(new LambdaQueryWrapper<CallLog>()
                    .ge(CallLog::getCreateTime, yesterdayStart)
                    .lt(CallLog::getCreateTime, yesterdayEnd)).longValue();

            // 写入 daily_statistics
            DailyStatistics ds = new DailyStatistics();
            ds.setStatDate(yesterday);
            ds.setNewUsers(Math.max(newUsers, 0));
            ds.setTotalUsers(totalUsers);
            ds.setActiveUsers(activeUsers);
            ds.setNewConversations(newConversations);
            ds.setTotalConversations(totalConversations);
            ds.setTotalMessages(totalMessages);
            ds.setApiCallTotal(apiCallTotal);
            ds.setCreateTime(LocalDateTime.now());
            ds.setUpdateTime(LocalDateTime.now());
            dailyStatsMapper.insert(ds);

            // 更新 platform_statistics 最新快照
            PlatformStatistics ps = new PlatformStatistics();
            ps.setStatDate(yesterday);
            ps.setTotalUsers(totalUsers);
            ps.setNewUsers(Math.max(newUsers, 0));
            ps.setActiveUsers(activeUsers);
            ps.setTotalCalls((long) apiCallTotal);
            // 营收/充值/套餐 依赖订阅和计费表，由 recalculate() 手动触发为准
            ps.setTotalRevenue(BigDecimal.ZERO);
            ps.setRechargeAmount(BigDecimal.ZERO);
            ps.setPackageSales(0);
            ps.setCreateTime(LocalDateTime.now());
            ps.setUpdateTime(LocalDateTime.now());
            platformStatisticsMapper.insert(ps);

            log.info("每日统计归档完成: date={}, newUsers={}, totalUsers={}, activeUsers={}, conversations={}, messages={}, apiCalls={}",
                    yesterday, newUsers, totalUsers, activeUsers, newConversations, totalMessages, apiCallTotal);
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
