package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.dto.*;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.AdminService;
import com.ai.gateway.service.DailyPointsService;
import com.ai.gateway.service.NotificationService;
import com.ai.gateway.service.SseConnectionManager;
import com.ai.gateway.service.StatisticsService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.SystemStatsVO;
import com.ai.gateway.vo.DashboardStatsVO;
import com.ai.gateway.vo.UserStatsVO;
import com.ai.gateway.vo.ConversationStatsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @RequireAdmin
    @GetMapping("/stats")
    public Result<SystemStatsVO> getStats(@RequestHeader("X-User-Token") String token) {
        return Result.success(adminService.getSystemStats());
    }

    @RequireAdmin
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
    @GetMapping("/user/{userId}")
    public Result<SystemStatsVO.UserStatVO> getUserDetail(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long userId) {
        var detail = adminService.getUserDetail(userId);
        return Result.success(detail);
    }

    @RequireAdmin
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

    @RequireAdmin
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
