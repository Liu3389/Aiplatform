package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.dto.*;
import com.ai.gateway.service.*;
import com.ai.gateway.vo.ConversationStatsVO;
import com.ai.gateway.vo.DashboardStatsVO;
import com.ai.gateway.vo.SystemStatsVO;
import com.ai.gateway.vo.UserStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag(name = "07-管理后台", description = "系统统计、用户管理、点数管理、会员管理、操作日志、通知管理、统计分析（全部需管理员权限）")
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AdminService adminService;
    private final AdminOperationLogService operationLogService;
    private final NotificationService notificationService;
    private final TokenService tokenService;
    private final StatisticsService statisticsService;
    private final DailyPointsService dailyPointsService;
    private final StringRedisTemplate redisTemplate;
    private final SseConnectionManager sseConnectionManager;
    private final FinanceService financeService;
    private final StatsConsistencyService statsConsistencyService;
    private final DockerControlService dockerControlService;

    @RequireAdmin
    @Operation(summary = "系统统计概览")
    @GetMapping("/stats")
    public Result<SystemStatsVO> getStats(@RequestHeader("X-User-Token") String token) {
        return Result.success(adminService.getSystemStats());
    }

    @RequireAdmin
    @Operation(summary = "系统组件实时状态", description = "实时检测后端/MySQL/Redis/Docker 组件健康状态与耗时")
    @GetMapping("/components/status")
    public Result<Map<String, Object>> getSystemStatus(@RequestHeader("X-User-Token") String token) {
        Map<String, Object> result = new HashMap<>();

        // 后端服务
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> backend = new HashMap<>();
        backend.put("status", "UP");
        backend.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        backend.put("usedMemoryMB", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        backend.put("maxMemoryMB", rt.maxMemory() / 1024 / 1024);
        result.put("backend", backend);

        // MySQL
        Map<String, Object> mysql = new HashMap<>();
        try {
            long s = System.currentTimeMillis();
            userService.getDailyUsage(1L, 1); // 触发一次真实 DB 查询
            mysql.put("status", "UP");
            mysql.put("latencyMs", System.currentTimeMillis() - s);
        } catch (Exception e) {
            mysql.put("status", "DOWN");
            mysql.put("error", String.valueOf(e.getMessage()));
        }
        result.put("mysql", mysql);

        // Redis
        Map<String, Object> redis = new HashMap<>();
        try {
            long s = System.currentTimeMillis();
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            redis.put("status", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN");
            redis.put("latencyMs", System.currentTimeMillis() - s);
        } catch (Exception e) {
            redis.put("status", "DOWN");
            redis.put("error", String.valueOf(e.getMessage()));
        }
        result.put("redis", redis);

        // Docker（AI 沙箱依赖）
        Map<String, Object> docker = new HashMap<>();
        try {
            long s = System.currentTimeMillis();
            boolean ok = dockerControlService.isAvailable();
            docker.put("status", ok ? "UP" : "DOWN");
            docker.put("latencyMs", System.currentTimeMillis() - s);
        } catch (Exception e) {
            docker.put("status", "DOWN");
            docker.put("error", String.valueOf(e.getMessage()));
        }
        result.put("docker", docker);

        result.put("timestamp", System.currentTimeMillis());
        return Result.success(result);
    }

    @RequireAdmin
    @Operation(summary = "用户列表（分页+筛选）")
    @GetMapping("/users")
    public Result<?> getUsers(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String membership,
            @RequestParam(required = false) String status) {
        Map<String, Object> result = adminService.getUserPage(page, size, search, membership, status);
        return Result.success(result);
    }

    @RequireAdmin
    @Operation(summary = "用户详情")
    @GetMapping("/user/{userId}")
    public Result<SystemStatsVO.UserStatVO> getUserDetail(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long userId) {
        var detail = adminService.getUserDetail(userId);
        return Result.success(detail);
    }

    @RequireAdmin
    @Operation(summary = "启用/禁用用户")
    @PostMapping("/user/status")
    public Result<Void> toggleUserStatus(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminToggleUserStatusRequest request) {
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        
        try {
            Long userId = request.getUserId();
            String status = request.getStatus();
            
            userService.toggleUserStatus(userId, status);
            
            Map<String, Object> detail = new HashMap<>();
            detail.put("userId", userId);
            detail.put("status", status);
            operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE", 
                    "TOGGLE_STATUS", java.util.Arrays.asList(userId), detail);
            
            return Result.success("操作成功", null);
        } catch (Exception e) {
            log.error("切换用户状态失败: {}", e.getMessage());
            
            Long userId = request.getUserId();
            Map<String, Object> detail = new HashMap<>();
            detail.put("userId", userId);
            operationLogService.logFailure(adminId, adminUsername, "USER_MANAGE", 
                    "TOGGLE_STATUS", userId != null ? java.util.Arrays.asList(userId) : null, detail, e.getMessage());
            
            return Result.error(500, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 给用户充值点数（修复 P2-14: 路径改为 /points/recharge 以匹配功能）
     */
    @RequireAdmin
    @Operation(summary = "给用户充值点数")
    @PostMapping("/points/recharge")
    public Result<Void> rechargeUserPoints(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminRechargeRequest request) {
        
        try {
            Long userId = request.getUserId();
            BigDecimal points = request.getPoints();
            
            adminService.adjustUserPoints(userId, points, "管理员手动充值");
            
            Long adminId = getUserIdFromToken(token);
            String adminUsername = getUsernameFromToken(token);
            operationLogService.logSuccess(adminId, adminUsername, "POINTS_MANAGE", 
                    "RECHARGE_POINTS", java.util.Arrays.asList(userId), 
                    java.util.Map.of("points", points));
            
            return Result.success("点数充值成功", null);
        } catch (Exception e) {
            log.error("给用户充值点数失败: {}", e.getMessage());
            
            try {
                Long adminId = getUserIdFromToken(token);
                String adminUsername = getUsernameFromToken(token);
                operationLogService.logFailure(adminId, adminUsername, "POINTS_MANAGE", 
                        "RECHARGE_POINTS", java.util.Arrays.asList(request.getUserId()), 
                        java.util.Map.of("points", request.getPoints()), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error(500, "充值失败: " + e.getMessage());
        }
    }

    @RequireAdmin
    @Operation(summary = "分配管理员角色", description = "仅超级管理员可操作")
    @PostMapping("/assign-role")
    public Result<Void> assignAdminRole(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminAssignRoleRequest request) {
        
        Long currentAdminId = getUserIdFromToken(token);
        com.ai.gateway.entity.User currentUser = userService.getUserEntityById(currentAdminId);
        if (currentUser == null || !"SUPER_ADMIN".equals(currentUser.getRole())) {
            return Result.error(403, "只有超级管理员可以分配管理员角色");
        }
        
        userService.assignRole(request.getUserId(), request.getRole());
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE", 
                "ASSIGN_ROLE", java.util.Arrays.asList(request.getUserId()), 
                java.util.Map.of("role", request.getRole()));
        return Result.success("角色分配成功", null);
    }

    @RequireAdmin
    @Operation(summary = "撤销管理员角色", description = "不能撤销自己的角色")
    @PostMapping("/revoke-role")
    public Result<Void> revokeAdminRole(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminRevokeRoleRequest request) {
        
        Long currentAdminId = getUserIdFromToken(token);
        if (request.getUserId().equals(currentAdminId)) {
            return Result.error(403, "不能撤销自己的管理员角色");
        }
        
        userService.assignRole(request.getUserId(), "USER");
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE", 
                "REVOKE_ROLE", java.util.Arrays.asList(request.getUserId()), null);
        return Result.success("角色撤销成功", null);
    }

    @RequireAdmin
    @Operation(summary = "重置用户密码", description = "密码需同时含字母和数字")
    @PostMapping("/reset-password")
    public Result<Void> resetUserPassword(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        
        String password = request.getPassword();
        if (!password.matches(".*[a-zA-Z].*") || !password.matches(".*\\d.*")) {
            return Result.error(400, "密码必须同时包含字母和数字");
        }
        
        userService.resetPassword(request.getUserId(), password);
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE", 
                "RESET_PASSWORD", java.util.Arrays.asList(request.getUserId()), null);
        return Result.success("密码重置成功", null);
    }

    @RequireAdmin
    @Operation(summary = "完整分析数据", description = "含每日用量/模型用量/时段流量/头部用户/收入趋势/用户增长，Redis 缓存5分钟")
    @GetMapping("/analytics/full")
    public Result<SystemStatsVO> getFullAnalytics(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "6") int months) {
        
        // 优化 P2-1: 增加 Redis 缓存，TTL 5分钟
        String cacheKey = "admin:analytics:full:" + days + ":" + months;
        SystemStatsVO cachedStats = getCachedStats(cacheKey);
        if (cachedStats != null) {
            log.info("命中仪表盘统计缓存: {}", cacheKey);
            return Result.success(cachedStats);
        }

        log.info("未命中缓存，开始执行数据库聚合查询...");
        SystemStatsVO stats = adminService.getSystemStats();
        stats.setDailyUsage(adminService.getDailyUsage(days));
        stats.setModelUsage(adminService.getModelUsage());
        stats.setHourlyTraffic(adminService.getHourlyTraffic());
        stats.setTopUsers(adminService.getTopUsers());
        stats.setRevenueTrend(adminService.getRevenueTrend(months));
        stats.setUserGrowth(adminService.getUserGrowth(months));
        
        // 存入缓存
        cacheStats(cacheKey, stats, 5, TimeUnit.MINUTES);
        
        return Result.success(stats);
    }

    // ==================== 用户管理增强接口 ====================

    /**
     * 分页查询用户（支持多条件筛选）
     */
    @RequireAdmin
    @Operation(summary = "高级用户查询", description = "多条件筛选：余额范围、注册日期等")
    @GetMapping("/users/advanced")
    public Result<?> getUsersAdvanced(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String membership,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) BigDecimal maxBalance,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        Map<String, Object> result = adminService.getUserPageAdvanced(
            page, size, search, membership, status, minBalance, maxBalance, startDate, endDate);
        
        return Result.success(result);
    }

    /**
     * 获取仪表盘统计数据（核心）
     * @param period 时间范围：7d/30d
     */
    @RequireAdmin
    @Operation(summary = "仪表盘统计", description = "period=7d/30d")
    @GetMapping("/statistics/dashboard")
    public Result<DashboardStatsVO> getDashboardStats(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "7d") String period) {
        DashboardStatsVO stats = statisticsService.getDashboardStats(period);
        return Result.success(stats);
    }

    /**
     * 获取用户统计数据（核心）
     */
    @RequireAdmin
    @Operation(summary = "用户统计数据")
    @GetMapping("/statistics/users")
    public Result<UserStatsVO> getUserStats(
            @RequestHeader("X-User-Token") String token) {
        UserStatsVO stats = statisticsService.getUserStats();
        return Result.success(stats);
    }

    /**
     * 获取对话统计数据（核心）
     */
    @RequireAdmin
    @Operation(summary = "对话统计数据")
    @GetMapping("/statistics/conversations")
    public Result<ConversationStatsVO> getConversationStats(
            @RequestHeader("X-User-Token") String token) {
        ConversationStatsVO stats = statisticsService.getConversationStats();
        return Result.success(stats);
    }

    // ==================== 批量操作接口 ====================

    /**
     * 批量封禁/解封用户
     */
    @RequireAdmin
    @Operation(summary = "批量封禁/解封用户", description = "status=0封禁，1解封")
    @PostMapping("/batch/update-status")
    public Result<Map<String, Object>> batchUpdateStatus(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody BatchUpdateStatusRequest request) {
        
        java.util.List<Long> userIds = request.getUserIds();
        Integer status = request.getStatus();
        
        if (status != 0 && status != 1) {
            return Result.error(400, "状态值只能为0(封禁)或1(解封)");
        }
        
        int successCount = adminService.batchUpdateStatus(userIds, status);
        
        if (successCount > 0) {
            Long adminId = getUserIdFromToken(token);
            String adminUsername = getUsernameFromToken(token);
            operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE", 
                    "BATCH_UPDATE_STATUS", userIds, 
                    java.util.Map.of("status", status));
        }
        
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("successCount", successCount);
        data.put("totalCount", userIds.size());
        data.put("status", status);
        
        return Result.success("批量更新成功", data);
    }

    /**
     * 批量发放点数
     */
    @RequireAdmin
    @Operation(summary = "批量发放点数", description = "单批上限1000人")
    @PostMapping("/batch/grant-points")
    public Result<Map<String, Object>> batchGrantPoints(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody BatchGrantPointsRequest request) {
        
        java.util.List<Long> userIds = request.getUserIds();
        BigDecimal points = request.getPoints();
        
        if (userIds == null || userIds.isEmpty()) {
            return Result.error(400, "用户ID列表不能为空");
        }
        
        // 修复 P1-1: 验证批量操作数量上限
        if (userIds.size() > 1000) {
            return Result.error(400, "批量操作用户数不能超过 1000 个");
        }
        
        int successCount = adminService.batchGrantPoints(userIds, points);
        
        // 修复 SEC-1: 检查是否有实际成功的操作再记录日志
        if (successCount > 0) {
            Long adminId = getUserIdFromToken(token);
            String adminUsername = getUsernameFromToken(token);
            operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE", 
                    "BATCH_GRANT_POINTS", userIds, 
                    java.util.Map.of("points", points));
        }
        
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("successCount", successCount);
        data.put("totalCount", userIds.size());
        data.put("points", points);
        
        return Result.success("批量发放成功", data);
    }

    // ==================== 点数管理接口 ====================

    /**
     * 手动触发每日点数发放（用于测试或补发）
     */
    @RequireAdmin
    @Operation(summary = "手动触发每日点数发放", description = "可指定 userId，不传则全员发放，30秒分布式锁防重")
    @PostMapping("/points/distribute-daily")
    public Result<Map<String, Object>> distributeDailyPoints(
            @RequestHeader("X-User-Token") String token,
            @RequestBody(required = false) Map<String, Object> body) {
        
        Long userId = null;
        if (body != null && body.containsKey("userId")) {
            userId = Long.valueOf(body.get("userId").toString());
        }
        
        String lockKey = "admin:daily_points:lock:" + (userId != null ? userId : "all");
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (acquired == null || !acquired) {
            return Result.error(429, "点数发放正在进行中，请勿重复操作");
        }
        
        try {
            dailyPointsService.manualDistributePoints(userId);
            
            // 记录操作日志
            Long adminId = getUserIdFromToken(token);
            String adminUsername = getUsernameFromToken(token);
            operationLogService.logSuccess(adminId, adminUsername, "POINTS_MANAGE", 
                    "DISTRIBUTE_DAILY_POINTS", userId != null ? java.util.Arrays.asList(userId) : null, 
                    java.util.Map.of("targetUserId", userId != null ? userId : "ALL"));
            
            Map<String, Object> data = new java.util.HashMap<>();
            if (userId != null) {
                data.put("message", "已给用户 " + userId + " 发放30点");
            } else {
                data.put("message", "已给所有用户发放30点");
            }
            
            return Result.success("点数发放成功", data);
        } catch (Exception e) {
            log.error("手动触发每日点数发放失败", e);
            
            // 修复 P1-8: 记录失败日志
            try {
                Long adminId = getUserIdFromToken(token);
                String adminUsername = getUsernameFromToken(token);
                operationLogService.logFailure(adminId, adminUsername, "POINTS_MANAGE", 
                        "DISTRIBUTE_DAILY_POINTS", userId != null ? java.util.Arrays.asList(userId) : null, 
                        java.util.Map.of("targetUserId", userId != null ? userId : "ALL"), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error("点数发放失败: " + e.getMessage());
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 手动增减用户点数
     */
    @RequireAdmin
    @Operation(summary = "手动增减用户点数", description = "可指定原因")
    @PostMapping("/points/adjust")
    public Result<Void> adjustPoints(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminAdjustPointsRequest request) {
        
        String reason = request.getReason() != null ? request.getReason() : "管理员手动调整";
        
        try {
            adminService.adjustUserPoints(request.getUserId(), request.getPoints(), reason);
            
            Long adminId = getUserIdFromToken(token);
            String adminUsername = getUsernameFromToken(token);
            operationLogService.logSuccess(adminId, adminUsername, "POINTS_MANAGE", 
                    "ADJUST_POINTS", java.util.Arrays.asList(request.getUserId()), 
                    java.util.Map.of("points", request.getPoints(), "reason", reason));
            
            return Result.success("点数调整成功", null);
        } catch (Exception e) {
            log.error("调整用户点数失败", e);
            
            try {
                Long adminId = getUserIdFromToken(token);
                String adminUsername = getUsernameFromToken(token);
                operationLogService.logFailure(adminId, adminUsername, "POINTS_MANAGE", 
                        "ADJUST_POINTS", java.util.Arrays.asList(request.getUserId()), 
                        java.util.Map.of("points", request.getPoints(), "reason", reason), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error(500, "点数调整失败: " + e.getMessage());
        }
    }

    /**
     * 手动增减用户余额（仅超级管理员可操作）
     */
    @RequireAdmin
    @Operation(summary = "手动增减用户余额", description = "仅超级管理员可操作；正数为增加，负数为扣减")
    @PostMapping("/balance/adjust")
    public Result<Void> adjustUserBalance(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminAdjustBalanceRequest request) {

        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);

        // 权限校验：仅超级管理员可增减余额
        com.ai.gateway.entity.User currentUser = userService.getUserEntityById(adminId);
        if (currentUser == null || !"SUPER_ADMIN".equals(currentUser.getRole())) {
            return Result.error(403, "只有超级管理员可以增减用户余额");
        }

        String reason = request.getReason() != null ? request.getReason() : "管理员手动调整余额";

        try {
            BigDecimal updated = adminService.adjustUserBalance(request.getUserId(), request.getBalance(), reason);

            operationLogService.logSuccess(adminId, adminUsername, "BALANCE_MANAGE",
                    "ADJUST_BALANCE", java.util.Arrays.asList(request.getUserId()),
                    java.util.Map.of("balance", request.getBalance(), "reason", reason, "newBalance", updated));

            return Result.success("余额调整成功", null);
        } catch (Exception e) {
            log.error("调整用户余额失败: {}", e.getMessage());
            operationLogService.logFailure(adminId, adminUsername, "BALANCE_MANAGE",
                    "ADJUST_BALANCE", java.util.Arrays.asList(request.getUserId()),
                    java.util.Map.of("balance", request.getBalance(), "reason", reason), e.getMessage());
            return Result.error(500, "余额调整失败: " + e.getMessage());
        }
    }

    @RequireAdmin
    @Operation(summary = "设置用户每日免费次数")
    @PostMapping("/points/set-free-count")
    public Result<Void> setDailyFreeCount(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminSetFreeCountRequest request) {
        
        adminService.setDailyFreeCount(request.getUserId(), request.getCount());
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "POINTS_MANAGE", 
                "SET_FREE_COUNT", java.util.Arrays.asList(request.getUserId()), 
                java.util.Map.of("count", request.getCount()));
        
        return Result.success("设置成功", null);
    }

    @RequireAdmin
    @Operation(summary = "更新用户会员等级")
    @PostMapping("/membership/update")
    public Result<Void> updateMembership(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminUpdateMembershipRequest request) {
        
        adminService.updateMembership(request.getUserId(), request.getMembership());
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "MEMBERSHIP_MANAGE", 
                "UPDATE_MEMBERSHIP", java.util.Arrays.asList(request.getUserId()), 
                java.util.Map.of("membership", request.getMembership()));
        
        return Result.success("会员等级调整成功", null);
    }

    @RequireAdmin
    @Operation(summary = "设置会员过期时间", description = "ISO 8601 格式，不能超过10年")
    @PostMapping("/membership/set-expire-time")
    public Result<Void> setMembershipExpireTime(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminSetMembershipExpireRequest request) {
        
        java.time.LocalDateTime expireTime;
        try {
            expireTime = java.time.LocalDateTime.parse(request.getExpireTime());
        } catch (Exception e) {
            try {
                expireTime = java.time.ZonedDateTime.parse(request.getExpireTime()).toLocalDateTime();
            } catch (Exception ex) {
                return Result.error(400, "日期格式错误，请使用 yyyy-MM-ddTHH:mm:ss 或 ISO 8601 格式");
            }
        }
        
        if (expireTime.isBefore(java.time.LocalDateTime.now())) {
            return Result.error(400, "会员过期时间不能设置为过去的时间");
        }
        
        java.time.LocalDateTime maxExpireTime = java.time.LocalDateTime.now().plusYears(10);
        if (expireTime.isAfter(maxExpireTime)) {
            return Result.error(400, "会员过期时间不能超过10年");
        }
        
        adminService.setMembershipExpireTime(request.getUserId(), expireTime);
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "MEMBERSHIP_MANAGE", 
                "SET_EXPIRE_TIME", java.util.Arrays.asList(request.getUserId()), 
                java.util.Map.of("expireTime", request.getExpireTime()));
        
        return Result.success("会员有效期设置成功", null);
    }

    @RequireAdmin
    @Operation(summary = "批量开通会员", description = "指定 membership 与天数")
    @PostMapping("/batch/activate-membership")
    public Result<Map<String, Object>> batchActivateMembership(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody BatchActivateMembershipRequest request) {
        
        java.util.List<Long> userIds = request.getUserIds();
        String membership = request.getMembership();
        Integer days = request.getDays();
        
        int successCount = adminService.batchActivateMembership(userIds, membership, days);
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE", 
                "BATCH_ACTIVATE_MEMBERSHIP", userIds, 
                Map.of("membership", membership, "days", days));
        
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("successCount", successCount);
        data.put("totalCount", userIds.size());
        data.put("membership", membership);
        data.put("days", days);
        
        return Result.success("批量开通成功", data);
    }

    // ==================== 操作日志接口 ====================

    /**
     * 查询操作日志
     */
    @RequireAdmin
    @Operation(summary = "查询操作日志", description = "分页，可按管理员/操作类型/日期筛选，时间范围上限90天")
    @GetMapping("/logs")
    public Result<?> getOperationLogs(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        size = Math.min(size, 200);
        
        // 修复 P1-7: 限制最大查询时间范围为90天
        if (startDate != null && endDate != null) {
            try {
                java.time.LocalDate start = java.time.LocalDate.parse(startDate);
                java.time.LocalDate end = java.time.LocalDate.parse(endDate);
                if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 90) {
                    return Result.error(400, "操作日志查询时间范围不能超过90天");
                }
            } catch (Exception e) {
                return Result.error(400, "日期格式错误");
            }
        }
        
        Map<String, Object> logs = operationLogService.getOperationLogs(
            page, size, adminId, operationType, startDate, endDate);
        
        return Result.success(logs);
    }

    // ==================== 通知管理接口 ====================

    /**
     * 获取未读通知数量
     */
    @RequireAdmin
    @Operation(summary = "管理员未读通知数量")
    @GetMapping("/notifications/unread-count")
    public Result<Map<String, Integer>> getUnreadCount(
            @RequestHeader("X-User-Token") String token) {
        
        // 修复 P2-8: 移除冗余的 userId == null 检查，@RequireAdmin 已保证有效性
        Long userId = getUserIdFromToken(token);
        int count = notificationService.getUnreadCount(userId);
        
        Map<String, Integer> data = new java.util.HashMap<>();
        data.put("unreadCount", count);
        
        return Result.success(data);
    }

    /**
     * 获取通知列表
     */
    @RequireAdmin
    @Operation(summary = "管理员通知列表", description = "分页，可按 isRead 筛选")
    @GetMapping("/notifications")
    public Result<?> getNotifications(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer isRead) {
        
        Long userId = getUserIdFromToken(token);
        Map<String, Object> notifications = notificationService.getNotifications(
            userId, page, size, isRead);
        
        return Result.success(notifications);
    }

    /**
     * 标记通知为已读
     */
    @RequireAdmin
    @Operation(summary = "标记通知为已读")
    @PostMapping("/notifications/{id}/read")
    public Result<Void> markAsRead(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        
        Long userId = getUserIdFromToken(token);
        notificationService.markAsRead(id, userId);
        return Result.success("标记成功", null);
    }

    /**
     * 标记所有通知为已读
     */
    @RequireAdmin
    @Operation(summary = "标记所有通知为已读")
    @PostMapping("/notifications/read-all")
    public Result<Void> markAllAsRead(
            @RequestHeader("X-User-Token") String token) {
        
        Long userId = getUserIdFromToken(token);
        notificationService.markAllAsRead(userId);
        return Result.success("全部标记成功", null);
    }

    /**
     * 删除通知
     */
    @RequireAdmin
    @Operation(summary = "删除通知")
    @DeleteMapping("/notifications/{id}")
    public Result<Void> deleteNotification(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        
        Long userId = getUserIdFromToken(token);
        boolean deleted = notificationService.deleteNotification(id, userId);
        if (!deleted) {
            return Result.success("通知不存在或已删除", null);
        }
        return Result.success("删除成功", null);
    }

    /**
     * 获取最新未读通知（用于登录时弹窗）
     */
    @RequireAdmin
    @Operation(summary = "最新未读通知", description = "登录时弹窗用，limit 默认5")
    @GetMapping("/notifications/latest")
    public Result<?> getLatestNotifications(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "5") int limit) {
        
        Long userId = getUserIdFromToken(token);
        java.util.List<com.ai.gateway.entity.UserNotification> notifications = notificationService.getLatestNotifications(userId, limit);
        return Result.success(notifications);
    }

    // ==================== 统计分析接口 ====================

    /**
     * 按日查询统计数据
     */
    @RequireAdmin
    @Operation(summary = "按日查询统计数据", description = "startDate、endDate 必传")
    @GetMapping("/statistics/daily")
    public Result<?> getDailyStats(
            @RequestHeader("X-User-Token") String token,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        
        java.time.LocalDate start = java.time.LocalDate.parse(startDate);
        java.time.LocalDate end = java.time.LocalDate.parse(endDate);
        
        java.util.List<com.ai.gateway.entity.DailyStatistics> stats = statisticsService.getDailyStats(start, end);
        
        return Result.success(stats);
    }

    /**
     * 按周查询统计数据
     */
    @RequireAdmin
    @Operation(summary = "按周查询统计数据", description = "weeks 默认4")
    @GetMapping("/statistics/weekly")
    public Result<?> getWeeklyStats(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "4") int weeks) {
        
        java.util.List<java.util.Map<String, Object>> stats = statisticsService.getWeeklyStats(weeks);
        return Result.success(stats);
    }

    /**
     * 按月查询统计数据
     */
    @RequireAdmin
    @Operation(summary = "按月查询统计数据", description = "months 默认6")
    @GetMapping("/statistics/monthly")
    public Result<?> getMonthlyStats(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "6") int months) {
        
        java.util.List<java.util.Map<String, Object>> stats = statisticsService.getMonthlyStats(months);
        return Result.success(stats);
    }

    /**
     * 导出统计报表（CSV格式）
     */
    @RequireAdmin
    @Operation(summary = "导出统计报表(CSV)", description = "type=user/revenue/business，时间范围上限1年，返回 CSV 文件流")
    @GetMapping("/statistics/export")
    public void exportStats(
            @RequestHeader("X-User-Token") String token,
            @RequestParam String type,
            @RequestParam String startDate,
            @RequestParam String endDate,
            jakarta.servlet.http.HttpServletResponse response) {
        
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        
        java.time.LocalDate start;
        java.time.LocalDate end;
        try {
            start = java.time.LocalDate.parse(startDate);
            end = java.time.LocalDate.parse(endDate);
            if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 365) {
                response.setStatus(400);
                response.getWriter().write("{\"code\":400,\"message\":\"导出时间范围不能超过 1 年\"}");
                return;
            }
        } catch (Exception e) {
            log.error("日期解析失败", e);
            try {
                response.setStatus(400);
                response.getWriter().write("{\"code\":400,\"message\":\"日期格式错误\"}");
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
            return;
        }

        if (!"user".equals(type) && !"revenue".equals(type) && !"business".equals(type)) {
            try {
                response.setStatus(400);
                response.getWriter().write("{\"code\":400,\"message\":\"无效的报表类型\"}");
            } catch (Exception e) {
                log.error("写入错误响应失败", e);
            }
            return;
        }
        
        try {
            String csvData = statisticsService.exportStatsToCsv(start, end, type);
            
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", 
                "attachment; filename=" + type + "_stats_" + startDate + "_" + endDate + ".csv");
            response.getOutputStream().write(csvData.getBytes("UTF-8"));
            response.getOutputStream().flush();
            
            operationLogService.logSuccess(adminId, adminUsername, "STATISTICS_MANAGE", 
                    "EXPORT_STATS", null, java.util.Map.of("type", type, "startDate", startDate, "endDate", endDate));
            
        } catch (Exception e) {
            log.error("导出统计报表失败", e);
            try {
                response.setStatus(500);
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
            
            try {
                operationLogService.logFailure(adminId, adminUsername, "STATISTICS_MANAGE", 
                        "EXPORT_STATS", null, java.util.Map.of("type", type, "startDate", startDate, "endDate", endDate), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
        }
    }

    /**
     * 获取用户增长趋势数据
     */
    @RequireAdmin
    @Operation(summary = "用户增长趋势", description = "months 默认6")
    @GetMapping("/statistics/user-growth")
    public Result<?> getUserGrowthStats(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "6") int months) {

        java.util.List<java.util.Map<String, Object>> stats = adminService.getUserGrowth(months);
        return Result.success(stats);
    }

    /**
     * 获取收入趋势数据
     */
    @RequireAdmin
    @Operation(summary = "收入趋势", description = "months 默认6")
    @GetMapping("/statistics/revenue")
    public Result<?> getRevenueStats(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "6") int months) {

        java.util.List<java.util.Map<String, Object>> stats = adminService.getRevenueTrend(months);
        return Result.success(stats);
    }

    /**
     * 获取平台总营收统计数据
     */
    @RequireAdmin
    @Operation(summary = "平台总营收统计")
    @GetMapping("/platform/revenue")
    public Result<Map<String, Object>> getPlatformRevenue(
            @RequestHeader("X-User-Token") String token) {

        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效");
        }

        Map<String, Object> revenue = adminService.getPlatformRevenue();
        return Result.success(revenue);
    }

    // ==================== 财务管理接口 ====================

    @RequireAdmin
    @Operation(summary = "充值记录分页", description = "来源 billing_record type=2，支持时间筛选")
    @GetMapping("/finance/recharge/list")
    public Result<Map<String, Object>> financeRechargeList(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(financeService.rechargeList(page, size, startDate, endDate));
    }

    @RequireAdmin
    @Operation(summary = "消费明细分页", description = "来源 billing_record type=1，支持时间筛选")
    @GetMapping("/finance/consumption/list")
    public Result<Map<String, Object>> financeConsumptionList(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(financeService.consumptionList(page, size, startDate, endDate));
    }

    @RequireAdmin
    @Operation(summary = "订单管理分页", description = "来源 user_subscription，支持状态与时间筛选")
    @GetMapping("/finance/orders/list")
    public Result<Map<String, Object>> financeOrdersList(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(financeService.ordersList(page, size, status, startDate, endDate));
    }

    /**
     * 封禁/解封用户
     */
    @RequireAdmin
    @Operation(summary = "封禁/解封用户", description = "切换用户状态，可在 body 传 reason")
    @PutMapping("/user/{userId}/ban")
    public Result<?> banUser(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long userId,
            @RequestBody(required = false) java.util.Map<String, String> body) {

        String reason = body != null ? body.get("reason") : "管理员操作";

        // 获取用户当前状态
        com.ai.gateway.entity.User user = userService.getUserEntityById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        // 切换状态：1->0(封禁), 0->1(解封)
        int newStatus = user.getStatus() == 1 ? 0 : 1;
        String action = newStatus == 0 ? "BAN" : "UNBAN";
        String message = newStatus == 0 ? "用户已封禁" : "用户已解封";

        // 使用 adminService 更新状态
        adminService.batchUpdateStatus(java.util.Arrays.asList(userId), newStatus);

        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        operationLogService.logSuccess(adminId, adminUsername, "USER_MANAGE",
                action, java.util.Arrays.asList(userId), java.util.Map.of("reason", reason != null ? reason : "N/A"));

        return Result.success(message);
    }

    // ==================== 统计对账验证 ====================

    @RequireAdmin
    @Operation(summary = "统计数据对账验证", description = "比较缓存/聚合表与原始事实表的数据一致性，偏差>1% WARN，>5% ERROR")
    @GetMapping("/stats/consistency-check")
    public Result<Map<String, Object>> statsConsistencyCheck(@RequestHeader("X-User-Token") String token) {
        log.info("管理员触发统计对账验证");
        return Result.success(statsConsistencyService.consistencyCheck());
    }

    @RequireAdmin
    @Operation(summary = "手动重算统计缓存", description = "从原始表实时聚合数据，重建 platform_statistics 并清除所有缓存")
    @PostMapping("/stats/recalculate")
    public Result<Map<String, Object>> statsRecalculate(@RequestHeader("X-User-Token") String token) {
        Long adminId = getUserIdFromToken(token);
        String adminUsername = getUsernameFromToken(token);
        Map<String, Object> result = statsConsistencyService.recalculate();
        operationLogService.logSuccess(adminId, adminUsername, "SYSTEM_MANAGE",
                "STATS_RECALCULATE", null, result);
        log.info("管理员 {} 手动重算统计缓存完成", adminUsername);
        return Result.success("重算完成", result);
    }

    // ==================== 辅助方法 ====================

    private Long getUserIdFromToken(String token) {
        return tokenService.getUserIdFromToken(token);
    }

    private String getUsernameFromToken(String token) {
        try {
            Long userId = tokenService.getUserIdFromToken(token);
            if (userId != null) {
                com.ai.gateway.entity.User user = userService.getUserEntityById(userId);
                return user != null ? user.getUsername() : "Unknown_Admin";
            }
        } catch (Exception e) {
            log.warn("从token获取用户名失败: {}", e.getMessage());
        }
        return "Unknown_Admin";
    }

    // ==================== 缓存辅助方法 ====================

    private SystemStatsVO getCachedStats(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, SystemStatsVO.class);
            }
        } catch (Exception e) {
            log.warn("读取统计缓存失败: {}", e.getMessage());
        }
        return null;
    }

    private void cacheStats(String key, SystemStatsVO stats, long timeout, TimeUnit unit) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(stats);
            redisTemplate.opsForValue().set(key, json, timeout, unit);
        } catch (Exception e) {
            log.error("写入统计缓存失败: {}", e.getMessage());
        }
    }
}
