package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.service.PackageTemplateService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserSubscriptionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户订阅控制器
 */
@Tag(name = "02-用户中心-订阅", description = "套餐查询、订阅状态、购买与取消")
@Slf4j
@RestController
@RequestMapping("/user/subscriptions")
@RequiredArgsConstructor
public class UserSubscriptionController {

    private final PackageTemplateService packageTemplateService;
    private final UserSubscriptionService userSubscriptionService;
    private final TokenService tokenService;

    /**
     * 获取所有可用套餐列表
     */
    @Operation(summary = "查询可用套餐列表", description = "无需登录")
    @GetMapping("/packages")
    public Result<List<PackageTemplate>> listAvailablePackages() {
        List<PackageTemplate> packages = packageTemplateService.listActivePackages();
        return Result.success(packages);
    }

    /**
     * 获取当前用户的订阅状态
     */
    @Operation(summary = "查询当前订阅状态", description = "返回订阅信息、身份标签、token 限额、模型权限等级等")
    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentSubscription(
            @RequestHeader("X-User-Token") String token) {
        
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效");
        }
        
        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(userId);
        
        Map<String, Object> result = new HashMap<>();
        if (subscription != null) {
            result.put("hasSubscription", true);
            result.put("subscription", subscription);
            result.put("identityLabel", subscription.getIdentityLabel());
            result.put("expireTime", subscription.getExpireTime());
            result.put("pointsGranted", subscription.getPointsGranted());
            result.put("maxInputTokens", getMaxInputTokens(subscription.getPackageCode()));
            result.put("maxOutputTokens", getMaxOutputTokens(subscription.getPackageCode()));
            result.put("modelAccessLevel", getModelAccessLevel(subscription.getPackageCode()));
            result.put("priorityLevel", getPriorityLevel(subscription.getPackageCode()));
        } else {
            result.put("hasSubscription", false);
            result.put("identityLabel", "普通用户");
            result.put("message", "当前无生效的套餐订阅");
        }
        
        return Result.success(result);
    }

    /**
     * 获取用户的订阅历史
     */
    @Operation(summary = "查询订阅历史")
    @GetMapping("/history")
    public Result<List<UserSubscription>> getSubscriptionHistory(
            @RequestHeader("X-User-Token") String token) {
        
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效");
        }
        
        List<UserSubscription> history = userSubscriptionService.getSubscriptionHistory(userId);
        return Result.success(history);
    }

    @Operation(summary = "分页查询订阅历史")
    @GetMapping("/history/page")
    public Result<Page<UserSubscription>> getSubscriptionHistoryPage(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效");
        }
        return Result.success(userSubscriptionService.getSubscriptionHistoryPage(userId, page, size));
    }

    /**
     * 购买套餐
     */
    @Operation(summary = "购买套餐", description = "传 packageCode、cycle(MONTHLY默认)、可选 couponId")
    @PostMapping("/purchase")
    public Result<Map<String, Object>> purchasePackage(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, Object> body) {
        
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效");
        }
        
        String packageCode = (String) body.get("packageCode");
        if (packageCode == null || packageCode.isEmpty()) {
            return Result.error(400, "套餐代码不能为空");
        }

        // 获取订阅周期，默认为月度
        String cycle = (String) body.get("cycle");
        if (cycle == null || cycle.isEmpty()) {
            cycle = "MONTHLY";
        }
        
        Long couponId = body.containsKey("couponId") && body.get("couponId") != null
                ? Long.valueOf(body.get("couponId").toString()) : null;
        
        try {
            Map<String, Object> result = userSubscriptionService.purchasePackage(userId, packageCode, cycle, couponId);
            return Result.success("购买成功", result);
        } catch (Exception e) {
            log.error("购买套餐失败", e);
            int code = e instanceof IllegalArgumentException ? 400 : 500;
            return Result.error(code, "购买失败: " + e.getMessage());
        }
    }

    /**
     * 取消订阅
     */
    @Operation(summary = "取消订阅")
    @PostMapping("/cancel/{subscriptionId}")
    public Result<Void> cancelSubscription(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long subscriptionId) {
        
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效");
        }
        
        try {
            userSubscriptionService.cancelSubscription(userId, subscriptionId);
            return Result.success("已取消订阅", null);
        } catch (Exception e) {
            log.error("取消订阅失败", e);
            return Result.error(500, "取消失败: " + e.getMessage());
        }
    }

    // 辅助方法：根据套餐代码获取限制信息
    private Integer getMaxInputTokens(String packageCode) {
        PackageTemplate pkg = packageTemplateService.getByCode(packageCode);
        return pkg != null ? pkg.getMaxInputTokens() : 0;
    }

    private Integer getMaxOutputTokens(String packageCode) {
        PackageTemplate pkg = packageTemplateService.getByCode(packageCode);
        return pkg != null ? pkg.getMaxOutputTokens() : 0;
    }

    private Integer getModelAccessLevel(String packageCode) {
        PackageTemplate pkg = packageTemplateService.getByCode(packageCode);
        return pkg != null ? pkg.getModelAccessLevel() : 0;
    }

    private Integer getPriorityLevel(String packageCode) {
        PackageTemplate pkg = packageTemplateService.getByCode(packageCode);
        return pkg != null ? pkg.getPriorityLevel() : 0;
    }
}
