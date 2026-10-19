package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ApiKeyGenerateRequest;
import com.ai.gateway.service.ApiKeyService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.vo.ApiKeyInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "02-用户中心-API Key", description = "用户 API Key 生成与管理")
@RestController
@RequestMapping("/api-key")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final TokenService tokenService;

    private Long getUserIdFromHeader(String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            throw new RuntimeException("Token无效或已过期");
        }
        return userId;
    }

    @Operation(summary = "生成 API Key", description = "为当前用户生成新的 API Key，可指定名称与限流阈值")
    @PostMapping("/generate")
    public Result<ApiKeyInfoVO> generateApiKey(
            @RequestHeader("X-User-Token") String token,
            @RequestBody(required = false) ApiKeyGenerateRequest request) {
        Long userId = getUserIdFromHeader(token);
        String name = request != null ? request.getName() : null;
        Integer rateLimit = request != null ? request.getRateLimit() : null;

        ApiKeyInfoVO apiKeyInfo = apiKeyService.generateApiKey(userId, name, rateLimit);
        return Result.success("API Key生成成功", apiKeyInfo);
    }

    @Operation(summary = "删除 API Key")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        Long userId = getUserIdFromHeader(token);
        apiKeyService.deleteApiKey(id, userId);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "启用 API Key")
    @PutMapping("/{id}/enable")
    public Result<Void> enableApiKey(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        Long userId = getUserIdFromHeader(token);
        apiKeyService.enableApiKey(id, userId);
        return Result.success("启用成功", null);
    }

    @Operation(summary = "禁用 API Key")
    @PutMapping("/{id}/disable")
    public Result<Void> disableApiKey(@RequestHeader("X-User-Token") String token, @PathVariable Long id) {
        Long userId = getUserIdFromHeader(token);
        apiKeyService.disableApiKey(id, userId);
        return Result.success("禁用成功", null);
    }

    @Operation(summary = "更新 API Key 限流阈值")
    @PutMapping("/{id}/rate-limit")
    public Result<Void> updateRateLimit(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id,
            @RequestParam Integer rateLimit) {
        Long userId = getUserIdFromHeader(token);
        apiKeyService.updateRateLimit(id, userId, rateLimit);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "查询当前用户的 API Key 列表")
    @GetMapping("/list")
    public Result<List<ApiKeyInfoVO>> listApiKeys(@RequestHeader("X-User-Token") String token) {
        Long userId = getUserIdFromHeader(token);
        List<ApiKeyInfoVO> apiKeys = apiKeyService.listApiKeys(userId);
        return Result.success(apiKeys);
    }
}
