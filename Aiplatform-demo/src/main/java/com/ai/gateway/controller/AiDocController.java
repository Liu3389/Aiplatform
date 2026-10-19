package com.ai.gateway.controller;

import com.ai.gateway.service.AiDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Tag(name = "04-AI文档", description = "AI 结果导出")
@Slf4j
@RestController
@RequestMapping("/v1/ai-doc")
@RequiredArgsConstructor
public class AiDocController {

    private final AiDocService aiDocService;

    /**
     * 3. 导出 AI 结果到指定格式文件 (修复 P2-11: 改用 POST + Body)
     */
    @Operation(summary = "导出 AI 结果为文件", description = "传 content + format(md/txt/html 等)，返回二进制文件流")
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
