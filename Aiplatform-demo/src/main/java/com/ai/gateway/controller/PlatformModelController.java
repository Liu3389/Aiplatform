package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.service.PlatformModelService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "06-模型市场-平台模型", description = "平台可用模型列表与用户权限查询")
@RestController
@RequestMapping("/platform-model")
@RequiredArgsConstructor
public class PlatformModelController {

    private final PlatformModelService platformModelService;
    private final TokenService tokenService;
    private final UserSubscriptionService userSubscriptionService;

    @Operation(summary = "查询已启用的模型列表", description = "返回平台所有启用的模型配置（脱敏，不含apiKey），无需登录")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listModels() {
        List<PlatformModelConfig> models = platformModelService.listEnabledModels();
        List<Map<String, Object>> sanitized = models.stream().map(m -> {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", m.getId());
            item.put("displayName", m.getDisplayName());
            item.put("modelCode", m.getActualModel());
            item.put("fixedPoints", m.getFixedPoints());
            item.put("provider", m.getProvider());
            item.put("description", m.getDescription());
            item.put("supportsVision", m.getSupportsVision());
            item.put("sortOrder", m.getSortOrder());
            item.put("accessLevel", m.getAccessLevel());
            // 仅向前端暴露是否具备基础上游配置，不返回 API Key。
            item.put("chatEnabled", platformModelService.isChatEnabled(m));
            return item;
        }).collect(java.util.stream.Collectors.toList());
        return Result.success(sanitized);
    }

    /**
     * 获取当前用户可用的模型列表（含权限信息）
     * 前端根据 accessible 字段灰显/隐藏不可用模型
     */
    @Operation(summary = "查询当前用户可用的模型列表", description = "根据用户订阅等级返回模型及 accessible 权限标识")
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
