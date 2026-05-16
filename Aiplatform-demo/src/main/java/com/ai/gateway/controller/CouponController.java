package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.dto.AdminSendCouponRequest;
import com.ai.gateway.dto.AdminSendNotificationRequest;
import com.ai.gateway.entity.Coupon;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.CouponService;
import com.ai.gateway.service.SystemNotificationService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final SystemNotificationService notificationService;
    private final TokenService tokenService;
    private final UserService userService;
    private final AdminOperationLogService operationLogService;

    @GetMapping("/user/my-coupons")
    public Result<List<Coupon>> listUserCoupons(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        return Result.success(couponService.listByUser(userId));
    }

    @GetMapping("/user/coupon-usage-records")
    public Result<List<com.ai.gateway.entity.CouponUsageRecord>> listCouponUsageRecords(
            @RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        return Result.success(couponService.listUsageRecords(userId));
    }

    @PostMapping("/admin/coupons/send")
    @RequireAdmin
    public Result<Map<String, Object>> adminSendCoupons(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminSendCouponRequest request) {

        String target = request.getTarget() != null ? request.getTarget() : "all";
        LocalDateTime expireTime = request.getExpireTime() != null && !request.getExpireTime().isEmpty() 
                ? LocalDateTime.parse(request.getExpireTime()) : null;

        int count;
        switch (target) {
            case "membership":
                count = couponService.sendToMembership(request.getMembership(), request.getAmount(), 
                        request.getThresholdAmount(), request.getApplicablePlan(), expireTime);
                break;
            case "new_users":
                count = couponService.sendToRecentUsers(request.getDays() != null ? request.getDays() : 7, 
                        request.getAmount(), request.getThresholdAmount(), request.getApplicablePlan(), expireTime);
                break;
            default:
                count = couponService.sendToAllUsers(request.getAmount(), request.getThresholdAmount(), 
                        request.getApplicablePlan(), expireTime);
        }

        Long adminId = tokenService.getUserIdFromToken(token);
        com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
        String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
        operationLogService.logSuccess(adminId, adminUsername, "COUPON_MANAGE", 
                "SEND_COUPONS", null, java.util.Map.of("amount", request.getAmount(), "count", count));

        return Result.success(Map.of("sent", count, "amount", request.getAmount()));
    }

    @PostMapping("/admin/notifications/send")
    @RequireAdmin
    public Result<Map<String, Object>> adminSendNotification(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody AdminSendNotificationRequest request) {

        String target = request.getTarget() != null ? request.getTarget() : "all";
        int count;
        switch (target) {
            case "membership":
                count = notificationService.sendToMembership(request.getMembership(), 
                        request.getTitle(), request.getContent(), request.getType());
                break;
            case "new_users":
                count = notificationService.sendToRecentUsers(request.getDays() != null ? request.getDays() : 7, 
                        request.getTitle(), request.getContent(), request.getType());
                break;
            default:
                count = notificationService.sendToAllUsers(request.getTitle(), request.getContent(), request.getType());
        }

        Long adminId = tokenService.getUserIdFromToken(token);
        com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
        String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
        operationLogService.logSuccess(adminId, adminUsername, "NOTIFICATION_MANAGE", 
                "SEND_NOTIFICATION", null, java.util.Map.of("title", request.getTitle(), "count", count));

        return Result.success(Map.of("sent", count, "title", request.getTitle()));
    }
}
