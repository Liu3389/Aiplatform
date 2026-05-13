package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @PostMapping("/sync")
    public Result<Void> syncConversations(
            @RequestHeader("X-User-Token") String token,
            @RequestBody List<Map<String, Object>> conversations) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        userService.syncConversations(userId, conversations);
        return Result.success("同步成功", null);
    }

    @GetMapping("/fetch")
    public Result<List<Map<String, Object>>> fetchConversations(
            @RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        List<Map<String, Object>> conversations = userService.fetchConversations(userId);
        return Result.success(conversations);
    }

    @DeleteMapping("/clear")
    public Result<Void> clearConversations(
            @RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        userService.syncConversations(userId, List.of());
        return Result.success("清除成功", null);
    }
}
