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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocService {

    private final OssService ossService;
    private final OpenAiChatService openAiChatService; // 引入计费服务
    private final ObjectMapper objectMapper = new ObjectMapper();

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
}
