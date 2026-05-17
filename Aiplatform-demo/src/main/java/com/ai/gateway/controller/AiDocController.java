package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.service.AiDocService;
import com.ai.gateway.service.OpenAiChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/ai-doc")
@RequiredArgsConstructor
public class AiDocController {

    private final AiDocService aiDocService;
    private final OpenAiChatService openAiChatService;

    /**
     * 1. 独立上传文件并解析（两步流程：先上传解析，后对话）
     * 
     * 流程：
     * 1. 用户上传文件，后端上传到 OSS 并解析为纯文本
     * 2. 解析结果缓存到 Redis，返回 fileId 和解析状态
     * 3. 前端轮询 /parse-status/{fileId} 等待解析完成
     * 4. 解析完成后，前端发送问题 + fileId 到 /chat/stream
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        
        try {
            String fileName = file.getOriginalFilename();
            long fileSize = file.getSize();
            
            // MultipartFile 的 InputStream 只能读一次，先读取为字节数组
            byte[] fileBytes = file.getBytes();
            
            // 上传并校验文件（从字节数组重新创建流）
            String ossUrl = aiDocService.uploadAndValidateBytes(file, fileName, fileBytes, userId);
            
            // 解析文件内容为纯文本（从字节数组创建新流）
            try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                String content = aiDocService.parseFileToText(is, fileName);
                
                // 生成 fileId 并缓存解析结果
                String fileId = aiDocService.cacheParsedContent(userId, fileName, content);
                
                // 返回 fileId 和文件信息
                Map<String, Object> result = Map.of(
                    "fileId", fileId,
                    "fileName", fileName != null ? fileName : "unknown",
                    "fileSize", fileSize,
                    "status", "parsed",
                    "ossUrl", ossUrl
                );
                
                return Result.success(result);
            }
        } catch (Exception e) {
            return Result.error("文件上传解析失败: " + e.getMessage());
        }
    }

    /**
     * 2. 查询文件解析状态
     */
    @GetMapping("/parse-status/{fileId}")
    public Result<Map<String, Object>> getParseStatus(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        
        try {
            Map<String, Object> status = aiDocService.getParseStatus(userId, fileId);
            if (status == null) {
                return Result.error("文件不存在或已过期");
            }
            return Result.success(status);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 2.5. 删除已缓存的解析文件（用户取消或清理）
     */
    @DeleteMapping("/upload/{fileId}")
    public Result<Void> deleteUploadedFile(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        
        try {
            boolean deleted = aiDocService.deleteCachedContent(userId, fileId);
            if (deleted) {
                return Result.success(null);
            }
            return Result.error("文件不存在或已删除");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 3. 导出 AI 结果到指定格式文件 (修复 P2-11: 改用 POST + Body)
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportResult(@RequestBody Map<String, String> request) {
        try {
            String content = request.get("content");
            String format = request.getOrDefault("format", "md");
            byte[] data = aiDocService.exportResult(content, format);
            
            HttpHeaders headers = new HttpHeaders();
            String fileName = "ai_result." + format;
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", 
                URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 4. 上传文件并解析，与 AI 进行交互（非流式 - 旧接口保留兼容）
     */
    @PostMapping("/chat")
    public Result<String> chatWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question,
            HttpServletRequest request) { 
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        
        try {
            byte[] fileBytes = file.getBytes();
            String fileName = file.getOriginalFilename();
            aiDocService.uploadAndValidateBytes(file, fileName, fileBytes, userId);
            String content = aiDocService.parseFileToText(new ByteArrayInputStream(fileBytes), fileName);
            String apiKey = (String) request.getAttribute("apiKey");
            String requestId = (String) request.getAttribute("requestId");
            String aiResponse = aiDocService.interactWithAi(question, content, userId, apiKey, requestId);
            
            return Result.success(aiResponse);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 5. 基于已解析文件进行流式 AI 对话（新两步流程）
     * 
     * 流程：
     * 1. 前端传入 fileId + question
     * 2. 后端从 Redis 获取已解析的文件内容
     * 3. 调用 OpenAiChatService.streamChat 进行真正的流式对话
     * 
     * SSE 事件：
     * - status: {"stage": "ready", "message": "正在与AI对话..."}
     * - message: {"content": "逐字推送的内容"}  ← 真正的流式
     * - done: {"finished": true, "inputTokens": x, "outputTokens": y, "cost": z}
     * - error: {"message": "错误信息"}
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithFileStream(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        
        String fileId = request.get("fileId");
        String question = request.get("question");
        
        if (fileId == null || fileId.isEmpty()) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        if (question == null || question.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        
        // 从缓存获取已解析的文件内容
        String content = aiDocService.getParsedContent(userId, fileId);
        if (content == null) {
            throw new RuntimeException("文件内容不存在或已过期，请重新上传");
        }
        
        // 构造 ChatRequest
        String prompt = "你是一个智能文档助手。\n\n用户问题：" + question + "\n\n参考文档内容：\n" + content + "\n\n请根据文档内容回答用户问题。";
        
        com.ai.gateway.dto.ChatRequest chatRequest = new com.ai.gateway.dto.ChatRequest();
        chatRequest.setModel("deepseek-v4-flash");
        chatRequest.setMode("paid");  // 默认付费模式
        
        com.ai.gateway.dto.ChatRequest.ChatMessage msg = new com.ai.gateway.dto.ChatRequest.ChatMessage();
        msg.setRole("user");
        msg.setContent(prompt);
        chatRequest.setMessages(List.of(msg));
        
        // 直接调用 OpenAiChatService 的流式方法
        log.info("开始文件流式对话: userId={}, fileId={}", userId, fileId);
        return openAiChatService.streamChat(userId, chatRequest);
    }
}
