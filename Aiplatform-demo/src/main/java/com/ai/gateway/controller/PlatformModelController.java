package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.service.PlatformModelService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform-model")
@RequiredArgsConstructor
public class PlatformModelController {

    private final PlatformModelService platformModelService;
    private final TokenService tokenService;
    private final UserSubscriptionService userSubscriptionService;

    @GetMapping("/list")
    public Result<List<PlatformModelConfig>> listModels() {
        List<PlatformModelConfig> models = platformModelService.listEnabledModels();
        return Result.success(models);
    }

    /**
     * 获取当前用户可用的模型列表（含权限信息）
     * 前端根据 accessible 字段灰显/隐藏不可用模型
     */
    @GetMapping("/available")
    public Result<List<Map<String, Object>>> getAvailableModels(
            @RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }

        int userAccessLevel = 0;
        try {
            UserSubscription subscription = userSubscriptionService.getCurrentSubscription(userId);
            if (subscription != null) {
                PackageTemplate pkg = new PackageTemplate();
                pkg.setId(subscription.getPackageId());
                userAccessLevel = subscription.getPackageId() != null ? getAccessLevel(subscription) : 0;
            }
        } catch (Exception e) {
            // 查询失败时降级为免费用户权限
        }

        List<Map<String, Object>> models = platformModelService.getAvailableModels(userAccessLevel);
        return Result.success(models);
    }

    private int getAccessLevel(UserSubscription subscription) {
        if (subscription == null || subscription.getPackageCode() == null) {
            return 0;
        }
        return switch (subscription.getPackageCode()) {
            case "silver" -> 1;
            case "gold" -> 2;
            case "diamond" -> 3;
            default -> 0;
        };
    }
}
