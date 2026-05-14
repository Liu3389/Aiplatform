package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.service.AdminService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.SystemStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AdminService adminService;

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
}
