package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.service.AiDocService;
import com.ai.gateway.service.BillingService;
import com.ai.gateway.service.ChatFileService;
import com.ai.gateway.service.OpenAiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = {".txt", ".md", ".java", ".py", ".cpp", ".xlsx", ".docx", ".html", ".css", ".js", ".xml", ".json"};
    private static final int MAX_REQUIREMENT_LENGTH = 5000;
    private static final int MAX_SYNC_DATA_SIZE = 5 * 1024 * 1024;
    private static final int MAX_EXPORT_CONTENT_SIZE = 1024 * 1024;

    private final OpenAiChatService openAiChatService;
    private final ChatFileService chatFileService;
    private final AiDocService aiDocService;
    private final com.ai.gateway.service.ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isAllowedExtension(String filename) {
        if (filename == null) return false;
        String suffix = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        for (String ext : ALLOWED_EXTENSIONS) {
            if (ext.equals(suffix)) return true;
        }
        return false;
    }

    /**
     * 对话中接收用户文件并调用 AI (返回 Markdown)
     */
    @PostMapping("/with-file")
    public Result<Map<String, Object>> chatWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        
        try {
            if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
                return Result.error("文件为空或超过10MB限制");
            }
            String originalName = file.getOriginalFilename();
            if (originalName == null || !isAllowedExtension(originalName)) {
                return Result.error("不支持的文件格式，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
            }
            if (question == null || question.trim().isEmpty()) {
                return Result.error("问题不能为空");
            }
            // 1. 上传文件到 OSS
            String fileUrl = chatFileService.handleUserUpload(file, userId);
            
            // 2. 解析文件内容
            String content = aiDocService.parseFileToText(file.getInputStream(), originalName);
            
            // 3. 构造 Prompt 并调用 AI (走 OpenAiChatService 确保计费)
            String prompt = "参考文档：\n" + content + "\n\n用户问题：" + question;
            ChatRequest request = new ChatRequest();
            request.setModel("deepseek-v4-flash");
            
            ChatRequest.ChatMessage msg = new ChatRequest.ChatMessage();
            msg.setRole("user");
            msg.setContent(prompt);
            request.setMessages(List.of(msg));
            
            // 修复 P2-16: 移除不再使用的 apiKey 和 requestId 参数
            String aiAnswer = openAiChatService.chat(userId, request);
            
            return Result.success(Map.of(
                "answer", aiAnswer,
                "uploadedFileUrl", fileUrl
            ));
        } catch (Exception e) {
            log.error("带文件对话失败", e);
            throw new RuntimeException("处理失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户需求让 AI 生成指定格式文件 (修复 P0-3)
     */
    @PostMapping("/generate-file")
    public Result<Map<String, Object>> generateFile(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String requirement = request.get("requirement");
        String format = request.get("format");

        if (requirement == null || requirement.trim().isEmpty()) {
            return Result.error("需求内容不能为空");
        }
        if (requirement.length() > MAX_REQUIREMENT_LENGTH) {
            return Result.error("需求内容过长，请控制在 " + MAX_REQUIREMENT_LENGTH + " 字符以内");
        }
        if (format == null || format.trim().isEmpty()) {
            return Result.error("文件格式不能为空");
        }

        // 1. 构造 Prompt 调用 AI (确保走 OpenAiChatService 以触发计费 P0-4)
        ChatRequest chatReq = new ChatRequest();
        chatReq.setModel("deepseek-v4-flash");
        ChatRequest.ChatMessage msg = new ChatRequest.ChatMessage();
        msg.setRole("user");
        msg.setContent("请根据以下需求生成 " + format + " 格式的内容：" + requirement);
        chatReq.setMessages(List.of(msg));

        // 修复 P2-16: 移除不再使用的 apiKey 和 requestId 参数
        try {
            String aiContent = openAiChatService.chat(userId, chatReq);
            
            // 2. 存入 OSS
            String generatedUrl = chatFileService.handleAiGeneratedFile(
                aiContent.getBytes(StandardCharsets.UTF_8), "generated." + format, userId
            );

            return Result.success(Map.of("downloadUrl", generatedUrl, "content", aiContent));
        } catch (Exception e) {
            throw new RuntimeException("AI 生成失败: " + e.getMessage());
        }
    }

    /**
     * 同步对话数据至阿里云 (ZIP 打包)
     */
    @PostMapping("/sync")
    public Result<Map<String, Object>> syncConversation(
            @RequestBody Map<String, Object> conversationData,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        try {
            String dataJson = objectMapper.writeValueAsString(conversationData);
            if (dataJson.length() > MAX_SYNC_DATA_SIZE) {
                return Result.error("同步数据过大，请控制在 " + (MAX_SYNC_DATA_SIZE / 1024 / 1024) + "MB 以内");
            }
            String zipUrl = chatFileService.syncConversationToOss(dataJson, null, userId);
            return Result.success(Map.of("syncUrl", zipUrl));
        } catch (Exception e) {
            throw new RuntimeException("同步失败: " + e.getMessage());
        }
    }

    /**
     * 导出对话中的代码块或内容为文件 (不收费)
     */
    @PostMapping("/export-content")
    public void exportContent(
            @RequestBody Map<String, String> request,
            HttpServletResponse response) throws IOException {
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

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + 
                           java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    /**
     * 加载对话时同步从阿里云加载关联文件
     */
    @GetMapping("/load")
    public Result<List<String>> loadConversationFiles(
            @RequestParam Long conversationId) {
        // 修复 P0-5: 通过 Service 层查询
        List<String> urls = chatMessageService.getFileUrlsByConversationId(conversationId);
        return Result.success(urls != null ? urls : new ArrayList<>());
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

        // 调用流式对话服务
        return openAiChatService.streamChat(userId, request);
    }
}
