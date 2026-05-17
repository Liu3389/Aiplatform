package com.ai.gateway.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.service.OpenAiChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocService {

    private final OssService ossService;
    private final OpenAiChatService openAiChatService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 解析内容缓存 key 前缀和 TTL（30分钟）
    private static final String PARSED_FILE_KEY_PREFIX = "ai-doc:parsed:";
    private static final long PARSED_FILE_TTL_MINUTES = 30;

    // 支持的文件类型与大小限制 (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = {".txt", ".md", ".java", ".py", ".cpp", ".xlsx", ".docx", ".html", ".css", ".js", ".xml", ".json"};

    /**
     * 1. 文件上传与校验
     */
    public String uploadAndValidate(MultipartFile file, Long userId) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件为空或超过10MB限制");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) throw new IllegalArgumentException("文件名无效");

        String suffix = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        boolean isAllowed = false;
        for (String ext : ALLOWED_EXTENSIONS) {
            if (ext.equals(suffix)) { isAllowed = true; break; }
        }
        if (!isAllowed) throw new IllegalArgumentException("不支持的文件格式: " + suffix);

        // 上传到 OSS: documents/{userId}/uuid.suffix
        return ossService.uploadFile(file, "documents/" + userId + "/");
    }

    /**
     * 1.5. 从字节数组上传并校验文件（解决 MultipartFile 流只能读一次的问题）
     */
    public String uploadAndValidateBytes(MultipartFile file, String fileName, byte[] fileBytes, Long userId) {
        if (fileBytes == null || fileBytes.length == 0 || fileBytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件为空或超过10MB限制");
        }

        if (fileName == null) throw new IllegalArgumentException("文件名无效");

        String suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        boolean isAllowed = false;
        for (String ext : ALLOWED_EXTENSIONS) {
            if (ext.equals(suffix)) { isAllowed = true; break; }
        }
        if (!isAllowed) throw new IllegalArgumentException("不支持的文件格式: " + suffix);

        // 上传到 OSS: documents/{userId}/uuid.suffix
        return ossService.uploadBytes(fileBytes, fileName, "documents/" + userId + "/");
    }

    /**
     * 2. 文件解析（提取纯文本）
     */
    public String parseFileToText(InputStream inputStream, String fileName) throws IOException {
        String suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        
        switch (suffix) {
            case ".txt":
            case ".md":
            case ".java":
            case ".py":
            case ".cpp":
            case ".html":
            case ".css":
            case ".js":
            case ".xml":
            case ".json":
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            
            case ".docx":
                XWPFDocument doc = new XWPFDocument(inputStream);
                StringBuilder text = new StringBuilder();
                for (XWPFParagraph para : doc.getParagraphs()) {
                    text.append(para.getText()).append("\n");
                }
                doc.close();
                return text.toString();

            case ".xlsx":
                Workbook workbook = WorkbookFactory.create(inputStream);
                StringBuilder excelText = new StringBuilder();
                Sheet sheet = workbook.getSheetAt(0); // 默认读取第一个 Sheet
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        excelText.append(cell.toString()).append("\t");
                    }
                    excelText.append("\n");
                }
                workbook.close();
                return excelText.toString();
                
            default:
                throw new IOException("无法解析的文件类型: " + suffix);
        }
    }

    /**
     * 3. AI 交互 (修复 P0-4: 走 OpenAiChatService 确保计费)
     */
    public String interactWithAi(String userQuestion, String fileContent, Long userId, String apiKey, String requestId) {
        String prompt = "你是一个智能文档助手。\n\n用户问题：" + userQuestion + "\n\n参考文档内容：\n" + fileContent + "\n\n请根据文档内容回答用户问题。";

        ChatRequest request = new ChatRequest();
        request.setModel("deepseek-v4-flash");
        ChatRequest.ChatMessage msg = new ChatRequest.ChatMessage();
        msg.setRole("user");
        msg.setContent(prompt);
        request.setMessages(List.of(msg));

        try {
            return openAiChatService.chat(userId, request);
        } catch (Exception e) {
            log.error("AI 交互失败", e);
            throw new RuntimeException("AI 响应失败: " + e.getMessage());
        }
    }

    /**
     * 4. 文件导出 (生成并返回字节数组)
     */
    public byte[] exportResult(String content, String format) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        switch (format.toLowerCase()) {
            case "md":
                outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                break;
            
            case "docx":
                XWPFDocument doc = new XWPFDocument();
                // 修复 P1-9: 将 Markdown 内容按行拆分存入段落，保持基本结构
                String[] lines = content.split("\n");
                for (String line : lines) {
                    doc.createParagraph().createRun().setText(line);
                }
                doc.write(outputStream);
                doc.close();
                break;

            case "xlsx":
                Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                Sheet sheet = workbook.createSheet("AI Result");
                String[] rows = content.split("\n");
                for (int i = 0; i < rows.length; i++) {
                    Row row = sheet.createRow(i);
                    row.createCell(0).setCellValue(rows[i]);
                }
                workbook.write(outputStream);
                workbook.close();
                break;
                
            default:
                throw new IllegalArgumentException("不支持的导出格式: " + format);
        }
        
        return outputStream.toByteArray();
    }

    /**
     * 5. 缓存解析后的文件内容到 Redis，生成 fileId
     */
    public String cacheParsedContent(Long userId, String fileName, String content) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String key = PARSED_FILE_KEY_PREFIX + userId + ":" + fileId;
        
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("fileId", fileId);
        cacheData.put("userId", userId);
        cacheData.put("fileName", fileName);
        cacheData.put("content", content);
        cacheData.put("status", "parsed");
        cacheData.put("createdAt", System.currentTimeMillis());
        
        redisTemplate.opsForValue().set(key, cacheData, PARSED_FILE_TTL_MINUTES, TimeUnit.MINUTES);
        log.info("文件解析内容已缓存: userId={}, fileId={}, fileName={}", userId, fileId, fileName);
        
        return fileId;
    }

    /**
     * 6. 从 Redis 获取已解析的文件内容
     */
    public String getParsedContent(Long userId, String fileId) {
        String key = PARSED_FILE_KEY_PREFIX + userId + ":" + fileId;
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached == null) {
            log.warn("文件内容不存在或已过期: userId={}, fileId={}", userId, fileId);
            return null;
        }
        
        try {
            if (cached instanceof Map) {
                return (String) ((Map<?, ?>) cached).get("content");
            }
            return null;
        } catch (Exception e) {
            log.error("获取解析内容失败: userId={}, fileId={}", userId, fileId, e);
            return null;
        }
    }

    /**
     * 7. 查询文件解析状态
     */
    public Map<String, Object> getParseStatus(Long userId, String fileId) {
        String key = PARSED_FILE_KEY_PREFIX + userId + ":" + fileId;
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached == null) {
            return null;
        }
        
        try {
            if (cached instanceof Map) {
                Map<?, ?> data = (Map<?, ?>) cached;
                Map<String, Object> status = new HashMap<>();
                status.put("fileId", data.get("fileId"));
                status.put("fileName", data.get("fileName"));
                status.put("status", data.get("status"));
                status.put("createdAt", data.get("createdAt"));
                return status;
            }
            return null;
        } catch (Exception e) {
            log.error("查询解析状态失败: userId={}, fileId={}", userId, fileId, e);
            return null;
        }
    }

    /**
     * 8. 删除已缓存的解析内容（用户取消或清理未使用的文件）
     */
    public boolean deleteCachedContent(Long userId, String fileId) {
        String key = PARSED_FILE_KEY_PREFIX + userId + ":" + fileId;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("已删除缓存的解析内容: userId={}, fileId={}", userId, fileId);
            return true;
        }
        log.warn("删除缓存内容失败或不存在: userId={}, fileId={}", userId, fileId);
        return false;
    }
}
