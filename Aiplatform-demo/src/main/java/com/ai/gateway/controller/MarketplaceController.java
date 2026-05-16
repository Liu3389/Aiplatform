package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.MarketplaceProvider;
import com.ai.gateway.entity.UserMarketplaceKey;
import com.ai.gateway.mapper.MarketplaceProviderMapper;
import com.ai.gateway.mapper.UserMarketplaceKeyMapper;
import com.ai.gateway.service.MarketplaceService;
import com.ai.gateway.service.PointsService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模型商店控制器
 * <p>
 * 模型商店功能搁置，当前仅有 DeepSeek API Key，待获取更多厂商 Key 后开放。
 * 目前接口仅返回开发中提示。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final UserMarketplaceKeyMapper userMarketplaceKeyMapper;
    private final MarketplaceProviderMapper providerMapper;
    private final MarketplaceService marketplaceService;
    private final TokenService tokenService;
    private final UserService userService;
    private final PointsService pointsService;

    /**
     * 获取用户已购买的模型商店 Key 列表
     * <p>
     * 模型商店功能搁置，当前仅有 DeepSeek API Key，待获取更多厂商 Key 后开放。
     * </p>
     *
     * @param token 用户 Token
     * @return 结果（当前返回开发中提示）
     */
    @GetMapping("/keys")
    public Result<List<UserMarketplaceKey>> listKeys(@RequestHeader("X-User-Token") String token) {
        return Result.error("模型商店功能正在开发中，敬请期待！");
    }

    /**
     * 购买模型商店 Key
     * <p>
     * 模型商店功能搁置，当前仅有 DeepSeek API Key，待获取更多厂商 Key 后开放。
     * </p>
     *
     * @param request 请求参数
     * @param token   用户 Token
     * @return 结果（当前返回开发中提示）
     */
    @PostMapping("/buy-key")
    public Result<Map<String, Object>> buyKey(@RequestBody Map<String, Object> request, @RequestHeader("X-User-Token") String token) {
        return Result.error("模型商店功能正在开发中，敬请期待！");
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 12) return "****";
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
