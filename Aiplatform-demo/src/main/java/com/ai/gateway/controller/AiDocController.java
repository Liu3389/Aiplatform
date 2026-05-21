package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
     * @deprecated 已废弃，请使用 /v1/file/upload 批量上传接口
     * @see com.ai.gateway.controller.FileUploadController#uploadFiles
     */
    @Deprecated
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
     *
     * @deprecated 已废弃，请使用 /v1/file/parse-status/{fileId}
     */
    @Deprecated
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
     *
     * @deprecated 已废弃，请使用 DELETE /v1/file/{fileId}
     */
    @Deprecated
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
     *
     * @deprecated 已废弃，请使用新标准流程：先 /v1/file/upload 上传，再 /chat/stream-with-files 对话
     */
    @Deprecated
    @PostMapping("/chat")
    public Result<String> chatWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question,
            HttpServletRequest request) {
        // 修复 P3-5: 返回友好提示，引导用户使用新接口
        return Result.error("该接口已废弃，请使用 /chat/stream-with-files 接口");
    }
}
