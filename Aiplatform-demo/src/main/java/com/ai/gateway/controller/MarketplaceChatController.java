package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.UserMarketplaceKey;
import com.ai.gateway.mapper.UserMarketplaceKeyMapper;
import com.ai.gateway.service.MarketplaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 模型商店 API 调用控制器 (P0-3 修复)
 */
@Slf4j
@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
public class MarketplaceChatController {

    private final UserMarketplaceKeyMapper userMarketplaceKeyMapper;
    private final MarketplaceService marketplaceService;

    /**
     * 使用商店 Key 进行对话
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestBody Map<String, Object> request) {
        
        // 1. 验证 Key 有效性
        LambdaQueryWrapper<UserMarketplaceKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMarketplaceKey::getApiKey, apiKey);
        UserMarketplaceKey keyEntity = userMarketplaceKeyMapper.selectOne(wrapper);
        
        if (keyEntity == null || !"ACTIVE".equals(keyEntity.getStatus())) {
            return Result.error("API Key 无效或已失效");
        }

        try {
            // 2. 调用底层 AI 服务（此处简化，实际应转发请求）
            // 3. 记录用量并扣费
            BigDecimal cost = new BigDecimal("0.05"); // 示例价格
            marketplaceService.recordUsage(
                keyEntity.getId(), 
                keyEntity.getUserId(), 
                (String) request.get("model"),
                100, 50, cost, 1000
            );

            return Result.success(Map.of("content", "这是通过模型商店 Key 调用的回复"));
        } catch (Exception e) {
            log.error("模型商店调用失败", e);
            return Result.error("调用失败: " + e.getMessage());
        }
    }
}
