package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.service.AdminService;
import com.ai.gateway.service.TokenService;
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

    private final TokenService tokenService;
    private final UserService userService;
    private final AdminService adminService;

    private void requireAdmin(String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            throw new RuntimeException("Token无效");
        }
        var user = userService.getUserEntityById(userId);
        String role = user.getRole();
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new RuntimeException("权限不足，需要管理员角色");
        }
    }

    @GetMapping("/stats")
    public Result<SystemStatsVO> getStats(@RequestHeader("X-User-Token") String token) {
        requireAdmin(token);
        return Result.success(adminService.getSystemStats());
    }

    @GetMapping("/users")
    public Result<SystemStatsVO> getUsers(@RequestHeader("X-User-Token") String token) {
        requireAdmin(token);
        return Result.success(adminService.getSystemStats());
    }

    @GetMapping("/user/{userId}")
    public Result<SystemStatsVO.UserStatVO> getUserDetail(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long userId) {
        requireAdmin(token);
        var detail = adminService.getUserDetail(userId);
        return Result.success(detail);
    }

    @PostMapping("/user/status")
    public Result<Void> toggleUserStatus(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        requireAdmin(token);
        Long userId = Long.valueOf(body.get("userId").toString());
        String status = (String) body.get("status");
        userService.toggleUserStatus(userId, status);
        return Result.success("操作成功", null);
    }

    @PostMapping("/user/recharge")
    public Result<Void> rechargeUser(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        requireAdmin(token);
        Long userId = Long.valueOf(body.get("userId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        userService.recharge(userId, amount);
        return Result.success("充值成功", null);
    }

    @PostMapping("/assign-role")
    public Result<Void> assignAdminRole(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        requireAdmin(token);
        Long userId = Long.valueOf(body.get("userId").toString());
        String role = (String) body.get("role");
        userService.assignRole(userId, role);
        return Result.success("角色分配成功", null);
    }

    @PostMapping("/revoke-role")
    public Result<Void> revokeAdminRole(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        requireAdmin(token);
        Long userId = Long.valueOf(body.get("userId").toString());
        userService.assignRole(userId, "USER");
        return Result.success("角色撤销成功", null);
    }

    @PostMapping("/reset-password")
    public Result<Void> resetUserPassword(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        requireAdmin(token);
        Long userId = Long.valueOf(body.get("userId").toString());
        String password = (String) body.get("password");
        userService.resetPassword(userId, password);
        return Result.success("密码重置成功", null);
    }

    @GetMapping("/analytics/full")
    public Result<SystemStatsVO> getFullAnalytics(@RequestHeader("X-User-Token") String token) {
        requireAdmin(token);
        SystemStatsVO stats = adminService.getSystemStats();
        stats.setDailyUsage(adminService.getDailyUsage());
        stats.setModelUsage(adminService.getModelUsage());
        stats.setHourlyTraffic(adminService.getHourlyTraffic());
        stats.setTopUsers(adminService.getTopUsers());
        stats.setRevenueTrend(adminService.getRevenueTrend());
        stats.setUserGrowth(adminService.getUserGrowth());
        return Result.success(stats);
    }
}
