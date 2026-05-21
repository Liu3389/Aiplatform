package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.service.FileParseService;
import com.ai.gateway.service.OpenAiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final int MAX_EXPORT_CONTENT_SIZE = 1024 * 1024;

    // 修复 P1-6: 导出接口速率限制（5秒冷却时间）
    private static final Map<Long, Long> EXPORT_RATE_LIMIT = new ConcurrentHashMap<>();
    private static final long EXPORT_COOLDOWN_MS = 5000;

    private final OpenAiChatService openAiChatService;
    private final FileParseService fileParseService;
    private final com.ai.gateway.service.ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 导出对话中的代码块或内容为文件 (不收费)
     * 支持格式: md, txt, html, css, js, java, py, cpp, json, xml, csv
     */
    @PostMapping("/export-content")
    public void exportContent(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) throws IOException {
        // 获取用户ID进行权限校验
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            response.sendError(401, "未授权访问");
            return;
        }

        // 修复 P1-6: 检查导出冷却时间
        Long lastExportTime = EXPORT_RATE_LIMIT.get(userId);
        if (lastExportTime != null && System.currentTimeMillis() - lastExportTime < EXPORT_COOLDOWN_MS) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"导出过于频繁，请5秒后再试\"}");
            return;
        }
        
        String content = request.get("content");
        String fileName = request.get("fileName");
        
        if (content == null || fileName == null) {
            response.sendError(400, "内容和文件名不能为空");
            return;
        }
        if (content.length() > MAX_EXPORT_CONTENT_SIZE) {
            response.sendError(400, "导出内容过大，请控制在 1MB 以内");
            return;
        }

        // 记录本次导出时间
        EXPORT_RATE_LIMIT.put(userId, System.currentTimeMillis());

        // 根据文件扩展名设置 Content-Type
        String contentType = getContentTypeByFileName(fileName);
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setCharacterEncoding("UTF-8");
        
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();

        log.info("用户导出文件: userId={}, fileName={}", userId, fileName);
    }

    /**
     * 根据文件名获取 Content-Type
     */
    private String getContentTypeByFileName(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return "text/markdown";
        } else if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return "text/html";
        } else if (lowerName.endsWith(".css")) {
            return "text/css";
        } else if (lowerName.endsWith(".js")) {
            return "application/javascript";
        } else if (lowerName.endsWith(".json")) {
            return "application/json";
        } else if (lowerName.endsWith(".xml")) {
            return "application/xml";
        } else if (lowerName.endsWith(".csv")) {
            return "text/csv";
        } else if (lowerName.endsWith(".java")) {
            return "text/x-java-source";
        } else if (lowerName.endsWith(".py")) {
            return "text/x-python";
        } else if (lowerName.endsWith(".txt")) {
            return "text/plain";
        } else {
            return "application/octet-stream";
        }
    }

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

        try {
            // 调用 AI 服务进行对话
            String response = openAiChatService.chat(userId, request);
            
            return Result.success(response);
            
        } catch (Exception e) {
            log.error("对话失败: userId={}", userId, e);
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

        log.info("开始流式对话: userId={}, model={}", userId, request.getModel());

        try {
            // 调用流式对话服务
            return openAiChatService.streamChat(userId, request);
        } catch (com.ai.gateway.exception.BusinessException e) {
            log.error("流式对话业务异常: userId={}", userId, e);
            SseEmitter emitter = new SseEmitter(1000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(Map.of("message", e.getMessage()))));
                emitter.complete();
            } catch (IOException ioEx) {
                log.error("发送错误事件失败", ioEx);
            }
            return emitter;
        } catch (Exception e) {
            log.error("流式对话系统异常: userId={}", userId, e);
            SseEmitter emitter = new SseEmitter(1000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(Map.of("message", "对话服务异常"))));
                emitter.complete();
            } catch (IOException ioEx) {
                log.error("发送错误事件失败", ioEx);
            }
            return emitter;
        }
    }

    /**
     * 带文件的流式对话接口（新标准流程）
     * <p>
     * 流程：
     * 1. 前端传入fileIds + question + model
     * 2. 后端读取已解析的文件内容
     * 3. 拼接prompt：问题 + 文件1内容 + 文件2内容 + ...
     * 4. 扣费并调用AI
     * 5. 流式返回AI回答
     * <p>
     * SSE事件：
     * - status: {"stage": "reading_files", "message": "正在读取文件内容..."}
     * - status: {"stage": "processing", "message": "正在与AI对话..."}
     * - message: {"content": "逐字推送的内容"}
     * - done: {"finished": true, "inputTokens": x, "outputTokens": y, "cost": z}
     * - error: {"message": "错误信息"}
     */
    @PostMapping(value = "/stream-with-files", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatWithFiles(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }

        String question = (String) request.get("question");
        String model = (String) request.getOrDefault("model", "deepseek-v4-flash");
        List<String> fileIds = (List<String>) request.get("fileIds");
        Long conversationId = request.get("conversationId") != null ?
                ((Number) request.get("conversationId")).longValue() : null;

        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        // 创建SSE emitter，超时时间120秒
        SseEmitter emitter = new SseEmitter(120000L);

        // 异步处理
        new Thread(() -> {
            try {
                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("你是一个智能文档助手。\n\n用户问题：").append(question);

                // 如果有文件，读取并拼接内容
                if (fileIds != null && !fileIds.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data(objectMapper.writeValueAsString(Map.of(
                                    "stage", "reading_files",
                                    "message", "正在读取文件内容..."
                            ))));

                    for (String fileId : fileIds) {
                        try {
                            // 1. 检查文件是否已解析（复用机制）
                            String content = fileParseService.getParsedContent(fileId, userId);

                            promptBuilder.append("\n\n--- 参考文档 ---\n").append(content);

                            // 2. 标记文件为已使用
                            if (conversationId != null) {
                                fileParseService.markFileAsUsed(fileId, conversationId);
                            }

                            log.info("成功读取文件: fileId={}, reused=true", fileId);
                        } catch (Exception e) {
                            log.warn("读取文件失败: fileId={}", fileId, e);
                        }
                    }
                }

                // 发送处理中状态
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(objectMapper.writeValueAsString(Map.of(
                                "stage", "processing",
                                "message", "正在与AI对话..."
                        ))));

                // 构造ChatRequest
                ChatRequest chatRequest = new ChatRequest();
                chatRequest.setModel(model);

                ChatRequest.ChatMessage msg = new ChatRequest.ChatMessage();
                msg.setRole("user");
                msg.setContent(promptBuilder.toString());
                chatRequest.setMessages(List.of(msg));

                // 调用OpenAiChatService的流式对话
                SseEmitter aiEmitter = openAiChatService.streamChat(userId, chatRequest);

                // 转发AI的SSE事件
                aiEmitter.onCompletion(() -> {
                    log.info("带文件对话完成: userId={}", userId);
                    emitter.complete();
                });

                aiEmitter.onError((ex) -> {
                    log.error("带文件对话出错: userId={}", userId, ex);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(objectMapper.writeValueAsString(Map.of(
                                        "message", ex.getMessage()
                                ))));
                    } catch (IOException e) {
                        log.error("发送错误事件失败", e);
                    }
                    emitter.completeWithError(ex);
                });

                aiEmitter.onTimeout(() -> {
                    log.warn("带文件对话超时: userId={}", userId);
                    emitter.complete();
                });

            } catch (Exception e) {
                log.error("带文件对话失败: userId={}", userId, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(Map.of(
                                    "message", e.getMessage()
                            ))));
                } catch (IOException ioException) {
                    log.error("发送错误事件失败", ioException);
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}
