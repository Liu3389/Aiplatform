package com.ai.gateway.service;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.ApiKeyMapper;
import com.ai.gateway.mapper.CallLogMapper;
import com.ai.gateway.mapper.UserMapper;
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
        
        List<SystemStatsVO.UserStatVO> userStats = users.stream().map(user -> {
            SystemStatsVO.UserStatVO vo = new SystemStatsVO.UserStatVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());
            vo.setBalance(user.getBalance());
            vo.setRole(user.getRole());
            vo.setStatus(user.getStatus() == 1 ? "ACTIVE" : "DISABLED");

            // 使用批量查询的结果
            vo.setApiKeyCount(apiKeyCountMap.getOrDefault(user.getId(), 0));

            vo.setTotalCalls(0L);
            vo.setTotalCost(BigDecimal.ZERO);
            vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            return vo;
        }).collect(Collectors.toList());

        stats.setUsers(userStats);
        stats.setTotalCalls(0L);
        stats.setTotalCost(BigDecimal.ZERO);
        stats.setTodayCalls(0L);
        stats.setTodayCost(BigDecimal.ZERO);

        return stats;
    }

    public SystemStatsVO.UserStatVO getUserDetail(Long userId) {
        User user = userService.getUserEntityById(userId);
        SystemStatsVO.UserStatVO vo = new SystemStatsVO.UserStatVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setBalance(user.getBalance());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus() == 1 ? "ACTIVE" : "DISABLED");

        LambdaQueryWrapper<ApiKey> keyWrapper = new LambdaQueryWrapper<>();
        keyWrapper.eq(ApiKey::getUserId, userId);
        vo.setApiKeyCount(apiKeyMapper.selectCount(keyWrapper).intValue());

        vo.setTotalCalls(0L);
        vo.setTotalCost(BigDecimal.ZERO);
        vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);

        return vo;
    }

    public List<Map<String, Object>> getDailyUsage(int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", today.minusDays(i).toString());
            item.put("calls", new Random().nextInt(500) + 100);
            item.put("tokens", new Random().nextInt(50000) + 10000);
            item.put("cost", Math.round((Math.random() * 50 + 5) * 100.0) / 100.0);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getDailyUsage() {
        return getDailyUsage(7);
    }

    public List<Map<String, Object>> getModelUsage() {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] models = {"deepseek-v4-pro", "deepseek-v4-flash", "gpt-4o", "gpt-3.5-turbo"};
        String[] colors = {"#818cf8", "#6366f1", "#14b8a6", "#f59e0b"};
        for (int i = 0; i < models.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("model", models[i]);
            item.put("calls", new Random().nextInt(300) + 50);
            item.put("tokens", new Random().nextInt(30000) + 5000);
            item.put("cost", Math.round((Math.random() * 30 + 2) * 100.0) / 100.0);
            item.put("color", colors[i]);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getHourlyTraffic() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", String.format("%02d:00", h));
            item.put("calls", new Random().nextInt(80) + 5);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getTopUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<User> users = userService.listAllUsers();
        int rank = 1;
        for (User user : users.stream().sorted((a, b) -> b.getBalance().compareTo(a.getBalance())).limit(10).toList()) {
            Map<String, Object> item = new HashMap<>();
            item.put("rank", rank++);
            item.put("username", user.getUsername());
            item.put("userId", user.getId());
            item.put("calls", new Random().nextInt(100) + 10);
            item.put("cost", Math.round((Math.random() * 20 + 1) * 100.0) / 100.0);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getRevenueTrend(int months) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int m = months - 1; m >= 0; m--) {
            Map<String, Object> item = new HashMap<>();
            item.put("month", LocalDate.now().minusMonths(m).format(DateTimeFormatter.ofPattern("yyyy-MM")));
            item.put("revenue", Math.round((Math.random() * 1000 + 200) * 100.0) / 100.0);
            item.put("costs", Math.round((Math.random() * 800 + 100) * 100.0) / 100.0);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getRevenueTrend() {
        return getRevenueTrend(6);
    }

    public List<Map<String, Object>> getUserGrowth(int months) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int m = months - 1; m >= 0; m--) {
            int total = 10 + (months - m) * new Random().nextInt(20);
            Map<String, Object> item = new HashMap<>();
            item.put("month", LocalDate.now().minusMonths(m).format(DateTimeFormatter.ofPattern("yyyy-MM")));
            item.put("total", total);
            item.put("active", total * 3 / 4);
            item.put("new", new Random().nextInt(10) + 1);
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

        List<Map<String, Object>> users = result.getRecords().stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("email", user.getEmail());
            item.put("balance", user.getBalance());
            item.put("role", user.getRole());
            item.put("status", user.getStatus() == 1 ? "ACTIVE" : "DISABLED");
            item.put("membership", user.getMembership() != null ? user.getMembership() : "free");
            item.put("totalCalls", 0L);
            item.put("totalCost", BigDecimal.ZERO);
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

        List<Map<String, Object>> users = result.getRecords().stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("email", user.getEmail());
            item.put("balance", user.getBalance());
            item.put("points", user.getPoints());
            item.put("role", user.getRole());
            item.put("status", user.getStatus() == 1 ? "ACTIVE" : "DISABLED");
            item.put("membership", user.getMembership() != null ? user.getMembership() : "free");
            item.put("dailyFreeCount", user.getDailyFreeCount());
            item.put("membershipExpireTime", user.getMembershipExpireTime());
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
     * 批量封禁/解封用户
     */
    @Transactional
    public int batchUpdateStatus(List<Long> userIds, int status) {
        int successCount = 0;
        for (Long userId : userIds) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setStatus(status);
                userMapper.updateById(user);
                successCount++;
            }
        }
        log.info("批量更新用户状态: 总数={}, 成功={}, 新状态={}", userIds.size(), successCount, status);
        return successCount;
    }

    /**
     * 批量发放点数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchGrantPoints(List<Long> userIds, BigDecimal points) {
        int successCount = 0;
        for (Long userId : userIds) {
            try {
                // ✅ 使用 PointsService 统一处理，确保数据库+Redis+账单一致
                String businessId = "admin_batch_" + System.currentTimeMillis() + "_" + userId;
                pointsService.addPoints(userId, points, "GRANT", businessId, 
                        "管理员批量发放点数", null);
                successCount++;
            } catch (Exception e) {
                log.error("批量发放点数失败: userId={}, error={}", userId, e.getMessage());
            }
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
        // ✅ 使用 PointsService 统一处理，确保数据库+Redis+账单一致
        String changeType = points.compareTo(BigDecimal.ZERO) >= 0 ? "GRANT" : "DEDUCT";
        String businessId = "admin_adjust_" + System.currentTimeMillis();
        
        pointsService.addPoints(userId, points.abs(), changeType, businessId, 
                "管理员调整: " + reason, null);
        
        log.info("调整用户点数: userId={}, 调整={}, 原因={}", userId, points, reason);
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
     * 批量开通会员
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchActivateMembership(List<Long> userIds, String membership, int days) {
        LocalDateTime expireTime = LocalDateTime.now().plusDays(days);
        int successCount = 0;
        
        for (Long userId : userIds) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setMembership(membership);
                user.setMembershipExpireTime(expireTime);
                userMapper.updateById(user);
                successCount++;
            }
        }
        
        log.info("批量开通会员: 总数={}, 成功={}, 等级={}, 天数={}", 
                userIds.size(), successCount, membership, days);
        return successCount;
    }
}
