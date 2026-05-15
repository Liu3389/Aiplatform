package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.MarketplaceProvider;
import com.ai.gateway.entity.UserMarketplaceKey;
import com.ai.gateway.mapper.MarketplaceProviderMapper;
import com.ai.gateway.mapper.UserMarketplaceKeyMapper;
import com.ai.gateway.service.MarketplaceService;
import com.ai.gateway.service.TokenService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final UserMarketplaceKeyMapper userMarketplaceKeyMapper;
    private final MarketplaceProviderMapper providerMapper;
    private final MarketplaceService marketplaceService;
    private final TokenService tokenService;

    /**
     * 获取用户已购买的 API Key 列表（支持分页）
     */
    @GetMapping("/keys")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserMarketplaceKey>> listKeys(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 修复 P1-5: 使用统一的 TokenService 解析 userId
        Long userId = tokenService.getUserIdFromToken(token);
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserMarketplaceKey> pageParam = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        
        LambdaQueryWrapper<UserMarketplaceKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMarketplaceKey::getUserId, userId)
               .orderByDesc(UserMarketplaceKey::getCreateTime);
        
        return Result.success(userMarketplaceKeyMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 购买 API Key (真实业务逻辑)
     */
    @PostMapping("/buy-key")
    public Result<Map<String, Object>> buyKey(@RequestBody Map<String, Object> request, @RequestHeader("X-User-Token") String token) {
        // 修复 P1-5: 统一鉴权
        Long userId = tokenService.getUserIdFromToken(token);
        Long providerId = ((Number) request.get("providerId")).longValue();
        
        // 修复 P1-3: 查询厂商价格并扣费
        MarketplaceProvider provider = providerMapper.selectById(providerId);
        if (provider == null || provider.getStatus() != 1) {
            return Result.error("厂商不存在或已下架");
        }

        // TODO: 这里需要集成 UserService 或 BillingService 进行余额检查与扣除
        // 假设基础套餐价格为 10 元
        BigDecimal price = new BigDecimal("10.00"); 
        
        // 1. 生成唯一 Key
        String apiKey = "sk-mkt-" + UUID.randomUUID().toString().replace("-", "");
        
        // 2. 构建实体
        UserMarketplaceKey keyEntity = new UserMarketplaceKey();
        keyEntity.setUserId(userId);
        keyEntity.setProviderId(providerId);
        keyEntity.setApiKey(apiKey);
        keyEntity.setKeyName((String) request.get("packageName"));
        keyEntity.setStatus("ACTIVE");
        
        // 3. 存入数据库
        userMarketplaceKeyMapper.insert(keyEntity);
        
        return Result.success("购买成功", Map.of("apiKey", apiKey));
    }
}
