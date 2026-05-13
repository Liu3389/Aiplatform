package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.UserApiKey;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user/api-keys")
@RequiredArgsConstructor
public class UserApiKeyController {

    private final UserApiKeyService userApiKeyService;
    private final TokenService tokenService;

    @GetMapping
    public Result<List<UserApiKey>> list(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(userApiKeyService.listByUser(userId));
    }

    @PostMapping
    public Result<UserApiKey> add(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        String provider = body.get("provider");
        String displayName = body.get("displayName");
        String apiKey = body.get("apiKey");
        String baseUrl = body.get("baseUrl");

        if (provider == null || apiKey == null) return Result.error("provider 和 apiKey 不能为空");

        try {
            UserApiKey result = userApiKeyService.addKey(userId, provider, displayName, apiKey, baseUrl);
            return Result.success("添加成功", result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        userApiKeyService.deleteKey(userId, id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/test")
    public Result<Void> testConnection(
            @RequestHeader("X-User-Token") String token,
            @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        String provider = body.get("provider");
        String apiKey = body.get("apiKey");
        String baseUrl = body.get("baseUrl");

        if (provider == null || apiKey == null) return Result.error("provider 和 apiKey 不能为空");

        try {
            UserApiKey temp = new UserApiKey();
            temp.setProvider(provider);
            temp.setApiKey(apiKey);
            temp.setBaseUrl(baseUrl);
            userApiKeyService.addKey(userId, provider, "test", apiKey, baseUrl);
            return Result.success("连接测试通过", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
