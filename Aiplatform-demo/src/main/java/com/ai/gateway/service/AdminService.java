package com.ai.gateway.service;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.entity.PlatformStatistics;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserContactVerification;
import com.ai.gateway.entity.UserOAuthIdentity;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.dto.AdminUpdateUserProfileRequest;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.*;
import com.ai.gateway.util.DataMaskUtil;
import com.ai.gateway.vo.AdminUserDetailVO;
import com.ai.gateway.vo.SystemStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final ApiKeyMapper apiKeyMapper;
    private final CallLogMapper callLogMapper;
    private final UserMapper userMapper;
    private final UserOAuthIdentityMapper userOAuthIdentityMapper;
    private final UserContactVerificationMapper contactVerificationMapper;
    private final OssService ossService;
    private final PointsService pointsService;
    private final StringRedisTemplate redisTemplate;
    private final PlatformStatisticsMapper platformStatisticsMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;

    public SystemStatsVO getSystemStats() {
        SystemStatsVO stats = new SystemStatsVO();

        stats.setTotalUsers(userService.countUsers());
        stats.setActiveUsers(userService.countActiveUsers());

        Long totalApiKeys = 0L;
        try {
            // 修复 P1-1: 使用缓存计数器替代全表COUNT(*)查询
            String cacheKey = "apikey:count:approx";
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                totalApiKeys = Long.parseLong(cached);
            } else {
                totalApiKeys = apiKeyMapper.selectCount(null);
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(totalApiKeys), 30, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("获取API Key总数失败", e);
        }
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
        
        // 批量获取所有用户的调用次数和点数消耗
        Map<Long, Long> userCallsMap = new HashMap<>();
        Map<Long, BigDecimal> userPointsMap = new HashMap<>();
        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            List<Map<String, Object>> callStats = callLogMapper.getUserCallStats(userIds);
            for (Map<String, Object> row : callStats) {
                Long userId = ((Number) row.get("user_id")).longValue();
                userCallsMap.put(userId, ((Number) row.get("calls")).longValue());
                userPointsMap.put(userId, new BigDecimal(row.get("points").toString()));
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
            vo.setTotalPointsConsumed(userPointsMap.getOrDefault(user.getId(), BigDecimal.ZERO));
            vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            return vo;
        }).collect(Collectors.toList());

        stats.setUsers(userStats);
        
        // 从数据库聚合真实调用次数和消费金额
        Long totalCalls = 0L;
        try {
            // 修复 P1-1: 使用缓存计数器替代全表COUNT(*)查询
            String cacheKey = "calllog:count:approx";
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                totalCalls = Long.parseLong(cached);
            } else {
                totalCalls = callLogMapper.selectCount(null);
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(totalCalls), 30, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("获取调用总数失败", e);
        }
        stats.setTotalCalls(totalCalls);
        stats.setTotalPointsConsumed(callLogMapper.sumPointsConsumed());

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        stats.setTodayCalls(callLogMapper.countTodayCalls(startOfDay));
        
        BigDecimal todayPoints = callLogMapper.sumPointsByDate(startOfDay);
        stats.setTodayPointsConsumed(todayPoints != null ? todayPoints : BigDecimal.ZERO);

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
        vo.setTotalPointsConsumed(callStats != null ? new BigDecimal(callStats.get("points").toString()) : BigDecimal.ZERO);
        vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);

        return vo;
    }

    public AdminUserDetailVO getAdminUserDetail(Long userId) {
        User user = userService.getUserEntityById(userId);
        com.ai.gateway.vo.UserInfoVO profile = userService.getUserInfo(userId);
        AdminUserDetailVO detail = new AdminUserDetailVO();
        detail.setId(user.getId());
        detail.setUsername(user.getUsername());
        detail.setNickname(user.getNickname());
        detail.setDisplayName(user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname() : profile.getDisplayName());
        detail.setAvatarUrl(profile.getAvatarUrl());
        detail.setCustomAvatar(profile.isCustomAvatar());
        detail.setEmail(user.getEmail());
        detail.setEmailVerified(profile.isEmailVerified());
        detail.setPhone(user.getPhone());
        detail.setPhoneVerified(profile.isPhoneVerified());
        detail.setBalance(user.getBalance());
        detail.setPoints(user.getPoints());
        detail.setRole(user.getRole());
        detail.setStatus(user.getStatus() == 1 ? "ACTIVE" : "DISABLED");
        detail.setMembership(user.getMembership() == null ? "free" : user.getMembership());
        detail.setMembershipExpireTime(user.getMembershipExpireTime());
        detail.setLastLoginTime(user.getLastLoginTime());
        detail.setCreateTime(user.getCreateTime());

        LambdaQueryWrapper<ApiKey> keyWrapper = new LambdaQueryWrapper<>();
        keyWrapper.eq(ApiKey::getUserId, userId);
        detail.setApiKeyCount(apiKeyMapper.selectCount(keyWrapper).intValue());
        Map<String, Object> callStats = callLogMapper.getUserCallStatsSingle(userId);
        detail.setTotalCalls(callStats == null ? 0L : ((Number) callStats.get("calls")).longValue());
        detail.setTotalPointsConsumed(callStats == null ? BigDecimal.ZERO : new BigDecimal(callStats.get("points").toString()));
        detail.setOauthAccounts(userOAuthIdentityMapper.selectByUserId(userId).stream().map(identity -> {
            AdminUserDetailVO.OAuthAccount item = new AdminUserDetailVO.OAuthAccount();
            item.setProvider(identity.getProvider());
            item.setDisplayName(identity.getDisplayName());
            item.setEmail(identity.getEmail());
            item.setAvatarUrl(identity.getAvatarUrl());
            item.setCreateTime(identity.getCreateTime());
            return item;
        }).collect(Collectors.toList()));
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> updateUserProfile(AdminUpdateUserProfileRequest request) {
        User user = userMapper.selectById(request.getUserId());
        if (user == null) throw new BusinessException(com.ai.gateway.common.ResultCode.USER_NOT_FOUND);

        String nickname = request.getNickname() == null ? null : request.getNickname().trim();
        if (nickname != null && nickname.isEmpty()) nickname = null;
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email != null && email.isEmpty()) email = null;
        String phone = request.getPhone() == null ? null : request.getPhone().trim();
        if (phone != null && phone.isEmpty()) phone = null;

        if (email != null && userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email).ne(User::getId, user.getId())) > 0) {
            throw new BusinessException(com.ai.gateway.common.ResultCode.PARAM_ERROR.getCode(), "该邮箱已绑定其他账户");
        }
        if (phone != null && userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone).ne(User::getId, user.getId())) > 0) {
            throw new BusinessException(com.ai.gateway.common.ResultCode.PARAM_ERROR.getCode(), "该手机号已绑定其他账户");
        }

        List<String> changedFields = new ArrayList<>();
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> update =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>().eq(User::getId, user.getId());
        if (!Objects.equals(user.getNickname(), nickname)) {
            update.set(User::getNickname, nickname);
            changedFields.add("nickname");
        }
        boolean emailChanged = !Objects.equals(user.getEmail(), email);
        if (emailChanged) {
            update.set(User::getEmail, email);
            changedFields.add("email");
        }
        boolean phoneChanged = !Objects.equals(user.getPhone(), phone);
        if (phoneChanged) {
            update.set(User::getPhone, phone);
            changedFields.add("phone");
        }
        if (changedFields.isEmpty()) return changedFields;
        update.set(User::getUpdateTime, LocalDateTime.now());
        userMapper.update(null, update);

        UserContactVerification verified = contactVerificationMapper.selectById(user.getId());
        if (verified != null && (emailChanged || phoneChanged)) {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserContactVerification> verificationUpdate =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserContactVerification>()
                            .eq(UserContactVerification::getUserId, user.getId());
            if (emailChanged) verificationUpdate.set(UserContactVerification::getEmail, null);
            if (phoneChanged) verificationUpdate.set(UserContactVerification::getPhone, null);
            verificationUpdate.set(UserContactVerification::getUpdatedAt, LocalDateTime.now());
            contactVerificationMapper.update(null, verificationUpdate);
        }
        return changedFields;
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
                item.put("points", new BigDecimal(row.get("points").toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
                item.put("tokens", ((Number) row.get("tokens")).longValue());
            } else {
                item.put("calls", 0L);
                item.put("points", BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                item.put("tokens", 0L);
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
            item.put("points", new BigDecimal(row.get("points").toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
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
        List<Map<String, Object>> result = callLogMapper.getTopUsersByPoints(10);
        List<Long> userIds = result.stream()
                .map(item -> item.get("userId"))
                .filter(Objects::nonNull)
                .map(value -> ((Number) value).longValue())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> usersById = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, List<UserOAuthIdentity>> identitiesByUser = userIds.isEmpty() ? Collections.emptyMap() :
                userOAuthIdentityMapper.selectByUserIds(userIds).stream()
                        .collect(Collectors.groupingBy(UserOAuthIdentity::getUserId));
        for (int i = 0; i < result.size(); i++) {
            Map<String, Object> item = result.get(i);
            Long userId = item.get("userId") == null ? null : ((Number) item.get("userId")).longValue();
            User user = userId == null ? null : usersById.get(userId);
            List<UserOAuthIdentity> identities = userId == null ? Collections.emptyList() :
                    identitiesByUser.getOrDefault(userId, Collections.emptyList());
            String displayName = user != null && user.getNickname() != null && !user.getNickname().isBlank()
                    ? user.getNickname()
                    : identities.stream().map(UserOAuthIdentity::getDisplayName)
                            .filter(value -> value != null && !value.isBlank()).findFirst()
                            .orElse((String) item.get("username"));
            String avatar = user == null ? null : user.getAvatarUrl();
            if (avatar == null || avatar.isBlank()) {
                avatar = identities.stream().map(UserOAuthIdentity::getAvatarUrl)
                        .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
            }
            item.put("displayName", displayName);
            item.put("avatarUrl", ossService.toAccessibleUrl(avatar));
            item.put("rank", i + 1);
        }
        return result;
    }

    public List<Map<String, Object>> getRevenueTrend(int months) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(months - 1).withDayOfMonth(1);

        // 从 platform_statistics 聚合每月的真实营收数据
        LambdaQueryWrapper<PlatformStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(PlatformStatistics::getStatDate, startDate);
        List<PlatformStatistics> statsList = platformStatisticsMapper.selectList(wrapper);

        Map<String, BigDecimal> revenueMap = new LinkedHashMap<>();
        for (PlatformStatistics stats : statsList) {
            String month = stats.getStatDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            BigDecimal revenue = stats.getTotalRevenue() != null ? stats.getTotalRevenue() : BigDecimal.ZERO;
            revenueMap.merge(month, revenue, BigDecimal::add);
        }

        // 对话成本使用点数统计，不能与人民币营收混用。
        Map<String, BigDecimal> pointsMap = new HashMap<>();
        for (Map<String, Object> row : callLogMapper.aggregateRevenueByMonth(startDate.atStartOfDay())) {
            String month = String.valueOf(row.get("month"));
            BigDecimal points = row.get("points") == null
                    ? BigDecimal.ZERO : new BigDecimal(row.get("points").toString());
            pointsMap.put(month, points);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (int m = months - 1; m >= 0; m--) {
            LocalDate monthDate = today.minusMonths(m);
            String monthKey = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            Map<String, Object> item = new HashMap<>();
            item.put("month", monthKey);
            item.put("revenue", revenueMap.getOrDefault(monthKey, BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_HALF_UP));
            item.put("points", pointsMap.getOrDefault(monthKey, BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_HALF_UP));
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
        Map<Long, BigDecimal> userPointsMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<Map<String, Object>> callStats = callLogMapper.getUserCallStats(userIds);
            for (Map<String, Object> row : callStats) {
                Long uid = ((Number) row.get("user_id")).longValue();
                userCallsMap.put(uid, ((Number) row.get("calls")).longValue());
                userPointsMap.put(uid, new BigDecimal(row.get("points").toString()));
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
            item.put("totalPointsConsumed", userPointsMap.getOrDefault(user.getId(), BigDecimal.ZERO));
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
        
        // 搜索平台昵称、登录名、邮箱、手机号、ID 与已绑定的第三方资料。
        if (search != null && !search.isEmpty()) {
            List<Long> oauthMatches = userOAuthIdentityMapper.searchUserIds(search);
            long parsedId = search.matches("\\d+") ? tryParseLong(search) : -1L;
            wrapper.and(w -> {
                w.like(User::getUsername, search)
                        .or().like(User::getNickname, search)
                        .or().like(User::getEmail, search)
                        .or().like(User::getPhone, search)
                        .or().eq(User::getId, parsedId);
                if (!oauthMatches.isEmpty()) w.or().in(User::getId, oauthMatches);
            });
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
        Map<Long, BigDecimal> userPointsMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<Map<String, Object>> callStats = callLogMapper.getUserCallStats(userIds);
            for (Map<String, Object> row : callStats) {
                Long uid = ((Number) row.get("user_id")).longValue();
                userCallsMap.put(uid, ((Number) row.get("calls")).longValue());
                userPointsMap.put(uid, new BigDecimal(row.get("points").toString()));
            }
        }

        List<Map<String, Object>> users = result.getRecords().stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("nickname", user.getNickname());
            item.put("phone", DataMaskUtil.maskPhone(user.getPhone()));
            item.put("email", DataMaskUtil.maskEmail(user.getEmail()));
            item.put("balance", user.getBalance());
            item.put("points", user.getPoints());
            item.put("role", user.getRole());
            item.put("status", user.getStatus() == 1 ? "ACTIVE" : "DISABLED");
            item.put("membership", user.getMembership() != null ? user.getMembership() : "free");
            item.put("dailyFreeCount", user.getDailyFreeCount());
            item.put("membershipExpireTime", user.getMembershipExpireTime());
            item.put("totalCalls", userCallsMap.getOrDefault(user.getId(), 0L));
            item.put("totalPointsConsumed", userPointsMap.getOrDefault(user.getId(), BigDecimal.ZERO));
            item.put("createTime", user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            return item;
        }).collect(Collectors.toList());

        if (!userIds.isEmpty()) {
            Map<Long, Integer> apiKeyCountsByUser = new HashMap<>();
            for (Map<String, Object> row : apiKeyMapper.countApiKeysByUserIds(userIds)) {
                apiKeyCountsByUser.put(((Number) row.get("user_id")).longValue(), ((Number) row.get("count")).intValue());
            }
            List<UserOAuthIdentity> identities = userOAuthIdentityMapper.selectByUserIds(userIds);
            Map<Long, List<UserOAuthIdentity>> identitiesByUser = identities.stream()
                    .collect(Collectors.groupingBy(UserOAuthIdentity::getUserId));
            Map<Long, User> usersById = result.getRecords().stream().collect(Collectors.toMap(User::getId, user -> user));
            for (Map<String, Object> item : users) {
                Long userId = ((Number) item.get("id")).longValue();
                item.put("apiKeyCount", apiKeyCountsByUser.getOrDefault(userId, 0));
                List<UserOAuthIdentity> accounts = identitiesByUser.getOrDefault(userId, Collections.emptyList());
                User user = usersById.get(userId);
                String avatar = user == null ? null : user.getAvatarUrl();
                if (avatar == null || avatar.isBlank()) {
                    avatar = accounts.stream().map(UserOAuthIdentity::getAvatarUrl)
                            .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
                }
                item.put("avatarUrl", ossService.toAccessibleUrl(avatar));
                item.put("displayName", user != null && user.getNickname() != null && !user.getNickname().isBlank()
                        ? user.getNickname()
                        : accounts.stream().map(UserOAuthIdentity::getDisplayName)
                                .filter(value -> value != null && !value.isBlank()).findFirst()
                                .orElse(user == null ? null : user.getUsername()));
                item.put("oauthProviders", accounts.stream().map(UserOAuthIdentity::getProvider).distinct().collect(Collectors.toList()));
            }
        }

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
        if (status == 0) userIds.forEach(userService::revokeUserSessions);
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
                    "管理员批量发放点数", null, 0);
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
                "管理员调整: " + reason, null, 0);
        
        log.info("调整用户点数: userId={}, 调整={}, 类型={}, 原因={}", userId, points, changeType, reason);
    }

    /**
     * 手动增减用户余额（仅超级管理员可用，由控制器保证权限）
     * @return 调整后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal adjustUserBalance(Long userId, BigDecimal delta, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        BigDecimal current = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal updated = current.add(delta).setScale(3, java.math.RoundingMode.HALF_UP);
        user.setBalance(updated);
        userMapper.updateById(user);

        log.info("调整用户余额: userId={}, 调整={}, 调整后={}, 原因={}", userId, delta, updated, reason);
        return updated;
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

    // ==================== 平台营收统计 ====================

    /**
     * 获取平台总营收统计数据
     * 从 platform_statistics 和 user_subscription 表聚合数据
     */
    public Map<String, Object> getPlatformRevenue() {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);

        // 1. 从 platform_statistics 聚合历史数据
        LambdaQueryWrapper<PlatformStatistics> allStatsWrapper = new LambdaQueryWrapper<>();
        List<PlatformStatistics> allStats = platformStatisticsMapper.selectList(allStatsWrapper);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalRecharge = BigDecimal.ZERO;
        int totalPackageSales = 0;

        for (PlatformStatistics stats : allStats) {
            if (stats.getTotalRevenue() != null) {
                totalRevenue = totalRevenue.add(stats.getTotalRevenue());
            }
            if (stats.getRechargeAmount() != null) {
                totalRecharge = totalRecharge.add(stats.getRechargeAmount());
            }
            if (stats.getPackageSales() != null) {
                totalPackageSales += stats.getPackageSales();
            }
        }

        // 2. 今日营收（从 platform_statistics 取今天的数据）
        LambdaQueryWrapper<PlatformStatistics> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.eq(PlatformStatistics::getStatDate, today);
        PlatformStatistics todayStats = platformStatisticsMapper.selectOne(todayWrapper);
        BigDecimal todayRevenue = todayStats != null && todayStats.getTotalRevenue() != null ? todayStats.getTotalRevenue() : BigDecimal.ZERO;
        BigDecimal todayRecharge = todayStats != null && todayStats.getRechargeAmount() != null ? todayStats.getRechargeAmount() : BigDecimal.ZERO;

        // 3. 本月营收（聚合本月所有日期）
        LambdaQueryWrapper<PlatformStatistics> monthWrapper = new LambdaQueryWrapper<>();
        monthWrapper.ge(PlatformStatistics::getStatDate, firstDayOfMonth)
                .le(PlatformStatistics::getStatDate, today);
        List<PlatformStatistics> monthStats = platformStatisticsMapper.selectList(monthWrapper);
        BigDecimal monthRevenue = BigDecimal.ZERO;
        int monthPackageSales = 0;
        for (PlatformStatistics stats : monthStats) {
            if (stats.getTotalRevenue() != null) {
                monthRevenue = monthRevenue.add(stats.getTotalRevenue());
            }
            if (stats.getPackageSales() != null) {
                monthPackageSales += stats.getPackageSales();
            }
        }

        // 4. 订阅销售统计（从 user_subscription 表）
        LambdaQueryWrapper<UserSubscription> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(UserSubscription::getStatus, "ACTIVE");
        Long activeSubscriptionCount = userSubscriptionMapper.selectCount(activeWrapper);

        LambdaQueryWrapper<UserSubscription> allSubsWrapper = new LambdaQueryWrapper<>();
        allSubsWrapper.eq(UserSubscription::getStatus, "ACTIVE")
                .or().eq(UserSubscription::getStatus, "EXPIRED")
                .or().eq(UserSubscription::getStatus, "CANCELLED");
        Long totalSubscriptionCount = userSubscriptionMapper.selectCount(allSubsWrapper);

        // 按套餐统计销量
        Map<String, Object> packageSalesMap = new HashMap<>();
        for (String code : Arrays.asList("free", "silver", "gold", "diamond")) {
            LambdaQueryWrapper<UserSubscription> pkgWrapper = new LambdaQueryWrapper<>();
            pkgWrapper.eq(UserSubscription::getPackageCode, code);
            long count = userSubscriptionMapper.selectCount(pkgWrapper);
            packageSalesMap.put(code, count);
        }

        // 5. 从 user_subscription 汇总实际支付金额
        LambdaQueryWrapper<UserSubscription> allPaidWrapper = new LambdaQueryWrapper<>();
        List<UserSubscription> allSubscriptions = userSubscriptionMapper.selectList(allPaidWrapper);
        BigDecimal subscriptionRevenue = BigDecimal.ZERO;
        for (UserSubscription sub : allSubscriptions) {
            if (sub.getPricePaid() != null) {
                subscriptionRevenue = subscriptionRevenue.add(sub.getPricePaid());
            }
        }

        result.put("totalRevenue", totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("todayRevenue", todayRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("monthRevenue", monthRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("totalRechargeAmount", totalRecharge.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("todayRechargeAmount", todayRecharge.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("totalPackageSales", totalPackageSales);
        result.put("monthPackageSales", monthPackageSales);
        result.put("subscriptionRevenue", subscriptionRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("activeSubscriptionCount", activeSubscriptionCount);
        result.put("totalSubscriptionCount", totalSubscriptionCount);
        result.put("packageSalesBreakdown", packageSalesMap);

        log.info("查询平台营收统计: totalRevenue={}, todayRevenue={}, monthRevenue={}",
                totalRevenue, todayRevenue, monthRevenue);
        return result;
    }
}
