package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.service.AiDocService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/v1/ai-doc")
@RequiredArgsConstructor
public class AiDocController {

    private final AiDocService aiDocService;

    /**
     * 1. 上传文件并解析，与 AI 进行交互
     */
    @PostMapping("/chat")
    public Result<String> chatWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question,
            HttpServletRequest request) { 
        // 从拦截器获取 userId（修复 P0-2）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        
        try {
            // 步骤 1: 上传校验
            String ossUrl = aiDocService.uploadAndValidate(file, userId);
            
            // 步骤 2: 解析文件 (这里为了演示，重新从 OSS 下载流，实际可优化为直接处理上传流)
            // 注意：实际生产中建议直接在 uploadAndValidate 时返回 InputStream 或 byte[]
            // 此处简化逻辑，假设前端上传后我们立即处理
            
            // 由于 MultipartFile 只能读一次，我们在 Service 内部处理解析会更高效
            // 这里修改为：Service 接收 MultipartFile 直接解析
            String content = aiDocService.parseFileToText(file.getInputStream(), file.getOriginalFilename());
            
            // 步骤 3: AI 交互 (修复 P0-4: 传入计费所需参数)
            String apiKey = (String) request.getAttribute("apiKey");
            String requestId = (String) request.getAttribute("requestId");
            String aiResponse = aiDocService.interactWithAi(question, content, userId, apiKey, requestId);
            
            return Result.success(aiResponse);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 2. 导出 AI 结果到指定格式文件 (修复 P2-11: 改用 POST + Body)
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
}
