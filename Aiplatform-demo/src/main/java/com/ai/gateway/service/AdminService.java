package com.ai.gateway.service;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.entity.CallLog;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.ApiKeyMapper;
import com.ai.gateway.mapper.CallLogMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.util.DataMaskUtil;
import com.ai.gateway.vo.SystemStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final ApiKeyMapper apiKeyMapper;
    private final CallLogMapper callLogMapper;
    private final UserMapper userMapper;
    private final PointsService pointsService;

    public SystemStatsVO getSystemStats() {
        SystemStatsVO stats = new SystemStatsVO();

        stats.setTotalUsers(userService.countUsers());
        stats.setActiveUsers(userService.countActiveUsers());

        Long totalApiKeys = apiKeyMapper.selectCount(null);
        stats.setTotalApiKeys(totalApiKeys);

        // 优化：使用批量查询而不是为每个用户单独查询
        List<User> users = userService.listAllUsers();
        
        // 批量获取所有用户的API Key数量
        Map<Long, Integer> apiKeyCountMap = new HashMap<>();
        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            List<Map<String, Object>> apiKeyCounts = apiKeyMapper.countApiKeysByUserIds(userIds);
            for (Map<String, Object> row : apiKeyCounts) {
                Long userId = ((Number) row.get("user_id")).longValue();
                Integer count = ((Number) row.get("count")).intValue();
                apiKeyCountMap.put(userId, count);
            }
        }
        
        // 批量获取所有用户的调用次数和消费金额
        Map<Long, Long> userCallsMap = new HashMap<>();
        Map<Long, BigDecimal> userCostMap = new HashMap<>();
        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            List<Map<String, Object>> callStats = callLogMapper.getUserCallStats(userIds);
            for (Map<String, Object> row : callStats) {
                Long userId = ((Number) row.get("user_id")).longValue();
                userCallsMap.put(userId, ((Number) row.get("calls")).longValue());
                userCostMap.put(userId, new BigDecimal(row.get("cost").toString()));
            }
        }
        
        List<SystemStatsVO.UserStatVO> userStats = users.stream().map(user -> {
            SystemStatsVO.UserStatVO vo = new SystemStatsVO.UserStatVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());
            vo.setBalance(user.getBalance());
            vo.setRole(user.getRole());
            vo.setStatus(user.getStatus() == 1 ? "ACTIVE" : "DISABLED");

            vo.setApiKeyCount(apiKeyCountMap.getOrDefault(user.getId(), 0));
            vo.setTotalCalls(userCallsMap.getOrDefault(user.getId(), 0L));
            vo.setTotalCost(userCostMap.getOrDefault(user.getId(), BigDecimal.ZERO));
            vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            return vo;
        }).collect(Collectors.toList());

        stats.setUsers(userStats);
        
        // 从数据库聚合真实调用次数和消费金额
        stats.setTotalCalls(callLogMapper.selectCount(null));
        stats.setTotalCost(callLogMapper.sumCost());

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        stats.setTodayCalls(callLogMapper.countTodayCalls(startOfDay));
        
        BigDecimal todayCost = callLogMapper.sumCostByDate(startOfDay);
        stats.setTodayCost(todayCost != null ? todayCost : BigDecimal.ZERO);

        return stats;
    }

    public SystemStatsVO.UserStatVO getUserDetail(Long userId) {
        User user = userService.getUserEntityById(userId);
        SystemStatsVO.UserStatVO vo = new SystemStatsVO.UserStatVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(DataMaskUtil.maskEmail(user.getEmail()));
        vo.setBalance(user.getBalance());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus() == 1 ? "ACTIVE" : "DISABLED");

        LambdaQueryWrapper<ApiKey> keyWrapper = new LambdaQueryWrapper<>();
        keyWrapper.eq(ApiKey::getUserId, userId);
        vo.setApiKeyCount(apiKeyMapper.selectCount(keyWrapper).intValue());

        // 修复 P0-3: 从 call_log 聚合真实调用数据
        Map<String, Object> callStats = callLogMapper.getUserCallStatsSingle(userId);
        vo.setTotalCalls(callStats != null ? ((Number) callStats.get("calls")).longValue() : 0L);
        vo.setTotalCost(callStats != null ? new BigDecimal(callStats.get("cost").toString()) : BigDecimal.ZERO);
        vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);

        return vo;
    }

    public List<Map<String, Object>> getDailyUsage(int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1);
        List<Map<String, Object>> dbData = callLogMapper.aggregateByDate(startDate.atStartOfDay(), today.plusDays(1).atStartOfDay());
        
        Map<String, Map<String, Object>> dateMap = new LinkedHashMap<>();
        for (Map<String, Object> row : dbData) {
            String date = String.valueOf(row.get("date"));
            dateMap.put(date, row);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            Map<String, Object> row = dateMap.get(dateStr);
            
            Map<String, Object> item = new HashMap<>();
            item.put("date", dateStr);
            if (row != null) {
                item.put("calls", ((Number) row.get("calls")).longValue());
                item.put("cost", new BigDecimal(row.get("cost").toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
            } else {
                item.put("calls", 0L);
                item.put("cost", BigDecimal.ZERO);
            }
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getDailyUsage() {
        return getDailyUsage(7);
    }

    public List<Map<String, Object>> getModelUsage() {
        List<Map<String, Object>> distribution = callLogMapper.getModelDistribution();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : distribution) {
            Map<String, Object> item = new HashMap<>();
            item.put("model", row.get("model"));
            item.put("calls", row.get("calls"));
            item.put("cost", new BigDecimal(row.get("cost").toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getHourlyTraffic() {
        List<Map<String, Object>> dbData = callLogMapper.aggregateByHour();
        Map<Integer, Long> hourMap = new HashMap<>();
        for (Map<String, Object> row : dbData) {
            int hour = ((Number) row.get("hour")).intValue();
            long calls = ((Number) row.get("calls")).longValue();
            hourMap.put(hour, calls);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", String.format("%02d:00", h));
            item.put("calls", hourMap.getOrDefault(h, 0L));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getTopUsers() {
        List<Map<String, Object>> result = callLogMapper.getTopUsersByCost(10);
        for (int i = 0; i < result.size(); i++) {
            result.get(i).put("rank", i + 1);
        }
        return result;
    }

    public List<Map<String, Object>> getRevenueTrend(int months) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(months - 1).withDayOfMonth(1);
        List<Map<String, Object>> dbData = callLogMapper.aggregateRevenueByMonth(startDate.atStartOfDay());
        
        Map<String, Double> revenueMap = new LinkedHashMap<>();
        for (Map<String, Object> row : dbData) {
            String month = (String) row.get("month");
            Double revenue = ((Number) row.get("revenue")).doubleValue();
            revenueMap.put(month, revenue);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (int m = months - 1; m >= 0; m--) {
            LocalDate monthDate = today.minusMonths(m);
            String monthKey = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            Map<String, Object> item = new HashMap<>();
            item.put("month", monthKey);
            item.put("revenue", revenueMap.getOrDefault(monthKey, 0.0));
            item.put("costs", 0.0);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getRevenueTrend() {
        return getRevenueTrend(6);
    }

    public List<Map<String, Object>> getUserGrowth(int months) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(months - 1).withDayOfMonth(1);
        
        // 修复 P1-4: 一次性查询出所有月份的新增用户数，避免 N+1
        List<Map<String, Object>> userData = userMapper.selectList(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, startDate.atStartOfDay())).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    u -> u.getCreateTime().toLocalDate().withDayOfMonth(1).toString(),
                    java.util.stream.Collectors.counting()
                )).entrySet().stream().map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("month", e.getKey().substring(0, 7));
                    m.put("new_users", e.getValue());
                    return m;
                }).collect(java.util.stream.Collectors.toList());
        
        Map<String, Long> userMap = new LinkedHashMap<>();
        for (Map<String, Object> row : userData) {
            String month = (String) row.get("month");
            Long newUsers = ((Number) row.get("new_users")).longValue();
            userMap.put(month, newUsers);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        long cumulativeTotal = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .lt(User::getCreateTime, startDate.atStartOfDay()));
        
        for (int m = months - 1; m >= 0; m--) {
            LocalDate monthDate = today.minusMonths(m);
            String monthKey = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            Long newUsers = userMap.getOrDefault(monthKey, 0L);
            cumulativeTotal += newUsers;
            
            // 修复 P1-4: 同样优化活跃用户查询（此处简化为按月聚合）
            Long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .ge(User::getUpdateTime, monthDate.withDayOfMonth(1).atStartOfDay())
                    .lt(User::getUpdateTime, monthDate.plusMonths(1).withDayOfMonth(1).atStartOfDay()));
            
            Map<String, Object> item = new HashMap<>();
            item.put("month", monthKey);
            item.put("total", cumulativeTotal);
            item.put("active", activeUsers);
            item.put("new", newUsers);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getUserGrowth() {
        return getUserGrowth(6);
    }

    public Map<String, Object> getUserPage(int page, int size, String search, String membership, String status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (search != null && !search.isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, search).or().eq(User::getId, tryParseLong(search)));
        }
        if (membership != null && !membership.isEmpty()) {
            wrapper.eq(User::getMembership, membership);
        }
        wrapper.orderByDesc(User::getId);

        Page<User> userPage = new Page<>(page, size);
        Page<User> result = userMapper.selectPage(userPage, wrapper);

        // 修复 P2-36: 批量获取用户调用统计，避免 N+1 查询
        List<Long> userIds = result.getRecords().stream().map(User::getId).collect(Collectors.toList());
        Map<Long, Long> userCallsMap = new HashMap<>();
        Map<Long, BigDecimal> userCostMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<Map<String, Object>> callStats = callLogMapper.getUserCallStats(userIds);
            for (Map<String, Object> row : callStats) {
                Long uid = ((Number) row.get("user_id")).longValue();
                userCallsMap.put(uid, ((Number) row.get("calls")).longValue());
                userCostMap.put(uid, new BigDecimal(row.get("cost").toString()));
            }
        }

        List<Map<String, Object>> users = result.getRecords().stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("email", DataMaskUtil.maskEmail(user.getEmail()));
            item.put("balance", user.getBalance());
            item.put("role", user.getRole());
            item.put("status", user.getStatus() == 1 ? "ACTIVE" : "DISABLED");
            item.put("membership", user.getMembership() != null ? user.getMembership() : "free");
            item.put("totalCalls", userCallsMap.getOrDefault(user.getId(), 0L));
            item.put("totalCost", userCostMap.getOrDefault(user.getId(), BigDecimal.ZERO));
            item.put("createTime", user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> pageResult = new HashMap<>();
        pageResult.put("records", users);
        pageResult.put("total", result.getTotal());
        pageResult.put("page", page);
        pageResult.put("size", size);
        return pageResult;
    }

    private Long tryParseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1L; }
    }

    // ==================== 用户管理增强 ====================

    /**
     * 分页查询用户（支持多条件筛选）
     */
    public Map<String, Object> getUserPageAdvanced(int page, int size, String search, 
                                                    String membership, String status,
                                                    BigDecimal minBalance, BigDecimal maxBalance,
                                                    String startDate, String endDate) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        
        // 搜索条件（用户名或ID）
        if (search != null && !search.isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, search)
                             .or()
                             .eq(User::getId, tryParseLong(search)));
        }
        
        // 会员等级筛选
        if (membership != null && !membership.isEmpty()) {
            wrapper.eq(User::getMembership, membership);
        }
        
        // 状态筛选
        if (status != null && !status.isEmpty()) {
            if ("ACTIVE".equals(status)) {
                wrapper.eq(User::getStatus, 1);
            } else if ("DISABLED".equals(status)) {
                wrapper.eq(User::getStatus, 0);
            }
        }
        
        // 点数余额范围筛选
        if (minBalance != null) {
            wrapper.ge(User::getBalance, minBalance);
        }
        if (maxBalance != null) {
            wrapper.le(User::getBalance, maxBalance);
        }
        
        // 注册时间范围筛选
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(User::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.lt(User::getCreateTime, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        
        wrapper.orderByDesc(User::getId);

        Page<User> userPage = new Page<>(page, size);
        Page<User> result = userMapper.selectPage(userPage, wrapper);

        // 修复 P0-2: 批量获取用户调用统计，避免 N+1 查询
        List<Long> userIds = result.getRecords().stream().map(User::getId).collect(Collectors.toList());
        Map<Long, Long> userCallsMap = new HashMap<>();
        Map<Long, BigDecimal> userCostMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<Map<String, Object>> callStats = callLogMapper.getUserCallStats(userIds);
            for (Map<String, Object> row : callStats) {
                Long uid = ((Number) row.get("user_id")).longValue();
                userCallsMap.put(uid, ((Number) row.get("calls")).longValue());
                userCostMap.put(uid, new BigDecimal(row.get("cost").toString()));
            }
        }

        List<Map<String, Object>> users = result.getRecords().stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("email", DataMaskUtil.maskEmail(user.getEmail()));
            item.put("balance", user.getBalance());
            item.put("points", user.getPoints());
            item.put("role", user.getRole());
            item.put("status", user.getStatus() == 1 ? "ACTIVE" : "DISABLED");
            item.put("membership", user.getMembership() != null ? user.getMembership() : "free");
            item.put("dailyFreeCount", user.getDailyFreeCount());
            item.put("membershipExpireTime", user.getMembershipExpireTime());
            item.put("totalCalls", userCallsMap.getOrDefault(user.getId(), 0L));
            item.put("totalCost", userCostMap.getOrDefault(user.getId(), BigDecimal.ZERO));
            item.put("createTime", user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> pageResult = new HashMap<>();
        pageResult.put("records", users);
        pageResult.put("total", result.getTotal());
        pageResult.put("page", page);
        pageResult.put("size", size);
        return pageResult;
    }

    // ==================== 批量操作 ====================

    /**
     * 批量封禁/解封用户（修复 P1-19: 使用 LambdaUpdateWrapper 批量更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateStatus(List<Long> userIds, int status) {
        if (userIds == null || userIds.isEmpty()) return 0;
        
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> wrapper = 
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.in(User::getId, userIds).set(User::getStatus, status);
        
        int rows = userMapper.update(null, wrapper);
        log.info("批量更新用户状态: 总数={}, 成功={}, 新状态={}", userIds.size(), rows, status);
        return rows;
    }

    /**
     * 批量发放点数（修复 P1-5: 优化 businessId 生成策略）
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchGrantPoints(List<Long> userIds, BigDecimal points) {
        if (userIds.size() > 1000) {
            throw new IllegalArgumentException("批量操作用户数不能超过 1000 个");
        }
        
        int successCount = 0;
        long baseTime = System.currentTimeMillis();
        String uuidSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        for (int i = 0; i < userIds.size(); i++) {
            Long userId = userIds.get(i);
            String businessId = "admin_batch_" + baseTime + "_" + uuidSuffix + "_" + i + "_" + userId;
            pointsService.addPoints(userId, points, "GRANT", businessId, 
                    "管理员批量发放点数", null);
            successCount++;
        }
        log.info("批量发放点数: 总数={}, 成功={}, 点数={}", userIds.size(), successCount, points);
        return successCount;
    }

    // ==================== 点数管理 ====================

    /**
     * 手动增减用户点数
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustUserPoints(Long userId, BigDecimal points, String reason) {
        String changeType = points.compareTo(BigDecimal.ZERO) >= 0 ? "GRANT" : "DEDUCT";
        String businessId = "admin_adjust_" + System.currentTimeMillis();
        
        // 修复 P1-9: 传入原始数值（含符号），确保账单记录与实际变动一致
        pointsService.addPoints(userId, points, changeType, businessId, 
                "管理员调整: " + reason, null);
        
        log.info("调整用户点数: userId={}, 调整={}, 类型={}, 原因={}", userId, points, changeType, reason);
    }

    /**
     * 设置用户每日免费次数
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDailyFreeCount(Long userId, int count) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        user.setDailyFreeCount(count);
        userMapper.updateById(user);
        
        log.info("设置用户每日免费次数: userId={}, count={}", userId, count);
    }

    // ==================== 会员管理 ====================

    /**
     * 调整用户会员等级
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMembership(Long userId, String membership) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        user.setMembership(membership);
        userMapper.updateById(user);
        
        log.info("调整用户会员等级: userId={}, membership={}", userId, membership);
    }

    /**
     * 设置会员有效期
     */
    @Transactional(rollbackFor = Exception.class)
    public void setMembershipExpireTime(Long userId, LocalDateTime expireTime) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        user.setMembershipExpireTime(expireTime);
        userMapper.updateById(user);
        
        log.info("设置会员有效期: userId={}, expireTime={}", userId, expireTime);
    }

    /**
     * 批量开通会员（修复 P1-24: 使用 LambdaUpdateWrapper 批量更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchActivateMembership(List<Long> userIds, String membership, int days) {
        LocalDateTime expireTime = LocalDateTime.now().plusDays(days);
        
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> wrapper = 
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.in(User::getId, userIds)
               .set(User::getMembership, membership)
               .set(User::getMembershipExpireTime, expireTime);
        
        int rows = userMapper.update(null, wrapper);
        
        log.info("批量开通会员: 总数={}, 成功={}, 等级={}, 天数={}", 
                userIds.size(), rows, membership, days);
        return rows;
    }
}
