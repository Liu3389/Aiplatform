package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.AdminService;
import com.ai.gateway.service.NotificationService;
import com.ai.gateway.service.StatisticsService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.SystemStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

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
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String status = (String) body.get("status");
        
        if (userId == null || status == null) {
            return Result.error(400, "参数不完整");
        }
        
        userService.toggleUserStatus(userId, status);
        return Result.success("操作成功", null);
    }

    @RequireAdmin
    @PostMapping("/user/recharge")
    public Result<Void> rechargeUser(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        
        if (userId == null || amount == null) {
            return Result.error(400, "参数不完整");
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error(400, "充值金额必须大于0");
        }
        
        userService.recharge(userId, amount);
        return Result.success("充值成功", null);
    }

    @RequireAdmin
    @PostMapping("/assign-role")
    public Result<Void> assignAdminRole(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String role = (String) body.get("role");
        userService.assignRole(userId, role);
        return Result.success("角色分配成功", null);
    }

    @RequireAdmin
    @PostMapping("/revoke-role")
    public Result<Void> revokeAdminRole(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        userService.assignRole(userId, "USER");
        return Result.success("角色撤销成功", null);
    }

    @RequireAdmin
    @PostMapping("/reset-password")
    public Result<Void> resetUserPassword(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String password = (String) body.get("password");
        userService.resetPassword(userId, password);
        return Result.success("密码重置成功", null);
    }

    @RequireAdmin
    @GetMapping("/analytics/full")
    public Result<SystemStatsVO> getFullAnalytics(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "6") int months) {
        SystemStatsVO stats = adminService.getSystemStats();
        stats.setDailyUsage(adminService.getDailyUsage(days));
        stats.setModelUsage(adminService.getModelUsage());
        stats.setHourlyTraffic(adminService.getHourlyTraffic());
        stats.setTopUsers(adminService.getTopUsers());
        stats.setRevenueTrend(adminService.getRevenueTrend(months));
        stats.setUserGrowth(adminService.getUserGrowth(months));
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

    // ==================== 批量操作接口 ====================

    /**
     * 批量封禁/解封用户
     */
    @RequireAdmin
    @PostMapping("/batch/update-status")
    public Result<Map<String, Object>> batchUpdateStatus(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        @SuppressWarnings("unchecked")
        java.util.List<Long> userIds = (java.util.List<Long>) body.get("userIds");
        Integer status = (Integer) body.get("status");
        
        if (userIds == null || userIds.isEmpty()) {
            return Result.error(400, "用户ID列表不能为空");
        }
        
        int successCount = adminService.batchUpdateStatus(userIds, status);
        
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("successCount", successCount);
        data.put("totalCount", userIds.size());
        
        return Result.success("批量更新成功", data);
    }

    /**
     * 批量发放点数
     */
    @RequireAdmin
    @PostMapping("/batch/grant-points")
    public Result<Map<String, Object>> batchGrantPoints(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        @SuppressWarnings("unchecked")
        java.util.List<Long> userIds = (java.util.List<Long>) body.get("userIds");
        BigDecimal points = new BigDecimal(body.get("points").toString());
        
        if (userIds == null || userIds.isEmpty()) {
            return Result.error(400, "用户ID列表不能为空");
        }
        
        int successCount = adminService.batchGrantPoints(userIds, points);
        
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("successCount", successCount);
        data.put("totalCount", userIds.size());
        data.put("points", points);
        
        return Result.success("批量发放成功", data);
    }

    // ==================== 点数管理接口 ====================

    /**
     * 手动增减用户点数
     */
    @RequireAdmin
    @PostMapping("/points/adjust")
    public Result<Void> adjustPoints(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        Long userId = Long.valueOf(body.get("userId").toString());
        BigDecimal points = new BigDecimal(body.get("points").toString());
        String reason = (String) body.getOrDefault("reason", "管理员手动调整");
        
        adminService.adjustUserPoints(userId, points, reason);
        
        return Result.success("点数调整成功", null);
    }

    /**
     * 设置用户每日免费次数
     */
    @RequireAdmin
    @PostMapping("/points/set-free-count")
    public Result<Void> setDailyFreeCount(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        Long userId = Long.valueOf(body.get("userId").toString());
        Integer count = (Integer) body.get("count");
        
        adminService.setDailyFreeCount(userId, count);
        
        return Result.success("设置成功", null);
    }

    // ==================== 会员管理接口 ====================

    /**
     * 调整用户会员等级
     */
    @RequireAdmin
    @PostMapping("/membership/update")
    public Result<Void> updateMembership(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        Long userId = Long.valueOf(body.get("userId").toString());
        String membership = (String) body.get("membership");
        
        adminService.updateMembership(userId, membership);
        
        return Result.success("会员等级调整成功", null);
    }

    /**
     * 设置会员有效期
     */
    @RequireAdmin
    @PostMapping("/membership/set-expire-time")
    public Result<Void> setMembershipExpireTime(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        Long userId = Long.valueOf(body.get("userId").toString());
        String expireTimeStr = (String) body.get("expireTime");
        java.time.LocalDateTime expireTime = java.time.LocalDateTime.parse(expireTimeStr);
        
        adminService.setMembershipExpireTime(userId, expireTime);
        
        return Result.success("会员有效期设置成功", null);
    }

    /**
     * 批量开通会员
     */
    @RequireAdmin
    @PostMapping("/batch/activate-membership")
    public Result<Map<String, Object>> batchActivateMembership(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        @SuppressWarnings("unchecked")
        java.util.List<Long> userIds = (java.util.List<Long>) body.get("userIds");
        String membership = (String) body.get("membership");
        Integer days = (Integer) body.get("days");
        
        if (userIds == null || userIds.isEmpty()) {
            return Result.error(400, "用户ID列表不能为空");
        }
        
        int successCount = adminService.batchActivateMembership(userIds, membership, days);
        
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
        
        Map<String, Object> logs = operationLogService.getOperationLogs(
            page, size, adminId, operationType, startDate, endDate);
        
        return Result.success(logs);
    }

    // ==================== 通知管理接口 ====================

    /**
     * 获取未读通知数量
     */
    @GetMapping("/notifications/unread-count")
    public Result<Map<String, Integer>> getUnreadCount(
            @RequestHeader("X-User-Token") String token) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        
        int count = notificationService.getUnreadCount(userId);
        
        Map<String, Integer> data = new java.util.HashMap<>();
        data.put("unreadCount", count);
        
        return Result.success(data);
    }

    /**
     * 获取通知列表
     */
    @GetMapping("/notifications")
    public Result<?> getNotifications(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer isRead) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        
        Map<String, Object> notifications = notificationService.getNotifications(
            userId, page, size, isRead);
        
        return Result.success(notifications);
    }

    /**
     * 标记通知为已读
     */
    @PostMapping("/notifications/{id}/read")
    public Result<Void> markAsRead(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        
        notificationService.markAsRead(id, userId);
        return Result.success("标记成功", null);
    }

    /**
     * 标记所有通知为已读
     */
    @PostMapping("/notifications/read-all")
    public Result<Void> markAllAsRead(
            @RequestHeader("X-User-Token") String token) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        
        notificationService.markAllAsRead(userId);
        return Result.success("全部标记成功", null);
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/notifications/{id}")
    public Result<Void> deleteNotification(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        
        notificationService.deleteNotification(id, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 获取最新未读通知（用于登录时弹窗）
     */
    @GetMapping("/notifications/latest")
    public Result<?> getLatestNotifications(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "5") int limit) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        
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
        
        java.util.List<com.ai.gateway.entity.PlatformStatistics> stats = statisticsService.getDailyStats(start, end);
        
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
        
        try {
            // 验证参数
            if (!"user".equals(type) && !"revenue".equals(type) && !"business".equals(type)) {
                response.setStatus(400);
                response.getWriter().write("{\"code\":400,\"message\":\"无效的报表类型\"}");
                return;
            }
            
            String csvData = statisticsService.exportStatsToCsv(
                java.time.LocalDate.parse(startDate),
                java.time.LocalDate.parse(endDate),
                type
            );
            
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", 
                "attachment; filename=" + type + "_stats_" + startDate + "_" + endDate + ".csv");
            response.getOutputStream().write(csvData.getBytes("UTF-8"));
            response.getOutputStream().flush();
            
        } catch (Exception e) {
            log.error("导出统计报表失败", e);
            try {
                response.setStatus(500);
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }

    // ==================== 辅助方法 ====================

    private Long getUserIdFromToken(String token) {
        return tokenService.getUserIdFromToken(token);
    }
}
