package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/user/conversations")
public class ConversationController {

    private final UserService userService;
    private final TokenService tokenService;

    public ConversationController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    private Long resolveUserId(String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) throw new RuntimeException("Token无效或已过期");
        return userId;
    }

    @PostMapping("/sync")
    public Result<Map<String, Object>> syncConversations(
            @RequestHeader("X-User-Token") String token,
            @RequestBody List<Map<String, Object>> conversations) {
        Long userId = resolveUserId(token);
        long version = userService.syncConversations(userId, conversations);
        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        result.put("count", conversations.size());
        return Result.success(result);
    }

    @GetMapping("/fetch")
    public Result<Map<String, Object>> fetchConversations(
            @RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        List<Map<String, Object>> conversations = userService.fetchConversations(userId);
        long version = userService.getConversationVersion(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("conversations", conversations);
        result.put("version", version);
        return Result.success(result);
    }

    @GetMapping("/version")
    public Result<Map<String, Object>> getVersion(
            @RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        long version = userService.getConversationVersion(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        return Result.success(result);
    }

    @DeleteMapping("/clear")
    public Result<Void> clearConversations(
            @RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        userService.syncConversations(userId, List.of());
        return Result.success("清除成功", null);
    }
}
