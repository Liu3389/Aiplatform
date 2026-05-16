package com.ai.gateway.service;

import com.ai.gateway.entity.Conversation;
import com.ai.gateway.entity.DailyStatistics;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.CallLogMapper;
import com.ai.gateway.mapper.ConversationMapper;
import com.ai.gateway.mapper.DailyStatisticsMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 统计分析服务（核心）
 * 提供仪表盘、用户统计、对话统计等核心功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {
    
    private final StatisticsCounterService counterService;
    private final StatisticsCacheService cacheService;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final DailyStatisticsMapper dailyStatsMapper;
    private final CallLogMapper callLogMapper;
    
    /**
     * 获取仪表盘统计数据（从缓存或实时计算）
     * @param period 时间范围：7d/30d
     */
    public DashboardStatsVO getDashboardStats(String period) {
        // 1. 尝试从缓存获取
        DashboardStatsVO cached = cacheService.getDashboardStats(period, DashboardStatsVO.class);
        if (cached != null) {
            log.debug("从缓存获取仪表盘数据: {}", period);
            return cached;
        }
        
        // 2. 缓存未命中，实时计算
        log.info("实时计算仪表盘数据: {}", period);
        DashboardStatsVO stats = calculateDashboardStats(period);
        
        // 3. 存入缓存
        cacheService.cacheDashboardStats(period, stats);
        
        return stats;
    }
    
    /**
     * 计算仪表盘统计数据
     */
    private DashboardStatsVO calculateDashboardStats(String period) {
        DashboardStatsVO stats = new DashboardStatsVO();
        
        // 从计数器获取总数
        stats.setTotalUsers(counterService.getCounterValue("USER_TOTAL"));
        stats.setTotalConversations(counterService.getCounterValue("CONVERSATION_TOTAL"));
        
        // 从数据库查询今日数据
        LocalDate today = LocalDate.now();
        stats.setTodayNewUsers(Math.toIntExact(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, today.atStartOfDay()))));
        stats.setTodayConversations(Math.toIntExact(conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                .ge(Conversation::getCreateTime, today.atStartOfDay()))));
        
        // 计算今日活跃用户（假设最近一次更新时间在今天即为活跃）
        stats.setTodayActiveUsers(Math.toIntExact(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getUpdateTime, today.atStartOfDay()))));
        
        // 系统状态（简单判断）
        stats.setSystemStatus("normal");
        
        // 趋势数据
        int days = "7d".equals(period) ? 7 : 30;
        stats.setUserTrend(generateUserTrend(days));
        stats.setConversationTrend(generateConversationTrend(days));
        stats.setActiveUserTrend(generateActiveUserTrend(days));
        
        // 快速统计（修复 P2-40: 补充点数消耗和平均对话长度）
        DashboardStatsVO.QuickStatsVO quickStats = new DashboardStatsVO.QuickStatsVO();
        quickStats.setTotalMessages(Long.valueOf(counterService.getCounterValue("MESSAGE_TOTAL")));
        quickStats.setApiSuccessRate(calculateApiSuccessRate());
        
        // 计算总点数消耗（使用 SQL SUM 聚合）
        BigDecimal totalPointsConsumed = callLogMapper.sumCost();
        quickStats.setTotalPointsConsumed(totalPointsConsumed != null ? totalPointsConsumed : BigDecimal.ZERO);
        
        // 计算平均对话长度
        Long totalConv = conversationMapper.selectCount(null);
        if (totalConv != null && totalConv > 0) {
            quickStats.setAvgConversationLength((int) (quickStats.getTotalMessages() * 1.0 / totalConv));
        }
        
        stats.setQuickStats(quickStats);
        
        return stats;
    }
    
    /**
     * 获取用户统计数据
     */
    public UserStatsVO getUserStats() {
        // 尝试从缓存获取
        UserStatsVO cached = cacheService.getUserStats(UserStatsVO.class);
        if (cached != null) {
            return cached;
        }
        
        // 实时计算
        UserStatsVO stats = calculateUserStats();
        
        // 缓存
        cacheService.cacheUserStats(stats);
        
        return stats;
    }
    
    /**
     * 计算用户统计数据
     */
    private UserStatsVO calculateUserStats() {
        UserStatsVO stats = new UserStatsVO();
        
        // 基础统计
        stats.setTotalUsers(userMapper.selectCount(null).intValue());
        
        // 从数据库查询详细数据
        LocalDate today = LocalDate.now();
        stats.setTodayNewUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, today.atStartOfDay())).intValue());
        
        // 7天活跃用户
        stats.setActiveUsers7d(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getUpdateTime, today.minusDays(7).atStartOfDay())).intValue());
        
        // 30天未活跃用户
        stats.setInactiveUsers30d(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .lt(User::getUpdateTime, today.minusDays(30).atStartOfDay())).intValue());
        
        // 用户分层（修复 P0-9: 基于真实调用日志进行分层）
        UserStatsVO.UserSegmentVO segments = new UserStatsVO.UserSegmentVO();
        
        LocalDate now = LocalDate.now();
        LocalDateTime weekAgo = now.minusDays(7).atStartOfDay();
        LocalDateTime monthAgo = now.minusDays(30).atStartOfDay();
        
        // 高活跃：近7天有调用记录的独立用户数
        Long highActive = callLogMapper.countDistinctUserId(weekAgo, null);
        
        // 中活跃：近30天有调用记录但近7天没有的独立用户数
        Long mediumActive = callLogMapper.countDistinctUserId(monthAgo, weekAgo);
        
        segments.setHighActive(Math.toIntExact(highActive != null ? highActive : 0L));
        segments.setMediumActive(Math.toIntExact(mediumActive != null ? mediumActive : 0L));
        segments.setLowActive(stats.getTotalUsers() - segments.getHighActive() - segments.getMediumActive());
        segments.setInactive(stats.getInactiveUsers30d());
        stats.setUserSegments(segments);
        
        return stats;
    }
    
    /**
     * 获取对话统计数据
     */
    public ConversationStatsVO getConversationStats() {
        // 尝试从缓存获取
        ConversationStatsVO cached = cacheService.getConversationStats(ConversationStatsVO.class);
        if (cached != null) {
            return cached;
        }
        
        // 实时计算
        ConversationStatsVO stats = calculateConversationStats();
        
        // 缓存
        cacheService.cacheConversationStats(stats);
        
        return stats;
    }
    
    /**
     * 计算对话统计数据（修复 P0-12, P0-13: 完善模型统计字段）
     */
    private ConversationStatsVO calculateConversationStats() {
        ConversationStatsVO stats = new ConversationStatsVO();
        
        // 总量统计
        stats.setTotalConversations(conversationMapper.selectCount(null));
        stats.setTotalMessages(counterService.getCounterValue("MESSAGE_TOTAL"));
        
        // 计算平均值
        if (stats.getTotalConversations() > 0) {
            stats.setAvgMessagesPerConv(
                stats.getTotalMessages() * 1.0 / stats.getTotalConversations()
            );
        }
        
        // 从数据库查询今日数据
        LocalDate today = LocalDate.now();
        stats.setTodayConversations(Math.toIntExact(conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                .ge(Conversation::getCreateTime, today.atStartOfDay()))));
        stats.setTodayMessages(Math.toIntExact(counterService.getCounterValue("MESSAGE_TODAY")));
        stats.setAvgDuration(0); // 暂时保持为0，后续可从 call_log 聚合
        
        // 模型使用统计（修复：增加模型分布和热门排行）
        ConversationStatsVO.ModelUsageStatsVO modelUsage = new ConversationStatsVO.ModelUsageStatsVO();
        modelUsage.setTotalCalls(counterService.getCounterValue("API_CALL_TOTAL"));
        modelUsage.setSuccessRate(calculateApiSuccessRate());
        
        // 今日调用次数
        modelUsage.setTodayCalls(Math.toIntExact(callLogMapper.countTodayCalls(today.atStartOfDay())));
        
        // 获取模型分布（转换为 Map<String, Long>）
        List<Map<String, Object>> distribution = callLogMapper.getModelDistribution();
        Map<String, Long> modelDistMap = new LinkedHashMap<>();
        for (Map<String, Object> row : distribution) {
            String model = (String) row.get("model");
            Long calls = ((Number) row.get("calls")).longValue();
            modelDistMap.put(model, calls);
        }
        modelUsage.setModelDistribution(modelDistMap);
        
        // 获取热门模型排行（转换为 List<ModelRankVO>）
        List<Map<String, Object>> topModels = callLogMapper.getTopModels(5);
        Long totalCalls = modelUsage.getTotalCalls();
        List<ConversationStatsVO.ModelRankVO> rankList = new ArrayList<>();
        for (Map<String, Object> row : topModels) {
            ConversationStatsVO.ModelRankVO rank = new ConversationStatsVO.ModelRankVO();
            rank.setModelName((String) row.get("model"));
            rank.setCallCount(((Number) row.get("calls")).longValue());
            rank.setPercentage(totalCalls > 0 ? rank.getCallCount() * 100.0 / totalCalls : 0.0);
            rankList.add(rank);
        }
        modelUsage.setTopModels(rankList);
        
        stats.setModelUsage(modelUsage);
        
        // 平均对话时长（简单估算：总消息数 / 总会话数 * 2分钟）
        Long totalConv = conversationMapper.selectCount(null);
        if (totalConv != null && totalConv > 0) {
            stats.setAvgDuration((int) ((stats.getTotalMessages() * 1.0 / totalConv) * 2));
        }
        
        return stats;
    }
    
    /**
     * 生成用户趋势数据（修复：使用真实数据库查询）
     */
    private List<DashboardStatsVO.TrendDataVO> generateUserTrend(int days) {
        List<DashboardStatsVO.TrendDataVO> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        // 优化：一次性查询出所有数据，在内存中分组（减少数据库交互次数）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, today.minusDays(days).atStartOfDay());
        List<User> users = userMapper.selectList(wrapper);
        
        Map<String, Long> dateCountMap = users.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                u -> u.getCreateTime().toLocalDate().toString(),
                java.util.stream.Collectors.counting()
            ));

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            DashboardStatsVO.TrendDataVO data = new DashboardStatsVO.TrendDataVO();
            data.setDate(dateStr);
            data.setValue(dateCountMap.getOrDefault(dateStr, 0L));
            trend.add(data);
        }
        
        return trend;
    }
    
    /**
     * 生成对话趋势数据（修复：使用真实数据库查询）
     */
    private List<DashboardStatsVO.TrendDataVO> generateConversationTrend(int days) {
        List<DashboardStatsVO.TrendDataVO> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long count = conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                    .ge(Conversation::getCreateTime, date.atStartOfDay())
                    .lt(Conversation::getCreateTime, date.plusDays(1).atStartOfDay()));
            
            DashboardStatsVO.TrendDataVO data = new DashboardStatsVO.TrendDataVO();
            data.setDate(date.toString());
            data.setValue(count);
            trend.add(data);
        }
        
        return trend;
    }
    
    /**
     * 生成活跃用户趋势数据（修复 P0-8: 使用真实数据库查询）
     */
    private List<DashboardStatsVO.TrendDataVO> generateActiveUserTrend(int days) {
        List<DashboardStatsVO.TrendDataVO> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            
            // 修复 P1-7: 使用 COUNT(DISTINCT user_id) 替代错误的 groupBy 计数
            Long activeCount = callLogMapper.countDistinctUserId(start, end);
            
            DashboardStatsVO.TrendDataVO data = new DashboardStatsVO.TrendDataVO();
            data.setDate(date.toString());
            data.setValue(activeCount != null ? activeCount : 0L);
            trend.add(data);
        }
        
        return trend;
    }
    
    /**
     * 计算API成功率
     */
    private Double calculateApiSuccessRate() {
        Long total = counterService.getCounterValue("API_CALL_TOTAL");
        Long success = counterService.getCounterValue("API_CALL_SUCCESS");
        
        if (total == 0) {
            return 100.0;
        }
        
        return success * 100.0 / total;
    }
    
    /**
     * 按日查询统计数据
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 每日统计数据列表
     */
    public java.util.List<DailyStatistics> getDailyStats(LocalDate startDate, LocalDate endDate) {
        log.info("查询每日统计: {} 至 {}", startDate, endDate);
        LambdaQueryWrapper<DailyStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(DailyStatistics::getStatDate, startDate)
               .le(DailyStatistics::getStatDate, endDate)
               .orderByAsc(DailyStatistics::getStatDate);
        return dailyStatsMapper.selectList(wrapper);
    }
    
    /**
     * 按周查询统计数据
     * @param weeks 周数
     * @return 每周统计数据列表
     */
    public java.util.List<java.util.Map<String, Object>> getWeeklyStats(int weeks) {
        log.info("查询每周统计: 最近{}周", weeks);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusWeeks(weeks);
        
        List<Map<String, Object>> dbData = callLogMapper.aggregateByWeek(startDate.atStartOfDay());
        Map<String, Long> weekCountMap = new LinkedHashMap<>();
        for (Map<String, Object> row : dbData) {
            String week = String.valueOf(row.get("week"));
            Long count = ((Number) row.get("count")).longValue();
            weekCountMap.put(week, count);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDate weekStart = today.minusWeeks(i);
            int weekNum = weekStart.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            String weekKey = String.valueOf(weekStart.getYear() * 100 + weekNum);
            
            Map<String, Object> weekData = new HashMap<>();
            weekData.put("week", "W" + weekNum);
            weekData.put("newUsers", userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .ge(User::getCreateTime, weekStart.atStartOfDay())
                    .lt(User::getCreateTime, weekStart.plusDays(7).atStartOfDay())));
            weekData.put("conversations", weekCountMap.getOrDefault(weekKey, 0L));
            weekData.put("messages", 0);
            result.add(weekData);
        }
        
        return result;
    }
    
    /**
     * 按月查询统计数据
     * @param months 月数
     * @return 每月统计数据列表
     */
    public java.util.List<java.util.Map<String, Object>> getMonthlyStats(int months) {
        log.info("查询每月统计: 最近{}个月", months);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(months);
        
        List<Map<String, Object>> revenueData = callLogMapper.aggregateRevenueByMonth(startDate.atStartOfDay());
        Map<String, Double> revenueMap = new LinkedHashMap<>();
        for (Map<String, Object> row : revenueData) {
            String month = (String) row.get("month");
            Double revenue = ((Number) row.get("revenue")).doubleValue();
            revenueMap.put(month, revenue);
        }
        
        List<Map<String, Object>> userData = callLogMapper.aggregateUserGrowthByMonth(startDate.atStartOfDay());
        Map<String, Long> userMap = new LinkedHashMap<>();
        for (Map<String, Object> row : userData) {
            String month = (String) row.get("month");
            Long newUsers = ((Number) row.get("new_users")).longValue();
            userMap.put(month, newUsers);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthDate = today.minusMonths(i);
            String monthKey = monthDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", monthKey);
            monthData.put("newUsers", userMap.getOrDefault(monthKey, 0L));
            monthData.put("conversations", 0);
            monthData.put("messages", 0);
            monthData.put("revenue", revenueMap.getOrDefault(monthKey, 0.0));
            result.add(monthData);
        }
        
        return result;
    }
    
    /**
     * 导出统计报表为CSV格式（优化：使用批量聚合查询替代循环查询）
     */
    public String exportStatsToCsv(LocalDate startDate, LocalDate endDate, String type) {
        log.info("导出统计报表: 类型={}, 日期范围={}-{}", type, startDate, endDate);
        
        StringBuilder csv = new StringBuilder();
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        
        switch (type) {
            case "user":
                csv.append("日期,新增用户,活跃用户,总用户数\n");
                // 批量获取新增用户数据
                List<Map<String, Object>> newUserRows = userMapper.aggregateNewUsersByDay(startDateTime);
                Map<String, Long> newUserMap = new HashMap<>();
                for (Map<String, Object> row : newUserRows) {
                    newUserMap.put(String.valueOf(row.get("date")), ((Number) row.get("count")).longValue());
                }
                
                LocalDate current = startDate;
                while (!current.isAfter(endDate)) {
                    String dateStr = current.toString();
                    Long newUsers = newUserMap.getOrDefault(dateStr, 0L);
                    // 活跃用户和总用户数暂时保留逐日查询，或可增加更多 Mapper 方法进一步优化
                    Long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                            .ge(User::getUpdateTime, current.atStartOfDay())
                            .lt(User::getUpdateTime, current.plusDays(1).atStartOfDay()));
                    Long totalUsers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                            .le(User::getCreateTime, current.plusDays(1).atStartOfDay()));
                    csv.append(dateStr).append(",").append(newUsers).append(",").append(activeUsers).append(",").append(totalUsers).append("\n");
                    current = current.plusDays(1);
                }
                break;
                
            case "revenue":
                csv.append("日期,收入(元),点数消耗,调用次数\n");
                // 批量获取收入和调用数据
                List<Map<String, Object>> callRows = callLogMapper.aggregateByDate(startDateTime, endDateTime);
                Map<String, Map<String, Object>> revenueMap = new HashMap<>();
                for (Map<String, Object> row : callRows) {
                    revenueMap.put(String.valueOf(row.get("date")), row);
                }
                
                current = startDate;
                while (!current.isAfter(endDate)) {
                    String dateStr = current.toString();
                    Map<String, Object> dayData = revenueMap.getOrDefault(dateStr, new HashMap<>());
                    BigDecimal revenue = new BigDecimal(dayData.getOrDefault("cost", 0.0).toString());
                    Long calls = ((Number) dayData.getOrDefault("calls", 0L)).longValue();
                    csv.append(dateStr).append(",").append(revenue).append(",0.000,").append(calls).append("\n");
                    current = current.plusDays(1);
                }
                break;
                
            case "business":
                csv.append("日期,对话数,消息数,Token数,API调用\n");
                // 复用上面的 callRows 数据
                List<Map<String, Object>> businessRows = callLogMapper.aggregateByDate(startDateTime, endDateTime);
                Map<String, Long> callCountMap = new HashMap<>();
                for (Map<String, Object> row : businessRows) {
                    callCountMap.put(String.valueOf(row.get("date")), ((Number) row.get("calls")).longValue());
                }
                
                current = startDate;
                while (!current.isAfter(endDate)) {
                    String dateStr = current.toString();
                    Long calls = callCountMap.getOrDefault(dateStr, 0L);
                    Long conversations = conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                            .ge(Conversation::getCreateTime, current.atStartOfDay())
                            .lt(Conversation::getCreateTime, current.plusDays(1).atStartOfDay()));
                    csv.append(dateStr).append(",").append(conversations).append(",0,0,").append(calls).append("\n");
                    current = current.plusDays(1);
                }
                break;
                
            default:
                throw new IllegalArgumentException("无效的报表类型: " + type);
        }
        
        return csv.toString();
    }
}
