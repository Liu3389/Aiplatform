package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.UserNotification;
import com.ai.gateway.service.NotificationFacadeService;
import com.ai.gateway.service.NotificationSseManager;
import com.ai.gateway.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "02-用户中心-通知", description = "统一通知查询、已读管理与实时推送（SSE）")
@Slf4j
@RestController
@RequestMapping("/user/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationFacadeService notificationFacade;
    private final NotificationSseManager sseManager;
    private final TokenService tokenService;

    @Operation(summary = "查询通知列表", description = "可按 unreadOnly=true 只查未读")
    @GetMapping
    public Result<List<UserNotification>> list(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            log.warn("通知列表请求: Token无效");
            return Result.error(401, "Token无效");
        }
        List<UserNotification> notifications = notificationFacade.listByUser(userId, unreadOnly);
        log.info("通知列表查询: userId={}, unreadOnly={}, count={}", userId, unreadOnly, notifications.size());
        return Result.success(notifications);
    }

    @Operation(summary = "查询未读通知数量")
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            log.warn("未读计数请求: Token无效");
            return Result.error(401, "Token无效");
        }
        int count = notificationFacade.getUnreadCount(userId);
        log.info("未读计数查询: userId={}, unreadCount={}", userId, count);
        Map<String, Integer> m = new HashMap<>();
        m.put("unreadCount", count);
        return Result.success(m);
    }

    @Operation(summary = "标记单条通知为已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long id) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        notificationFacade.markAsRead(id, userId);
        return Result.success(null);
    }

    @Operation(summary = "标记所有通知为已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效");
        notificationFacade.markAllAsRead(userId);
        return Result.success(null);
    }

    @Operation(summary = "订阅通知实时推送（SSE）", description = "长连接，收到 notification 事件即代表新通知。token 通过 query 传递（EventSource 不支持自定义头）")
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            SseEmitter err = new SseEmitter();
            try {
                err.send(SseEmitter.event().name("error").data("{\"message\":\"Token无效\"}"));
                err.complete();
            } catch (Exception ignored) {}
            return err;
        }
        log.info("用户订阅通知SSE: userId={}", userId);
        return sseManager.register(userId);
    }
}
