package com.ai.gateway.service;

import com.ai.gateway.entity.Conversation;
import com.ai.gateway.entity.DailyStatistics;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.ConversationMapper;
import com.ai.gateway.mapper.DailyStatisticsMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        
        // 快速统计
        DashboardStatsVO.QuickStatsVO quickStats = new DashboardStatsVO.QuickStatsVO();
        quickStats.setTotalMessages(counterService.getCounterValue("MESSAGE_TOTAL"));
        quickStats.setApiSuccessRate(calculateApiSuccessRate());
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
        
        // 用户分层
        UserStatsVO.UserSegmentVO segments = new UserStatsVO.UserSegmentVO();
        segments.setHighActive(0);
        segments.setMediumActive(0);
        segments.setLowActive(0);
        segments.setInactive(0);
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
     * 计算对话统计数据
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
        stats.setTodayMessages(Math.toIntExact(counterService.getCounterValue("MESSAGE_TODAY"))); // 假设计数器有今日消息数
        stats.setAvgDuration(0); // 暂时保持为0，后续可从 call_log 聚合
        
        // 模型使用统计
        ConversationStatsVO.ModelUsageStatsVO modelUsage = new ConversationStatsVO.ModelUsageStatsVO();
        modelUsage.setTotalCalls(counterService.getCounterValue("API_CALL_TOTAL"));
        modelUsage.setSuccessRate(calculateApiSuccessRate());
        stats.setModelUsage(modelUsage);
        
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
     * 生成活跃用户趋势数据（示例）
     */
    private List<DashboardStatsVO.TrendDataVO> generateActiveUserTrend(int days) {
        List<DashboardStatsVO.TrendDataVO> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DashboardStatsVO.TrendDataVO data = new DashboardStatsVO.TrendDataVO();
            data.setDate(date.toString());
            data.setValue((long) (Math.random() * 200)); // 示例数据
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
    public java.util.List<com.ai.gateway.entity.PlatformStatistics> getDailyStats(LocalDate startDate, LocalDate endDate) {
        // 修复：从 platform_statistics 表查询真实数据
        log.info("查询每日统计: {} 至 {}", startDate, endDate);
        LambdaQueryWrapper<com.ai.gateway.entity.PlatformStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(com.ai.gateway.entity.PlatformStatistics::getStatDate, startDate)
               .le(com.ai.gateway.entity.PlatformStatistics::getStatDate, endDate)
               .orderByAsc(com.ai.gateway.entity.PlatformStatistics::getStatDate);
        return dailyStatsMapper.selectList(wrapper);
    }
    
    /**
     * 按周查询统计数据
     * @param weeks 周数
     * @return 每周统计数据列表
     */
    public java.util.List<java.util.Map<String, Object>> getWeeklyStats(int weeks) {
        // TODO: 实现按周聚合统计数据
        log.info("查询每周统计: 最近{}周", weeks);
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 示例数据结构
        for (int i = weeks - 1; i >= 0; i--) {
            Map<String, Object> weekData = new HashMap<>();
            weekData.put("week", "第" + (weeks - i) + "周");
            weekData.put("newUsers", 0);
            weekData.put("conversations", 0);
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
        // TODO: 实现按月聚合统计数据
        log.info("查询每月统计: 最近{}个月", months);
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 示例数据结构
        for (int i = months - 1; i >= 0; i--) {
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", "第" + (months - i) + "月");
            monthData.put("newUsers", 0);
            monthData.put("conversations", 0);
            monthData.put("messages", 0);
            monthData.put("revenue", 0.0);
            result.add(monthData);
        }
        
        return result;
    }
    
    /**
     * 导出统计报表为CSV格式
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param type 报表类型：user/revenue/business
     * @return CSV格式的字符串
     */
    public String exportStatsToCsv(LocalDate startDate, LocalDate endDate, String type) {
        log.info("导出统计报表: 类型={}, 日期范围={}-{}", type, startDate, endDate);
        
        StringBuilder csv = new StringBuilder();
        
        // 根据类型生成不同的CSV
        switch (type) {
            case "user":
                csv.append("日期,新增用户,活跃用户,总用户数\n");
                // TODO: 从数据库查询实际数据
                for (int i = 0; i < 7; i++) {
                    LocalDate date = startDate.plusDays(i);
                    csv.append(date).append(",0,0,0\n");
                }
                break;
                
            case "revenue":
                csv.append("日期,收入,点数消耗,订单数\n");
                // TODO: 从数据库查询实际数据
                for (int i = 0; i < 7; i++) {
                    LocalDate date = startDate.plusDays(i);
                    csv.append(date).append(",0.00,0.000,0\n");
                }
                break;
                
            case "business":
                csv.append("日期,对话数,消息数,Token数,API调用\n");
                // TODO: 从数据库查询实际数据
                for (int i = 0; i < 7; i++) {
                    LocalDate date = startDate.plusDays(i);
                    csv.append(date).append(",0,0,0,0\n");
                }
                break;
                
            default:
                throw new IllegalArgumentException("无效的报表类型: " + type);
        }
        
        return csv.toString();
    }
}
