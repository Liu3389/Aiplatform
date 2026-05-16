package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.SystemNotification;
import com.ai.gateway.service.SystemNotificationService;
import com.ai.gateway.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SystemNotificationService notificationService;
    private final TokenService tokenService;

    @GetMapping
    public Result<List<SystemNotification>> list(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        return Result.success(notificationService.listByUser(userId, unreadOnly));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        Map<String, Integer> m = new HashMap<>();
        m.put("unreadCount", notificationService.getUnreadCount(userId));
        return Result.success(m);
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        notificationService.markAsRead(id, userId);
        return Result.success(null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }
}
