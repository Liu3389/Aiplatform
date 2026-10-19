package com.ai.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知专用 SSE 连接管理器（按 userId 索引的长连接）。
 *
 * 与聊天流式 SseConnectionManager 不同，本管理器维护的是 per-user 长连接，
 * 用于实时推送通知事件（新通知到达即推送，前端无需轮询）。
 *
 * 连接超时 30 分钟，每 30 秒发送心跳保活防止 Nginx 断连。
 */
@Slf4j
@Component
public class NotificationSseManager {

    /** 连接超时：30 分钟 */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, SseEmitter> userConnections = new ConcurrentHashMap<>();

    /**
     * 注册用户的长连接。
     */
    public SseEmitter register(Long userId) {
        // 关闭旧连接（同一用户重复连接时替换）
        SseEmitter old = userConnections.remove(userId);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        userConnections.put(userId, emitter);

        emitter.onCompletion(() -> {
            userConnections.remove(userId, emitter);
            log.debug("通知SSE连接关闭: userId={}", userId);
        });
        emitter.onTimeout(() -> {
            userConnections.remove(userId, emitter);
            log.debug("通知SSE连接超时: userId={}", userId);
        });
        emitter.onError(e -> {
            userConnections.remove(userId, emitter);
            log.debug("通知SSE连接错误: userId={}, err={}", userId, e.getMessage());
        });

        log.info("通知SSE连接已注册: userId={}, 当前在线={}", userId, userConnections.size());
        return emitter;
    }

    /**
     * 向指定用户推送通知事件。连接不存在时静默跳过（用户不在线）。
     */
    public void push(Long userId, String eventName, Object data) {
        SseEmitter emitter = userConnections.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            userConnections.remove(userId, emitter);
            log.debug("通知SSE推送失败，已移除连接: userId={}", userId);
        }
    }

    /**
     * 心跳保活：每 30 秒向所有在线连接发送 comment 事件，防止 Nginx/代理超时断连。
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        if (userConnections.isEmpty()) return;
        int ok = 0, fail = 0;
        for (Map.Entry<Long, SseEmitter> entry : userConnections.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().comment("ping"));
                ok++;
            } catch (Exception e) {
                userConnections.remove(entry.getKey(), entry.getValue());
                fail++;
            }
        }
        if (fail > 0) {
            log.debug("通知SSE心跳: ok={}, fail={}", ok, fail);
        }
    }

    public int getOnlineCount() {
        return userConnections.size();
    }
}
