package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.entity.FileParseTask;
import com.ai.gateway.service.FileParseService;
import com.ai.gateway.service.OpenAiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天控制器 - 提供流式和非流式对话接口
 * 
 * @author AI Gateway Platform
 */
@Tag(name = "03-对话中心", description = "流式/非流式对话、内容导出")
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
    @Operation(summary = "导出对话内容为文件", description = "支持 md/txt/html/css/js/json/xml/csv/java/py 等，5秒冷却，不收费")
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
    @Operation(summary = "非流式对话", description = "一次性返回完整对话响应")
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
    @Operation(summary = "流式对话(SSE)", description = "SSE 流式响应，Knife4j 调试可能无法显示完整流，建议用 curl 测试")
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
     * - done: {"finished": true, "inputTokens": x, "outputTokens": y, "pointsConsumed": z}
     * - error: {"message": "错误信息"}
     */
    @Operation(summary = "带文件的流式对话(SSE)", description = "传 fileIds+question，SSE 流式返回，建议用 curl 测试")
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
        // 修复：前端传来的 conversationId 可能是 String 或 Number，统一处理避免 ClassCastException
        // 使用 final 变量以供 lambda 引用
        Long conversationIdTemp;
        Object convIdRaw = request.get("conversationId");
        if (convIdRaw != null) {
            if (convIdRaw instanceof Number) {
                conversationIdTemp = ((Number) convIdRaw).longValue();
            } else {
                try { conversationIdTemp = Long.valueOf(convIdRaw.toString()); } catch (NumberFormatException e) { conversationIdTemp = null; }
            }
        } else {
            conversationIdTemp = null;
        }
        final Long conversationId = conversationIdTemp;
        // 接收前端传来的文件元数据列表，透传到 ChatRequest.files，让 saveChatMessages 持久化到 metadata
        List<Map<String, Object>> filesMeta = (List<Map<String, Object>>) request.get("files");
        List<String> fileUrls = (List<String>) request.get("fileUrls");

        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        // 文件读取、解析和模型输出共用同一条流，超时与普通流式链路保持一致。
        // 过短的外层超时会在文件解析尚未完成时提前断开，前端只能看到“对话中断”。
        SseEmitter emitter = new SseEmitter(180000L);

        // 异步处理
        new Thread(() -> {
            try {
                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("你是一个智能文档助手。请根据提供的参考文档内容回答用户的问题。");
                List<Map<String, Object>> imageParts = new ArrayList<>();
                List<String> fileErrors = new ArrayList<>();
                int resolvedFileCount = 0;

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
                            FileParseTask task = fileParseService.getTaskForUser(fileId, userId);
                            if (isImageFileType(task.getFileType())) {
                                if (!"SUCCESS".equals(task.getParseStatus())) {
                                    throw new IllegalArgumentException("图片尚未准备完成，当前状态：" + task.getParseStatus());
                                }
                                Map<String, Object> imageUrl = new LinkedHashMap<>();
                                imageUrl.put("url", fileParseService.getAccessibleFileUrl(fileId, userId));
                                Map<String, Object> imagePart = new LinkedHashMap<>();
                                imagePart.put("type", "image_url");
                                imagePart.put("image_url", imageUrl);
                                imageParts.add(imagePart);
                            } else {
                                // 1. 检查文件是否已解析（复用机制）
                                String content = fileParseService.getParsedContent(fileId, userId);
                                promptBuilder.append("\n\n--- 参考文档 ---\n").append(content);
                            }

                            // 2. 标记文件为已使用
                            if (conversationId != null) {
                                fileParseService.markFileAsUsed(fileId, conversationId);
                            }

                            log.info("成功读取文件: fileId={}, reused=true", fileId);
                            resolvedFileCount++;
                        } catch (Exception e) {
                            log.warn("读取文件失败: fileId={}", fileId, e);
                            fileErrors.add(fileId + "：" + (e.getMessage() == null ? "文件读取失败" : e.getMessage()));
                        }
                    }

                    // 文件链路必须闭环：不能在文件读取失败时静默降级成纯文本对话，
                    // 否则用户会误以为模型看到了文件，且会进入后续扣点流程。
                    if (resolvedFileCount != fileIds.size()) {
                        String errorMessage = fileErrors.isEmpty()
                                ? "文件读取失败，请重新上传后再试"
                                : "文件处理失败，请重新上传后再试：" + String.join("；", fileErrors);
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(objectMapper.writeValueAsString(Map.of("message", errorMessage))));
                        emitter.complete();
                        return;
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
                Object idempotencyKey = request.get("idempotencyKey");
                if (idempotencyKey instanceof String key) {
                    chatRequest.setIdempotencyKey(key);
                }
                // 文件问答同样属于平台点数对话，避免旧接口漏传 mode 导致不扣点。
                chatRequest.setMode("paid");
                chatRequest.setConversationId(conversationId); // 修复: 透传conversationId
                // 透传文件元数据，让 saveChatMessages 把文件卡片持久化到消息的 metadata 字段
                if (filesMeta != null && !filesMeta.isEmpty()) {
                    chatRequest.setFiles(filesMeta);
                }
                if (fileUrls != null && !fileUrls.isEmpty()) {
                    chatRequest.setFileUrls(fileUrls);
                }
                // 带文件对话禁用联网搜索：用户是对文档提问，搜索会把拼接后的 prompt（含文档全文）
                // 误当作搜索词，文档里的"2026""最新"等词会命中时效性关键词触发搜索，搜回无关结果污染回答
                chatRequest.setDisableSearch(true);

                // 文件全文放入 system 消息给 AI 看；user 消息只存用户原始提问。
                // saveChatMessages 只持久化 user/assistant（不存 system），避免刷新后用户消息条
                // 暴露"参考文档"全文——前端只显示提问 + 文件卡片(metadata)
                List<ChatRequest.ChatMessage> messages = new java.util.ArrayList<>();
                ChatRequest.ChatMessage sysMsg = new ChatRequest.ChatMessage();
                sysMsg.setRole("system");
                sysMsg.setContent(promptBuilder.toString());
                messages.add(sysMsg);
                ChatRequest.ChatMessage userMsg = new ChatRequest.ChatMessage();
                userMsg.setRole("user");
                if (imageParts.isEmpty()) {
                    userMsg.setContent(question);
                } else {
                    List<Map<String, Object>> userContent = new ArrayList<>();
                    userContent.add(Map.of("type", "text", "text", question));
                    userContent.addAll(imageParts);
                    userMsg.setContent(userContent);
                }
                messages.add(userMsg);
                chatRequest.setMessages(messages);

                // 调用OpenAiChatService的流式对话
                // 修复：直接把外层 emitter 传给 streamChatWithEmitter，让 AI 的 message/done/error 事件
                // 直接发送到外层 emitter。之前用 streamChat() 返回的 aiEmitter + onCompletion/onError/onTimeout
                // 只转发状态回调，不转发 message/done 数据事件，导致 AI 回复丢失、前端永远卡在"思考中"
                openAiChatService.streamChatWithEmitter(userId, chatRequest, null, emitter);
                log.info("带文件对话已委托给 streamChatWithEmitter: userId={}", userId);

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

    private boolean isImageFileType(String fileType) {
        if (fileType == null) return false;
        return switch (fileType.toLowerCase()) {
            case "png", "jpg", "jpeg", "gif", "webp" -> true;
            default -> false;
        };
    }
}
