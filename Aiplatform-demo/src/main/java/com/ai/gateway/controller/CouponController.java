package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.entity.Coupon;
import com.ai.gateway.service.CouponService;
import com.ai.gateway.service.SystemNotificationService;
import com.ai.gateway.service.TokenService;
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
            @RequestBody Map<String, Object> body) {

        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        BigDecimal threshold = body.containsKey("thresholdAmount") && body.get("thresholdAmount") != null
                ? new BigDecimal(body.get("thresholdAmount").toString()) : BigDecimal.ZERO;
        String plan = body.containsKey("applicablePlan") ? (String) body.get("applicablePlan") : null;
        String expireStr = (String) body.get("expireTime");
        LocalDateTime expireTime = expireStr != null && !expireStr.isEmpty() ? LocalDateTime.parse(expireStr) : null;

        String target = (String) body.get("target");
        int count;
        switch (target != null ? target : "all") {
            case "membership":
                count = couponService.sendToMembership((String) body.get("membership"), amount, threshold, plan, expireTime);
                break;
            case "new_users":
                count = couponService.sendToRecentUsers(Integer.parseInt(body.get("days").toString()), amount, threshold, plan, expireTime);
                break;
            default:
                count = couponService.sendToAllUsers(amount, threshold, plan, expireTime);
        }

        return Result.success(Map.of("sent", count, "amount", amount));
    }

    @PostMapping("/admin/notifications/send")
    @RequireAdmin
    public Result<Map<String, Object>> adminSendNotification(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {

        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String type = body.containsKey("type") ? (String) body.get("type") : "system";
        String target = (String) body.get("target");

        int count;
        switch (target != null ? target : "all") {
            case "membership":
                count = notificationService.sendToMembership((String) body.get("membership"), title, content, type);
                break;
            case "new_users":
                count = notificationService.sendToRecentUsers(Integer.parseInt(body.get("days").toString()), title, content, type);
                break;
            default:
                count = notificationService.sendToAllUsers(title, content, type);
        }

        return Result.success(Map.of("sent", count, "title", title));
    }
}
