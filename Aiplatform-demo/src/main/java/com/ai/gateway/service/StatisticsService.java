package com.ai.gateway.service;

import com.ai.gateway.entity.CallLog;
import com.ai.gateway.entity.PlatformStatistics;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.CallLogMapper;
import com.ai.gateway.mapper.PlatformStatisticsMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 平台统计服务
 * 负责定时统计、数据查询、报表导出等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final PlatformStatisticsMapper statisticsMapper;
    private final UserMapper userMapper;
    private final CallLogMapper callLogMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String STATS_CACHE_PREFIX = "stats:";
    private static final long CACHE_TTL_HOURS = 24;

    /**
     * 定时统计任务 - 每天凌晨2点执行
     * 统计前一天的平台数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generateDailyStatistics() {
        log.info("开始执行每日统计任务...");
        
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            
            // 检查是否已经统计过
            LambdaQueryWrapper<PlatformStatistics> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(PlatformStatistics::getStatDate, yesterday);
            Long count = statisticsMapper.selectCount(checkWrapper);
            
            if (count > 0) {
                log.info("日期 {} 的统计数据已存在，跳过", yesterday);
                return;
            }
            
            // 执行统计
            PlatformStatistics stats = collectStatistics(yesterday);
            
            // 保存到数据库
            statisticsMapper.insert(stats);
            
            // 缓存到Redis（7天）
            cacheStatistics(yesterday, stats);
            
            // 检查异常数据并告警
            checkAndAlert(yesterday, stats);
            
            log.info("每日统计任务完成: date={}, totalUsers={}, totalCalls={}", 
                    yesterday, stats.getTotalUsers(), stats.getTotalCalls());
                    
        } catch (Exception e) {
            log.error("每日统计任务执行失败", e);
        }
    }

    /**
     * 收集统计数据
     */
    private PlatformStatistics collectStatistics(LocalDate date) {
        PlatformStatistics stats = new PlatformStatistics();
        stats.setStatDate(date);
        
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        
        // 1. 用户数据统计
        stats.setTotalUsers(countTotalUsers());
        stats.setNewUsers(countNewUsers(startOfDay, endOfDay));
        stats.setActiveUsers(countActiveUsers(startOfDay, endOfDay));
        stats.setRetainedUsers(countRetainedUsers(date));
        
        // 2. 业务数据统计
        stats.setTotalCalls(countTotalCalls(startOfDay, endOfDay));
        stats.setTotalTokens(countTotalTokens(startOfDay, endOfDay));
        stats.setTotalPointsConsumed(sumPointsConsumed(startOfDay, endOfDay));
        
        // 3. 营收数据统计
        stats.setRechargeAmount(sumRechargeAmount(startOfDay, endOfDay));
        stats.setPackageSales(countPackageSales(startOfDay, endOfDay));
        stats.setAvgOrderValue(calculateAvgOrderValue(stats.getRechargeAmount(), stats.getPackageSales()));
        
        // 4. 模型使用统计（JSON格式）
        stats.setModelUsage(getModelUsageJson(startOfDay, endOfDay));
        
        // 5. 小时流量分布（JSON格式）
        stats.setHourlyTraffic(getHourlyTrafficJson(startOfDay, endOfDay));
        
        stats.setCreateTime(LocalDateTime.now());
        stats.setUpdateTime(LocalDateTime.now());
        
        return stats;
    }

    /**
     * 统计总用户数
     */
    private Integer countTotalUsers() {
        return Math.toIntExact(userMapper.selectCount(null));
    }

    /**
     * 统计新增用户数
     */
    private Integer countNewUsers(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, startTime)
               .lt(User::getCreateTime, endTime);
        return Math.toIntExact(userMapper.selectCount(wrapper));
    }

    /**
     * 统计活跃用户数（有调用记录的用户）
     */
    private Integer countActiveUsers(LocalDateTime startTime, LocalDateTime endTime) {
        // TODO: 从call_log表统计有调用记录的不同用户数
        // 这里暂时返回0，实际需要SQL查询
        return 0;
    }

    /**
     * 统计留存用户数（昨天活跃且今天也活跃的用户）
     */
    private Integer countRetainedUsers(LocalDate date) {
        // TODO: 实现留存用户计算逻辑
        return 0;
    }

    /**
     * 统计总对话次数
     */
    private Long countTotalCalls(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<CallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(CallLog::getCreateTime, startTime)
               .lt(CallLog::getCreateTime, endTime)
               .eq(CallLog::getStatus, 1); // 只统计成功的调用
        return callLogMapper.selectCount(wrapper);
    }

    /**
     * 统计总Token消耗
     */
    private Long countTotalTokens(LocalDateTime startTime, LocalDateTime endTime) {
        // TODO: 从call_log表sum(input_tokens + output_tokens)
        return 0L;
    }

    /**
     * 统计点数消耗
     */
    private BigDecimal sumPointsConsumed(LocalDateTime startTime, LocalDateTime endTime) {
        // TODO: 从points_bill表统计DEDUCT类型的点数总和
        return BigDecimal.ZERO;
    }

    /**
     * 统计充值金额
     */
    private BigDecimal sumRechargeAmount(LocalDateTime startTime, LocalDateTime endTime) {
        // TODO: 从billing_record表统计type=2（充值）的金额总和
        return BigDecimal.ZERO;
    }

    /**
     * 统计套餐销量
     */
    private Integer countPackageSales(LocalDateTime startTime, LocalDateTime endTime) {
        // TODO: 从订单表统计套餐购买数量
        return 0;
    }

    /**
     * 计算平均客单价
     */
    private BigDecimal calculateAvgOrderValue(BigDecimal totalRevenue, Integer sales) {
        if (sales == null || sales == 0) {
            return BigDecimal.ZERO;
        }
        return totalRevenue.divide(new BigDecimal(sales), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 获取模型使用量JSON
     */
    private String getModelUsageJson(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<Map<String, Object>> modelUsage = new ArrayList<>();
            
            // TODO: 从call_log表按model分组统计
            // 示例数据结构
            Map<String, Object> example = new HashMap<>();
            example.put("model", "deepseek-v4-flash");
            example.put("calls", 100);
            example.put("tokens", 50000);
            modelUsage.add(example);
            
            return objectMapper.writeValueAsString(modelUsage);
        } catch (Exception e) {
            log.error("生成模型使用量JSON失败", e);
            return "[]";
        }
    }

    /**
     * 获取小时流量分布JSON
     */
    private String getHourlyTrafficJson(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<Map<String, Object>> hourlyTraffic = new ArrayList<>();
            
            // 生成24小时的数据
            for (int hour = 0; hour < 24; hour++) {
                Map<String, Object> item = new HashMap<>();
                item.put("hour", String.format("%02d:00", hour));
                item.put("calls", 0); // TODO: 实际查询该小时的调用数
                hourlyTraffic.add(item);
            }
            
            return objectMapper.writeValueAsString(hourlyTraffic);
        } catch (Exception e) {
            log.error("生成小时流量JSON失败", e);
            return "[]";
        }
    }

    /**
     * 缓存统计数据到Redis
     */
    private void cacheStatistics(LocalDate date, PlatformStatistics stats) {
        try {
            String key = STATS_CACHE_PREFIX + date.toString();
            String json = objectMapper.writeValueAsString(stats);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.debug("统计数据缓存成功: date={}", date);
        } catch (Exception e) {
            log.error("缓存统计数据失败", e);
        }
    }

    /**
     * 从缓存获取统计数据
     */
    public PlatformStatistics getCachedStatistics(LocalDate date) {
        try {
            String key = STATS_CACHE_PREFIX + date.toString();
            String json = redisTemplate.opsForValue().get(key);
            
            if (json != null) {
                return objectMapper.readValue(json, PlatformStatistics.class);
            }
        } catch (Exception e) {
            log.error("从缓存获取统计数据失败", e);
        }
        
        // 缓存未命中，从数据库查询
        LambdaQueryWrapper<PlatformStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformStatistics::getStatDate, date);
        return statisticsMapper.selectOne(wrapper);
    }

    /**
     * 检查异常数据并告警
     */
    private void checkAndAlert(LocalDate date, PlatformStatistics stats) {
        // 获取前一天的数据进行对比
        LocalDate previousDay = date.minusDays(1);
        PlatformStatistics previousStats = getCachedStatistics(previousDay);
        
        if (previousStats == null) {
            log.warn("前一天统计数据不存在，无法进行异常检测");
            return;
        }
        
        // 检查调用量突增/突减
        if (previousStats.getTotalCalls() > 0) {
            double changeRate = (double)(stats.getTotalCalls() - previousStats.getTotalCalls()) 
                              / previousStats.getTotalCalls() * 100;
            
            if (changeRate > 200) {
                log.warn("【告警】调用量突增 {:.2f}%: date={}, current={}, previous={}", 
                        changeRate, date, stats.getTotalCalls(), previousStats.getTotalCalls());
                // TODO: 发送邮件/短信通知管理员
            } else if (changeRate < -50) {
                log.warn("【告警】调用量突降 {:.2f}%: date={}, current={}, previous={}", 
                        changeRate, date, stats.getTotalCalls(), previousStats.getTotalCalls());
                // TODO: 发送邮件/短信通知管理员
            }
        }
        
        // 检查充值金额异常
        if (previousStats.getRechargeAmount().compareTo(BigDecimal.ZERO) > 0) {
            double revenueChangeRate = stats.getRechargeAmount()
                    .subtract(previousStats.getRechargeAmount())
                    .divide(previousStats.getRechargeAmount(), 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            
            if (revenueChangeRate > 300) {
                log.warn("【告警】充值金额异常增长 {:.2f}%: date={}, current={}, previous={}", 
                        revenueChangeRate, date, stats.getRechargeAmount(), previousStats.getRechargeAmount());
                // TODO: 发送邮件/短信通知管理员
            }
        }
    }

    /**
     * 按日查询统计数据
     */
    public List<PlatformStatistics> getDailyStats(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<PlatformStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(PlatformStatistics::getStatDate, startDate)
               .le(PlatformStatistics::getStatDate, endDate)
               .orderByAsc(PlatformStatistics::getStatDate);
        
        return statisticsMapper.selectList(wrapper);
    }

    /**
     * 按周查询统计数据（最近N周）
     */
    public List<Map<String, Object>> getWeeklyStats(int weeks) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDate weekStart = today.minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            
            Map<String, Object> weekData = aggregateWeeklyStats(weekStart, weekEnd);
            weekData.put("week", weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) 
                              + " ~ " + weekEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            result.add(weekData);
        }
        
        return result;
    }

    /**
     * 聚合周统计数据
     */
    private Map<String, Object> aggregateWeeklyStats(LocalDate startDate, LocalDate endDate) {
        List<PlatformStatistics> dailyStats = getDailyStats(startDate, endDate);
        
        Map<String, Object> aggregated = new HashMap<>();
        int totalUsers = 0;
        int newUsers = 0;
        long totalCalls = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        for (PlatformStatistics stats : dailyStats) {
            totalUsers = Math.max(totalUsers, stats.getTotalUsers());
            newUsers += stats.getNewUsers();
            totalCalls += stats.getTotalCalls();
            totalRevenue = totalRevenue.add(stats.getRechargeAmount());
        }
        
        aggregated.put("totalUsers", totalUsers);
        aggregated.put("newUsers", newUsers);
        aggregated.put("totalCalls", totalCalls);
        aggregated.put("totalRevenue", totalRevenue);
        
        return aggregated;
    }

    /**
     * 按月查询统计数据（最近N月）
     */
    public List<Map<String, Object>> getMonthlyStats(int months) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            
            Map<String, Object> monthData = aggregateWeeklyStats(monthStart, monthEnd);
            monthData.put("month", monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            result.add(monthData);
        }
        
        return result;
    }

    /**
     * 导出Excel报表（简化版，返回CSV格式）
     */
    public String exportStatsToCsv(LocalDate startDate, LocalDate endDate, String type) {
        List<PlatformStatistics> statsList = getDailyStats(startDate, endDate);
        
        StringBuilder csv = new StringBuilder();
        
        if ("user".equals(type)) {
            // 用户数据报表
            csv.append("日期,总用户数,新增用户,活跃用户,留存用户\n");
            for (PlatformStatistics stats : statsList) {
                csv.append(String.format("%s,%d,%d,%d,%d\n",
                        stats.getStatDate(),
                        stats.getTotalUsers(),
                        stats.getNewUsers(),
                        stats.getActiveUsers(),
                        stats.getRetainedUsers()));
            }
        } else if ("revenue".equals(type)) {
            // 营收数据报表
            csv.append("日期,充值金额,套餐销量,平均客单价\n");
            for (PlatformStatistics stats : statsList) {
                csv.append(String.format("%s,%.2f,%d,%.2f\n",
                        stats.getStatDate(),
                        stats.getRechargeAmount(),
                        stats.getPackageSales(),
                        stats.getAvgOrderValue()));
            }
        } else if ("business".equals(type)) {
            // 业务数据报表
            csv.append("日期,对话次数,Token消耗,点数消耗\n");
            for (PlatformStatistics stats : statsList) {
                csv.append(String.format("%s,%d,%d,%.3f\n",
                        stats.getStatDate(),
                        stats.getTotalCalls(),
                        stats.getTotalTokens(),
                        stats.getTotalPointsConsumed()));
            }
        }
        
        return csv.toString();
    }
}
