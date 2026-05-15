package com.ai.gateway.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE连接管理服务 - 支持断线重连 (修复 P1-8: 当前为僵尸代码，待后续流式功能启用)
 */
@Slf4j
@Service
public class SseConnectionManager {

    // 存储活跃的SSE连接
    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();
    
    // 存储未完成的响应数据（用于重连后继续发送）
    private final Map<String, PendingResponse> pendingResponses = new ConcurrentHashMap<>();

    /**
     * 注册SSE连接
     * @param connectionId 连接ID
     * @param emitter SSE发射器
     */
    public void registerConnection(String connectionId, SseEmitter emitter) {
        activeConnections.put(connectionId, emitter);
        
        // 设置完成回调
        emitter.onCompletion(() -> {
            activeConnections.remove(connectionId);
            pendingResponses.remove(connectionId);
            log.info("SSE连接已关闭: connectionId={}", connectionId);
        });
        
        // 设置超时回调
        emitter.onTimeout(() -> {
            activeConnections.remove(connectionId);
            log.warn("SSE连接超时: connectionId={}", connectionId);
        });
        
        // 设置错误回调
        emitter.onError((ex) -> {
            activeConnections.remove(connectionId);
            log.error("SSE连接错误: connectionId={}", connectionId, ex);
        });
        
        log.info("SSE连接已注册: connectionId={}", connectionId);
    }

    /**
     * 保存未完成的响应数据
     * @param connectionId 连接ID
     * @param response 响应数据
     */
    public void savePendingResponse(String connectionId, PendingResponse response) {
        pendingResponses.put(connectionId, response);
    }

    /**
     * 获取未完成的响应数据
     * @param connectionId 连接ID
     * @return 未完成的响应数据
     */
    public PendingResponse getPendingResponse(String connectionId) {
        return pendingResponses.get(connectionId);
    }

    /**
     * 清除未完成的响应数据
     * @param connectionId 连接ID
     */
    public void clearPendingResponse(String connectionId) {
        pendingResponses.remove(connectionId);
    }

    /**
     * 检查连接是否活跃
     * @param connectionId 连接ID
     * @return 是否活跃
     */
    public boolean isConnectionActive(String connectionId) {
        return activeConnections.containsKey(connectionId);
    }

    /**
     * 获取活跃连接数
     * @return 活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    /**
     * 关闭指定连接
     * @param connectionId 连接ID
     */
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
    }

    /**
     * 待响应数据类
     */
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
