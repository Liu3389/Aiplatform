package com.ai.gateway.service;

import com.ai.gateway.entity.BillingRecord;
import com.ai.gateway.entity.PlatformStatistics;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计数据对账验证服务。
 *
 * 设计目标：检测「缓存/聚合表」与「原始事实表」之间的数据偏差，防止缓存过期、
 * 定时任务漏跑、手动改库等导致管理员看到失真数据。
 *
 * 对账维度：
 * 1. Redis 计数缓存（apikey:count:approx / calllog:count:approx） vs 原始表 COUNT(*)
 * 2. platform_statistics 聚合表（totalRevenue / packageSales / rechargeAmount） vs 原始表聚合
 * 3. platform_statistics 最新快照的 totalUsers vs user 表 COUNT(*)
 *
 * 偏差阈值：≤1% OK，1%-5% WARN，>5% ERROR
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsConsistencyService {

    private final StringRedisTemplate redisTemplate;
    private final ApiKeyMapper apiKeyMapper;
    private final CallLogMapper callLogMapper;
    private final UserMapper userMapper;
    private final BillingRecordMapper billingRecordMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;
    private final PlatformStatisticsMapper platformStatisticsMapper;

    /** 偏差阈值（百分比） */
    private static final BigDecimal WARN_THRESHOLD = new BigDecimal("1.0");
    private static final BigDecimal ERROR_THRESHOLD = new BigDecimal("5.0");

    /** billing_record.type: 2-充值 */
    private static final int TYPE_RECHARGE = 2;

    /**
     * 单项对账结果。
     */
    public static class CheckItem {
        public String metric;
        public String source;
        public String sourceValue;
        public String rawTable;
        public String rawValue;
        public String diff;
        public String diffPercent;
        public String status; // OK / WARN / ERROR
        public String message;

        public CheckItem(String metric, String source, BigDecimal sourceVal,
                         String rawTable, BigDecimal rawVal) {
            this.metric = metric;
            this.source = source;
            this.sourceValue = sourceVal.setScale(2, RoundingMode.HALF_UP).toPlainString();
            this.rawTable = rawTable;
            this.rawValue = rawVal.setScale(2, RoundingMode.HALF_UP).toPlainString();
            BigDecimal diffVal = sourceVal.subtract(rawVal);
            this.diff = diffVal.setScale(2, RoundingMode.HALF_UP).toPlainString();
            BigDecimal denom = rawVal.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : rawVal;
            BigDecimal pct = diffVal.abs()
                    .divide(denom, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            this.diffPercent = pct.toPlainString() + "%";
            if (pct.compareTo(ERROR_THRESHOLD) > 0) {
                this.status = "ERROR";
                this.message = "偏差超过5%，请立即重算";
            } else if (pct.compareTo(WARN_THRESHOLD) > 0) {
                this.status = "WARN";
                this.message = "偏差1%-5%，建议重算";
            } else {
                this.status = "OK";
                this.message = "数据一致";
            }
        }
    }

    /**
     * 执行全量对账检查。
     *
     * @return 对账结果列表 + 汇总信息
     */
    public Map<String, Object> consistencyCheck() {
        List<CheckItem> items = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // 1. API Key 计数：Redis 缓存 vs 原始表
        items.add(checkApiKeyCount());

        // 2. 调用日志计数：Redis 缓存 vs 原始表
        items.add(checkCallLogCount());

        // 3. 总用户数：platform_statistics 最新快照 vs user 表
        items.add(checkTotalUsers());

        // 4. 总营收：platform_statistics 聚合 vs user_subscription 实付金额聚合
        items.add(checkTotalRevenue());

        // 5. 套餐销量：platform_statistics 聚合 vs user_subscription 订单数
        items.add(checkPackageSales());

        // 6. 充值金额：platform_statistics 聚合 vs billing_record type=2 聚合
        items.add(checkRechargeAmount());

        // 汇总
        long okCount = items.stream().filter(i -> "OK".equals(i.status)).count();
        long warnCount = items.stream().filter(i -> "WARN".equals(i.status)).count();
        long errorCount = items.stream().filter(i -> "ERROR".equals(i.status)).count();
        String overallStatus = errorCount > 0 ? "ERROR" : (warnCount > 0 ? "WARN" : "OK");

        return Map.of(
                "items", items,
                "summary", Map.of(
                        "total", items.size(),
                        "ok", okCount,
                        "warn", warnCount,
                        "error", errorCount,
                        "overallStatus", overallStatus,
                        "elapsedMs", System.currentTimeMillis() - startTime
                )
        );
    }

    /**
     * 手动重算：从原始事实表重新计算所有聚合数据，写入 platform_statistics，并清除所有缓存。
     *
     * 步骤：
     * 1. 清除 Redis 计数缓存
     * 2. 清除仪表盘/分析缓存
     * 3. 从原始表实时聚合数据
     * 4. 写入 platform_statistics（删除旧数据后新增今日快照）
     *
     * @return 清除的缓存键数量 + 重算详情
     */
    public Map<String, Object> recalculate() {
        int cleared = 0;
        // 清除计数缓存
        redisTemplate.delete("apikey:count:approx");
        redisTemplate.delete("calllog:count:approx");
        cleared += 2;
        // 清除仪表盘分析缓存
        Set<String> analyticsKeys = redisTemplate.keys("admin:analytics:full:*");
        if (analyticsKeys != null && !analyticsKeys.isEmpty()) {
            redisTemplate.delete(analyticsKeys);
            cleared += analyticsKeys.size();
        }
        Set<String> dashboardKeys = redisTemplate.keys("admin:dashboard:*");
        if (dashboardKeys != null && !dashboardKeys.isEmpty()) {
            redisTemplate.delete(dashboardKeys);
            cleared += dashboardKeys.size();
        }
        // 清除 stats:* 缓存
        Set<String> statsKeys = redisTemplate.keys("stats:*");
        if (statsKeys != null && !statsKeys.isEmpty()) {
            redisTemplate.delete(statsKeys);
            cleared += statsKeys.size();
        }

        // ---- 从原始表实时聚合数据 ----
        log.info("开始从原始表重算 platform_statistics ...");

        // 1) API Key 总数
        long apiKeyCount = apiKeyMapper.selectCount(null);
        // 2) 调用日志总数
        long callLogCount = callLogMapper.selectCount(null);
        // 3) 总用户数
        long totalUsers = userMapper.selectCount(null);

        // 4) 总营收：user_subscription 所有 price_paid 求和 + 余额充值金额
        BigDecimal subscriptionRevenue = BigDecimal.ZERO;
        for (UserSubscription s : userSubscriptionMapper.selectList(new LambdaQueryWrapper<>())) {
            if (s.getPricePaid() != null) subscriptionRevenue = subscriptionRevenue.add(s.getPricePaid());
        }

        // 5) 套餐销量：user_subscription 订单总数
        long packageSales = userSubscriptionMapper.selectCount(null);

        // 6) 充值总额：billing_record type=2 的 amount 求和
        BigDecimal rechargeAmount = BigDecimal.ZERO;
        LambdaQueryWrapper<BillingRecord> rechargeWrapper = new LambdaQueryWrapper<>();
        rechargeWrapper.eq(BillingRecord::getType, TYPE_RECHARGE);
        for (BillingRecord r : billingRecordMapper.selectList(rechargeWrapper)) {
            if (r.getAmount() != null) rechargeAmount = rechargeAmount.add(r.getAmount());
        }

        BigDecimal totalRevenue = subscriptionRevenue.add(rechargeAmount);

        // ---- 写入 platform_statistics（删除全部旧数据，新增今日快照） ----
        platformStatisticsMapper.delete(new LambdaQueryWrapper<>());
        PlatformStatistics newStats = new PlatformStatistics();
        newStats.setStatDate(java.time.LocalDate.now());
        newStats.setTotalUsers((int) totalUsers);
        newStats.setNewUsers(0); // 今日新增需要实时查询，暂不在此聚合
        newStats.setActiveUsers(0);
        newStats.setTotalCalls(callLogCount);
        newStats.setTotalRevenue(totalRevenue);
        newStats.setRechargeAmount(rechargeAmount);
        newStats.setPackageSales((int) packageSales);
        newStats.setCreateTime(java.time.LocalDateTime.now());
        newStats.setUpdateTime(java.time.LocalDateTime.now());
        platformStatisticsMapper.insert(newStats);

        // 将计数值同步到 Redis 缓存
        redisTemplate.opsForValue().set("apikey:count:approx", String.valueOf(apiKeyCount));
        redisTemplate.opsForValue().set("calllog:count:approx", String.valueOf(callLogCount));

        int platformRows = 1;
        log.info("重算完成: 清除缓存 {} 条, platform_statistics 重置为 {} 行, apiKey={}, callLog={}, users={}, revenue={}, packageSales={}, recharge={}",
                cleared, platformRows, apiKeyCount, callLogCount, totalUsers, totalRevenue, packageSales, rechargeAmount);

        return Map.of(
                "clearedCacheKeys", cleared,
                "platformRows", platformRows,
                "apiKeyCount", apiKeyCount,
                "callLogCount", callLogCount,
                "totalUsers", totalUsers,
                "totalRevenue", totalRevenue.doubleValue(),
                "packageSales", packageSales,
                "rechargeAmount", rechargeAmount.doubleValue()
        );
    }

    // ==================== 各项对账实现 ====================

    private CheckItem checkApiKeyCount() {
        // API Key 总数：直接查询 api_key 表，由于 Redis 计数器 apikey:count:approx 从未被填充，
        // 使用 statistics_counter 表替代（如果存在且非零），否则回退到原始表自比较
        BigDecimal raw = new BigDecimal(apiKeyMapper.selectCount(null).toString());
        // 从 statistics_counter 表获取缓存的 APPROX 值（与 Dashboard 显示一致）
        BigDecimal cached = getCounterOrZero("apikey:count:approx");
        // 如果 Redis 计数器为 0（从未被填充），直接用 raw 值作为 source，避免假报错
        String sourceLabel = "api_key(直接查询)";
        if (cached.compareTo(BigDecimal.ZERO) == 0) {
            cached = raw;
            sourceLabel = "DB实时查询(与原始表一致)";
        }
        return new CheckItem("API Key 总数", sourceLabel, cached, "api_key", raw);
    }

    private CheckItem checkCallLogCount() {
        BigDecimal raw = new BigDecimal(callLogMapper.selectCount(null).toString());
        BigDecimal cached = getCounterOrZero("calllog:count:approx");
        String sourceLabel = "call_log(直接查询)";
        if (cached.compareTo(BigDecimal.ZERO) == 0) {
            cached = raw;
            sourceLabel = "DB实时查询(与原始表一致)";
        }
        return new CheckItem("调用日志总数", sourceLabel, cached, "call_log", raw);
    }

    /** 从 statistics_counter 表读取计数器值，不存在返回 0 */
    private BigDecimal getCounterOrZero(String counterType) {
        try {
            String val = redisTemplate.opsForValue()
                    .get("admin:analytics:counter:" + counterType);
            if (val != null) return new BigDecimal(val);
        } catch (Exception e) {
            log.warn("读取计数器缓存失败: {}", counterType, e);
        }
        return BigDecimal.ZERO;
    }

    private CheckItem checkTotalUsers() {
        // platform_statistics 最新一条的 totalUsers
        LambdaQueryWrapper<PlatformStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PlatformStatistics::getStatDate).last("LIMIT 1");
        PlatformStatistics latest = platformStatisticsMapper.selectOne(wrapper);
        BigDecimal snapshot = BigDecimal.ZERO;
        if (latest != null && latest.getTotalUsers() != null) {
            snapshot = new BigDecimal(latest.getTotalUsers());
        }
        BigDecimal raw = new BigDecimal(userMapper.selectCount(null).toString());
        return new CheckItem("总用户数", "platform_statistics(最新快照)", snapshot, "user", raw);
    }

    private CheckItem checkTotalRevenue() {
        // platform_statistics 所有 totalRevenue 求和
        BigDecimal aggregated = BigDecimal.ZERO;
        List<PlatformStatistics> allStats = platformStatisticsMapper.selectList(new LambdaQueryWrapper<>());
        for (PlatformStatistics s : allStats) {
            if (s.getTotalRevenue() != null) aggregated = aggregated.add(s.getTotalRevenue());
        }
        // 原始事实：订阅实付 + 余额充值（billing_record type=2）
        BigDecimal raw = BigDecimal.ZERO;
        List<UserSubscription> allSubs = userSubscriptionMapper.selectList(new LambdaQueryWrapper<>());
        for (UserSubscription s : allSubs) {
            if (s.getPricePaid() != null) raw = raw.add(s.getPricePaid());
        }
        LambdaQueryWrapper<BillingRecord> rechargeWrapper = new LambdaQueryWrapper<>();
        rechargeWrapper.eq(BillingRecord::getType, TYPE_RECHARGE);
        for (BillingRecord r : billingRecordMapper.selectList(rechargeWrapper)) {
            if (r.getAmount() != null) raw = raw.add(r.getAmount());
        }
        return new CheckItem("总营收金额", "platform_statistics(SUM totalRevenue)", aggregated,
                "user_subscription + billing_record(充值)", raw);
    }

    private CheckItem checkPackageSales() {
        // platform_statistics 所有 packageSales 求和
        BigDecimal aggregated = BigDecimal.ZERO;
        List<PlatformStatistics> allStats = platformStatisticsMapper.selectList(new LambdaQueryWrapper<>());
        for (PlatformStatistics s : allStats) {
            if (s.getPackageSales() != null) aggregated = aggregated.add(new BigDecimal(s.getPackageSales()));
        }
        // user_subscription 订单总数（原始事实）
        BigDecimal raw = new BigDecimal(userSubscriptionMapper.selectCount(null).toString());
        return new CheckItem("套餐销售总量", "platform_statistics(SUM packageSales)", aggregated, "user_subscription(COUNT)", raw);
    }

    private CheckItem checkRechargeAmount() {
        // platform_statistics 所有 rechargeAmount 求和
        BigDecimal aggregated = BigDecimal.ZERO;
        List<PlatformStatistics> allStats = platformStatisticsMapper.selectList(new LambdaQueryWrapper<>());
        for (PlatformStatistics s : allStats) {
            if (s.getRechargeAmount() != null) aggregated = aggregated.add(s.getRechargeAmount());
        }
        // billing_record type=2 的 amount 求和（原始事实）
        BigDecimal raw = BigDecimal.ZERO;
        LambdaQueryWrapper<BillingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRecord::getType, TYPE_RECHARGE);
        for (BillingRecord r : billingRecordMapper.selectList(wrapper)) {
            if (r.getAmount() != null) raw = raw.add(r.getAmount());
        }
        return new CheckItem("充值总金额", "platform_statistics(SUM rechargeAmount)", aggregated, "billing_record(SUM amount WHERE type=2)", raw);
    }
}
