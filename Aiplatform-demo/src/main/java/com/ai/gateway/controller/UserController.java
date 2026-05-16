package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.service.PointsService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;
    private final PointsService pointsService;

    @PostMapping("/recharge")
    public Result<Void> recharge(@RequestHeader("X-User-Token") String token, @RequestParam BigDecimal amount) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return Result.error("充值金额必须大于0");
        userService.recharge(userId, amount);
        return Result.success("充值成功", null);
    }

    @PostMapping("/recharge-with-coupon")
    public Result<Map<String, Object>> rechargeWithCoupon(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return Result.error("充值金额必须大于0");
        
        Long couponId = body.containsKey("couponId") && body.get("couponId") != null 
                ? Long.valueOf(body.get("couponId").toString()) : null;
        
        Map<String, Object> result = userService.rechargeWithCoupon(userId, amount, couponId);
        return Result.success(result);
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) return Result.error("缺少参数");
        userService.updatePassword(userId, oldPassword, newPassword);
        return Result.success("密码修改成功", null);
    }

    @GetMapping("/info")
    public Result<?> getUserInfo(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getUserInfo(userId));
    }

    @GetMapping("/balance")
    public Result<BigDecimal> getBalance(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getBalance(userId));
    }

    /**
     * 查询点数余额
     */
    @GetMapping("/points")
    public Result<BigDecimal> getPoints(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        BigDecimal points = pointsService.getUserPoints(userId);
        return Result.success(points);
    }

    @GetMapping("/free-quota")
    public Result<Map<String, Integer>> getFreeQuota(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getFreeQuota(userId));
    }

    @PostMapping("/bind-email")
    public Result<Void> bindEmail(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w+$")) {
            return Result.error("邮箱格式不正确");
        }
        if (code == null) return Result.error("验证码不能为空");
        userService.bindEmail(userId, email, code);
        return Result.success("邮箱绑定成功", null);
    }

    @PostMapping("/bind-phone")
    public Result<Void> bindPhone(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String phone = body.get("phone");
        String code = body.get("code");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return Result.error("手机号格式不正确");
        }
        if (code == null) return Result.error("验证码不能为空");
        userService.bindPhone(userId, phone, code);
        return Result.success("手机号绑定成功", null);
    }

    @PostMapping("/send-bind-code")
    public Result<Void> sendBindCode(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String type = body.get("type");
        String target = body.get("target");
        if (type == null || target == null) return Result.error("缺少参数");
        userService.sendBindCode(userId, type, target);
        return Result.success("验证码已发送", null);
    }

    @PostMapping("/2fa/enable")
    public Result<Map<String, String>> enable2FA(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        Map<String, String> result = userService.enable2FA(userId);
        return Result.success("2FA已启用", result);
    }

    @PostMapping("/2fa/disable")
    public Result<Void> disable2FA(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String code = body.get("code");
        if (code == null) return Result.error("验证码不能为空");
        userService.disable2FA(userId, code);
        return Result.success("2FA已禁用", null);
    }

    @PostMapping("/2fa/verify")
    public Result<Boolean> verify2FA(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String code = body.get("code");
        if (code == null) return Result.error("验证码不能为空");
        boolean valid = userService.verify2FA(userId, code);
        return Result.success(valid ? "验证通过" : "验证失败", valid);
    }

    @GetMapping("/2fa/status")
    public Result<Map<String, Object>> get2FAStatus(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.get2FAStatus(userId));
    }

    @GetMapping("/login-devices")
    public Result<List<Map<String, Object>>> getLoginDevices(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getLoginDevices(userId));
    }

    @PostMapping("/login-devices/remove")
    public Result<Void> removeLoginDevice(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String deviceId = body.get("deviceId");
        if (deviceId == null) return Result.error("设备ID不能为空");
        userService.removeLoginDevice(userId, deviceId);
        return Result.success("设备已移除", null);
    }

    @PostMapping("/login-devices/remove-all")
    public Result<Void> removeAllLoginDevices(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String exceptCurrent = body.get("exceptCurrent");
        userService.removeAllLoginDevices(userId, "true".equals(exceptCurrent));
        return Result.success("已移除所有登录设备", null);
    }

    @PostMapping("/delete-account")
    public Result<Void> deleteAccount(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String password = body.get("password");
        String confirm = body.get("confirm");
        if (password == null) return Result.error("密码不能为空");
        if (!"DELETE".equals(confirm)) return Result.error("请输入 DELETE 确认注销");
        userService.deleteAccount(userId, password);
        return Result.success("账户已注销", null);
    }

    @GetMapping("/recharge-records")
    public Result<List<Map<String, Object>>> getRechargeRecords(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getRechargeRecords(userId, page, size));
    }

    /**
     * 获取点数账单列表（统一点数体系）
     * 包含充值、消费、赠送等所有点数变动记录
     */
    @GetMapping("/points-bills")
    public Result<?> getPointsBills(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String changeType) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        
        // 调用 PointsService 查询点数账单
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.ai.gateway.entity.PointsBill> billPage = 
            pointsService.getPointsBillList(userId, page, size, changeType);
        
        return Result.success(billPage);
    }

    @PostMapping("/invoice/apply")
    public Result<Void> applyInvoice(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, Object> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        userService.applyInvoice(userId, body);
        return Result.success("发票申请已提交", null);
    }

    @GetMapping("/coupons")
    public Result<List<Map<String, Object>>> getCoupons(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getCoupons(userId));
    }

    @PostMapping("/coupons/use")
    public Result<Map<String, Object>> useCoupon(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String code = body.get("code");
        if (code == null) return Result.error("优惠券码不能为空");
        return Result.success(userService.useCoupon(userId, code));
    }

    @PostMapping("/coupons/redeem")
    public Result<Void> redeemCoupon(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String redeemCode = body.get("redeemCode");
        if (redeemCode == null) return Result.error("兑换码不能为空");
        userService.redeemCoupon(userId, redeemCode);
        return Result.success("兑换成功", null);
    }

    /**
     * 获取订阅信息（已废弃）
     * @deprecated 已废弃，请迁移到 /user/subscriptions/* 系列接口
     */
    @Deprecated
    @GetMapping("/subscription")
    public Result<Map<String, Object>> getSubscription(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getSubscription(userId));
    }

    /**
     * 订阅套餐（已废弃）
     * @deprecated 已废弃，请迁移到 /user/subscriptions/* 系列接口
     */
    @Deprecated
    @PostMapping("/subscription/subscribe")
    public Result<Void> subscribe(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String plan = body.get("plan");
        String paymentMethod = body.get("paymentMethod");
        if (plan == null || paymentMethod == null) return Result.error("缺少参数");
        userService.subscribe(userId, plan, paymentMethod);
        return Result.success("订阅成功（该接口已废弃，请使用新版订阅接口）", null);
    }

    /**
     * 取消订阅（已废弃）
     * @deprecated 已废弃，请迁移到 /user/subscriptions/* 系列接口
     */
    @Deprecated
    @PostMapping("/subscription/cancel")
    public Result<Void> cancelSubscription(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        userService.cancelSubscription(userId);
        return Result.success("订阅已取消（该接口已废弃，请使用新版订阅接口）", null);
    }
}
