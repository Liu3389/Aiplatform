package com.ai.gateway.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE连接管理服务 — 支持断线重连
 * 
 * 优化点：
 * 1. 限制最大并发连接数，防止内存溢出
 * 2. 定时清理超时未响应的连接
 * 3. 记录连接统计信息用于监控
 */
@Slf4j
@Service
public class SseConnectionManager {

    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, PendingResponse> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, Long> connectionTimestamps = new ConcurrentHashMap<>();
    
    private static final int MAX_CONNECTIONS = 500;
    private static final long CONNECTION_TIMEOUT_MILLIS = 120_000;
    
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalCompleted = new AtomicInteger(0);
    private final AtomicInteger totalTimeout = new AtomicInteger(0);

    public void registerConnection(String connectionId, SseEmitter emitter) {
        if (activeConnections.size() >= MAX_CONNECTIONS) {
            log.warn("SSE连接数已达上限: {}", MAX_CONNECTIONS);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", "服务器繁忙，请稍后重试")));
                emitter.complete();
            } catch (IOException e) {
                log.error("发送拒绝事件失败", e);
            }
            return;
        }
        
        activeConnections.put(connectionId, emitter);
        connectionTimestamps.put(connectionId, System.currentTimeMillis());
        totalCreated.incrementAndGet();
        
        emitter.onCompletion(() -> {
            activeConnections.remove(connectionId);
            pendingResponses.remove(connectionId);
            connectionTimestamps.remove(connectionId);
            totalCompleted.incrementAndGet();
            log.debug("SSE连接已关闭: connectionId={}, active={}", connectionId, activeConnections.size());
        });
        
        emitter.onTimeout(() -> {
            activeConnections.remove(connectionId);
            pendingResponses.remove(connectionId);
            connectionTimestamps.remove(connectionId);
            totalTimeout.incrementAndGet();
            log.warn("SSE连接超时: connectionId={}, active={}", connectionId, activeConnections.size());
        });
        
        emitter.onError((ex) -> {
            activeConnections.remove(connectionId);
            pendingResponses.remove(connectionId);
            connectionTimestamps.remove(connectionId);
            log.error("SSE连接错误: connectionId={}", connectionId, ex);
        });
        
        log.debug("SSE连接已注册: connectionId={}, active={}", connectionId, activeConnections.size());
    }

    public void savePendingResponse(String connectionId, PendingResponse response) {
        pendingResponses.put(connectionId, response);
    }

    public PendingResponse getPendingResponse(String connectionId) {
        return pendingResponses.get(connectionId);
    }

    public void clearPendingResponse(String connectionId) {
        pendingResponses.remove(connectionId);
    }

    public boolean isConnectionActive(String connectionId) {
        return activeConnections.containsKey(connectionId);
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public Map<String, Object> getStats() {
        return Map.of(
            "active", activeConnections.size(),
            "totalCreated", totalCreated.get(),
            "totalCompleted", totalCompleted.get(),
            "totalTimeout", totalTimeout.get()
        );
    }

    @Scheduled(fixedRate = 30000)
    public void cleanupStaleConnections() {
        long now = System.currentTimeMillis();
        activeConnections.forEach((id, emitter) -> {
            Long timestamp = connectionTimestamps.get(id);
            if (timestamp != null && now - timestamp > CONNECTION_TIMEOUT_MILLIS) {
                log.warn("清理超时SSE连接: connectionId={}, age={}ms", id, now - timestamp);
                closeConnection(id);
            }
        });
    }

    public void closeConnection(String connectionId) {
        SseEmitter emitter = activeConnections.remove(connectionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("关闭SSE连接失败: connectionId={}", connectionId, e);
            }
        }
        pendingResponses.remove(connectionId);
        connectionTimestamps.remove(connectionId);
    }

    @Data
    public static class PendingResponse {
        private String requestId;
        private Long userId;
        private String model;
        private StringBuilder accumulatedContent;
        private int inputTokens;
        private long startTime;
        
        public PendingResponse(String requestId, Long userId, String model) {
            this.requestId = requestId;
            this.userId = userId;
            this.model = model;
            this.accumulatedContent = new StringBuilder();
            this.inputTokens = 0;
            this.startTime = System.currentTimeMillis();
        }
        
        public void appendContent(String content) {
            if (content != null) {
                accumulatedContent.append(content);
            }
        }
        
        public String getContent() {
            return accumulatedContent.toString();
        }
    }
}
