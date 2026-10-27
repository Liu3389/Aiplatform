package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "02-用户中心", description = "用户信息、余额点数、安全设置(2FA/绑定)、设备管理、发票优惠券")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;
    private final PointsService pointsService;
    private final UserSubscriptionService userSubscriptionService;
    private final PackageTemplateService packageTemplateService;
    private final CouponService couponService;

    @Operation(summary = "充值余额")
    @PostMapping("/recharge")
    public Result<Void> recharge(@RequestHeader("X-User-Token") String token, @RequestParam BigDecimal amount) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return Result.error("充值金额必须大于0");
        return Result.error(503, "自助充值暂未接入可信支付下单与验签回调，账户余额未变更");
    }

    @Operation(summary = "使用优惠券充值")
    @PostMapping("/recharge-with-coupon")
    public Result<Map<String, Object>> rechargeWithCoupon(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.error(503, "优惠券充值暂不可用：需先接入可信支付订单；优惠券和账户余额均未变更");
    }

    @Operation(summary = "修改密码")
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

    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<?> getUserInfo(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getUserInfo(userId));
    }

    @Operation(summary = "上传自定义头像", description = "支持 PNG、JPG、WebP，最大 5 MB")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> uploadAvatar(@RequestHeader("X-User-Token") String token,
                                  @RequestPart("file") MultipartFile file) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        try {
            return Result.success("头像已更新", userService.uploadAvatar(userId, file));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "恢复默认头像", description = "清除自定义头像并回退至 QQ 头像或默认头像")
    @DeleteMapping("/avatar")
    public Result<?> clearAvatar(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success("已恢复默认头像", userService.clearAvatar(userId));
    }

    @Operation(summary = "查询账户余额")
    @GetMapping("/balance")
    public Result<BigDecimal> getBalance(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getBalance(userId));
    }

    /**
     * 查询点数余额
     */
    @Operation(summary = "查询点数余额")
    @GetMapping("/points")
    public Result<BigDecimal> getPoints(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        BigDecimal points = pointsService.getUserPoints(userId);
        return Result.success(points);
    }

    /**
     * 查询点数详情（限时/永久拆分 + 会员每日发放信息）
     */
    @Operation(summary = "查询点数详情", description = "限时/永久拆分 + 会员每日发放信息")
    @GetMapping("/points/info")
    public Result<Map<String, Object>> getPointsInfo(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        Map<String, Object> pointsInfo = pointsService.getUserPointsInfo(userId);

        UserSubscription subscription = null;
        try {
            subscription = userSubscriptionService.getCurrentSubscription(userId);
        } catch (Exception ignored) {
        }

        if (subscription != null) {
            pointsInfo.put("hasActiveSubscription", true);
            pointsInfo.put("membershipLabel", subscription.getIdentityLabel());
            pointsInfo.put("dailyTemporaryPoints", 30);
            pointsInfo.put("dailyPermanentPoints", subscription.getPointsGranted() != null
                    ? subscription.getPointsGranted().intValue() : 0);
        } else {
            pointsInfo.put("hasActiveSubscription", false);
            pointsInfo.put("membershipLabel", "普通用户");
            pointsInfo.put("dailyTemporaryPoints", 30);
            pointsInfo.put("dailyPermanentPoints", 0);
        }

        return Result.success(pointsInfo);
    }

    @Operation(summary = "查询用户每日用量统计", description = "token/调用次数/点数消耗按日聚合，days=7或30")
    @GetMapping("/usage/daily")
    public Result<Map<String, Object>> getDailyUsage(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "7") int days) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getDailyUsage(userId, days));
    }

    @Operation(summary = "绑定邮箱", description = "需传邮箱与验证码")
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

    @Operation(summary = "绑定手机号", description = "需传手机号与验证码")
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

    @Operation(summary = "发送绑定验证码", description = "type=email/phone，target=邮箱或手机号")
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

    @Operation(summary = "开始设置两步验证(2FA)", description = "返回待确认密钥；提交有效动态码后才正式启用")
    @PostMapping("/2fa/enable")
    public Result<Map<String, String>> enable2FA(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        Map<String, String> result = userService.enable2FA(userId);
        return Result.success("请在验证器中添加密钥并输入动态验证码确认启用", result);
    }

    @Operation(summary = "禁用两步验证(2FA)", description = "需传验证码")
    @PostMapping("/2fa/disable")
    public Result<Void> disable2FA(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String code = body.get("code");
        if (code == null) return Result.error("验证码不能为空");
        userService.disable2FA(userId, code);
        return Result.success("2FA已禁用", null);
    }

    @Operation(summary = "验证两步验证(2FA)验证码")
    @PostMapping("/2fa/verify")
    public Result<Map<String, Object>> verify2FA(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String code = body.get("code");
        if (code == null) return Result.error("验证码不能为空");
        return Result.success("两步验证已启用，请安全保存本次显示的备用码", userService.verify2FA(userId, code));
    }

    @Operation(summary = "查询两步验证(2FA)状态")
    @GetMapping("/2fa/status")
    public Result<Map<String, Object>> get2FAStatus(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.get2FAStatus(userId));
    }

    @Operation(summary = "查询登录设备列表")
    @GetMapping("/login-devices")
    public Result<List<Map<String, Object>>> getLoginDevices(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userService.getLoginDevices(userId));
    }

    @Operation(summary = "移除指定登录设备")
    @PostMapping("/login-devices/remove")
    public Result<Void> removeLoginDevice(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String deviceId = body.get("deviceId");
        if (deviceId == null) return Result.error("设备ID不能为空");
        userService.removeLoginDevice(userId, deviceId);
        return Result.success("设备已移除", null);
    }

    @Operation(summary = "移除所有登录设备", description = "exceptCurrent=true 时保留当前设备")
    @PostMapping("/login-devices/remove-all")
    public Result<Void> removeAllLoginDevices(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String exceptCurrent = body.get("exceptCurrent");
        userService.removeAllLoginDevices(userId, "true".equals(exceptCurrent));
        return Result.success("已移除所有登录设备", null);
    }

    @Operation(summary = "停用账户", description = "需传密码且 confirm=DEACTIVATE 确认；/delete-account 保留为兼容路径")
    @PostMapping({"/deactivate-account", "/delete-account"})
    public Result<Void> deactivateAccount(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        String password = body.get("password");
        String confirm = body.get("confirm");
        if (password == null) return Result.error("密码不能为空");
        if (!"DEACTIVATE".equals(confirm) && !"DELETE".equals(confirm)) {
            return Result.error("账号停用确认参数无效");
        }
        userService.deactivateAccount(userId, password);
        return Result.success("账号已停用，账号资料和历史数据已保留", null);
    }

    @Operation(summary = "查询充值记录", description = "分页：page、size")
    @GetMapping("/recharge-records")
    public Result<Map<String, Object>> getRechargeRecords(
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
    @Operation(summary = "查询点数账单", description = "分页，可按 changeType 过滤")
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

    /**
     * 获取点数消费汇总（今天/本月/总计）
     */
    @Operation(summary = "查询点数消费汇总", description = "今天/本月/总计")
    @GetMapping("/points/consumption-summary")
    public Result<Map<String, Object>> getPointsConsumptionSummary(
            @RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        Map<String, Object> summary = pointsService.getConsumptionSummary(userId);
        return Result.success(summary);
    }

    /**
     * 获取用户充值/购买历史（包含点数充值和订阅购买）
     */
    @Operation(summary = "查询充值/购买历史", description = "含点数充值和订阅购买，分页")
    @GetMapping("/recharge-history")
    public Result<Map<String, Object>> getRechargeHistory(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        Map<String, Object> history = userService.getRechargeHistory(userId, page, size);
        return Result.success(history);
    }

    /**
     * 获取用户会员状态详情
     */
    @Operation(summary = "查询会员状态详情", description = "身份标签、会员到期、余额点数、当前订阅、每日点数")
    @GetMapping("/membership/status")
    public Result<Map<String, Object>> getMembershipStatus(
            @RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        Map<String, Object> result = new HashMap<>();

        // 获取用户基本信息
        com.ai.gateway.entity.User user = userService.getUserEntityById(userId);
        String identityLabel = user.getMembership() != null ? user.getMembership() : "free";
        String labelDisplay = "普通用户";

        switch (identityLabel) {
            case "silver":
                labelDisplay = "白银会员";
                break;
            case "gold":
                labelDisplay = "黄金会员";
                break;
            case "diamond":
                labelDisplay = "钻石会员";
                break;
            default:
                labelDisplay = "普通用户";
                break;
        }

        result.put("identityLabel", identityLabel);
        result.put("identityLabelDisplay", labelDisplay);
        result.put("membershipExpireTime", user.getMembershipExpireTime());
        result.put("balance", user.getBalance());
        result.put("points", user.getPoints());

        // 获取当前生效订阅
        UserSubscription subscription = null;
        try {
            subscription = userSubscriptionService.getCurrentSubscription(userId);
        } catch (Exception ignored) {
        }

        if (subscription != null) {
            result.put("hasActiveSubscription", true);
            result.put("subscription", Map.of(
                    "id", subscription.getId(),
                    "packageCode", subscription.getPackageCode(),
                    "packageName", subscription.getPackageName(),
                    "identityLabel", subscription.getIdentityLabel(),
                    "startTime", subscription.getStartTime(),
                    "expireTime", subscription.getExpireTime(),
                    "pointsGranted", subscription.getPointsGranted(),
                    "pricePaid", subscription.getPricePaid()
            ));
            // 从套餐模板获取每日点数
            PackageTemplate pkg = packageTemplateService.getByCode(subscription.getPackageCode());
            result.put("dailyPoints", pkg != null && pkg.getDailyPoints() != null ? pkg.getDailyPoints() : BigDecimal.ZERO);
        } else {
            result.put("hasActiveSubscription", false);
            result.put("dailyPoints", new BigDecimal("30.000"));
        }

        return Result.success(result);
    }

    @Operation(summary = "申请发票")
    @PostMapping("/invoice/apply")
    public Result<Void> applyInvoice(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, Object> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        userService.applyInvoice(userId, body);
        return Result.success("发票申请已提交", null);
    }

    @Operation(summary = "查询我的优惠券列表")
    @GetMapping("/coupons")
    public Result<List<Map<String, Object>>> getCoupons(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        List<Map<String, Object>> coupons = couponService.listByUser(userId).stream().map(coupon -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", coupon.getId());
            item.put("code", coupon.getCouponCode());
            item.put("name", "代金券");
            item.put("type", "CASH");
            item.put("value", coupon.getAmount());
            item.put("minAmount", coupon.getThresholdAmount());
            item.put("applicablePlan", coupon.getApplicablePlan());
            item.put("expireTime", coupon.getExpireTime());
            boolean expired = coupon.getExpireTime() != null
                    && coupon.getExpireTime().isBefore(LocalDateTime.now());
            item.put("status", Boolean.TRUE.equals(coupon.getUsed()) ? "USED" : expired ? "EXPIRED" : "AVAILABLE");
            return item;
        }).toList();
        return Result.success(coupons);
    }

    @Operation(summary = "使用优惠券", description = "传优惠券码 code")
    @PostMapping("/coupons/use")
    public Result<Map<String, Object>> useCoupon(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.error(503, "优惠券不能单独消费；请在已开放的订单确认流程中使用，优惠券未变更");
    }

    @Operation(summary = "兑换优惠券", description = "传兑换码 redeemCode")
    @PostMapping("/coupons/redeem")
    public Result<Void> redeemCoupon(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.error(503, "兑换码功能暂未开放，账户与优惠券均未变更");
    }

}
