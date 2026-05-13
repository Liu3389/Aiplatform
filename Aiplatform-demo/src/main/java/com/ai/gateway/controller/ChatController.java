package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.service.BillingService;
import com.ai.gateway.service.OpenAiChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;

/**
 * 聊天控制器 - 提供流式和非流式对话接口
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OpenAiChatService openAiChatService;
    private final BillingService billingService;

    /**
     * 非流式对话接口
     * 
     * @param request 聊天请求
     * @param httpRequest HTTP请求（从拦截器获取用户信息）
     * @return 对话响应
     */
    @PostMapping("/completions")
    public Result<String> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        // 从拦截器获取用户信息
        Long userId = (Long) httpRequest.getAttribute("userId");
        String apiKey = (String) httpRequest.getAttribute("apiKey");
        String requestId = (String) httpRequest.getAttribute("requestId");

        try {
            // 调用OpenAI进行对话
            String response = openAiChatService.chat(apiKey, userId, request, requestId);
            
            return Result.success(response);
            
        } catch (Exception e) {
            log.error("对话失败: requestId={}", requestId, e);
            throw e;
        }
    }

    /**
     * 流式对话接口（SSE）
     * 
     * @param request 聊天请求
     * @param httpRequest HTTP请求（从拦截器获取用户信息）
     * @return SSE emitter
     */
    @PostMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        // 从拦截器获取用户信息
        Long userId = (Long) httpRequest.getAttribute("userId");
        String apiKey = (String) httpRequest.getAttribute("apiKey");
        String requestId = (String) httpRequest.getAttribute("requestId");

        log.info("开始流式对话: userId={}, requestId={}, model={}", userId, requestId, request.getModel());

        // 调用流式对话服务
        return openAiChatService.streamChat(apiKey, userId, request, requestId);
    }
}
