package com.ai.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Service
public class AiDocService {

    /**
     * 文本类文件后缀集合 — 这些文件直接以 UTF-8 读取，无需特殊解析
     */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".markdown", ".text", ".log",
            ".yml", ".yaml", ".json", ".xml", ".csv", ".tsv",
            ".ini", ".conf", ".cfg", ".properties", ".toml", ".env",
            ".html", ".htm", ".css", ".scss", ".less",
            ".js", ".jsx", ".ts", ".tsx", ".mjs", ".vue", ".svelte",
            ".java", ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hxx",
            ".py", ".pyw", ".rb", ".go", ".rs", ".swift", ".kt", ".kts", ".scala",
            ".php", ".pl", ".pm", ".lua", ".sh", ".bash", ".zsh", ".bat", ".cmd", ".ps1",
            ".sql", ".r", ".m", ".jl", ".dart", ".groovy", ".gradle", ".cmake"
    );

    /**
     * 2. 文件解析（提取纯文本）
     * <p>
     * 采用 Apache Tika 开源解析器（支持 1000+ 文件格式自动检测与文本提取），
     * 替代手写 POI switch-case。覆盖 doc/docx/xls/xlsx/ppt/pptx/pdf/rtf/odt/epub 等。
     * <p>
     * 解析策略：
     * - 文本类（代码、配置、标记）：UTF-8 直接读取（性能更优，避免 Tika 开销）
     * - 二进制文档（Office/PDF/RTF 等）：Tika AutoDetectParser 自动识别并提取
     * - Tika 失败时：兜底 UTF-8 读取，避免流程中断
     */
    public String parseFileToText(InputStream inputStream, String fileName) throws IOException {
        String suffix = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf(".")).toLowerCase()
                : "";

        // 1. 文本类文件：直接 UTF-8 读取（更快且无损）
        if (TEXT_EXTENSIONS.contains(suffix)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 2. 二进制/富文档：交给 Apache Tika 自动检测与解析
        //    Tika 会读取流并尝试识别 MIME 类型，无需手工判断后缀
        Metadata metadata = new Metadata();
        if (!suffix.isEmpty()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        // BodyContentHandler 限制提取的最大字符数，防止超大文件 OOM（-1 表示无限制，这里给 5MB 文本上限）
        BodyContentHandler handler = new BodyContentHandler(5 * 1024 * 1024);
        ParseContext context = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();

        try {
            parser.parse(inputStream, handler, metadata, context);
            String text = handler.toString();
            return text == null ? "" : text;
        } catch (Exception e) {
            log.warn("Tika 解析失败 ({}), 降级为 UTF-8 读取: {}", suffix, e.getMessage());
            // Tika 失败时兜底 UTF-8 读取（注意流可能已被部分消费）
            return "[文件解析异常，以下为部分内容]\n" + fallbackUtf8(inputStream);
        } finally {
            try { inputStream.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * 兜底 UTF-8 读取：读取剩余字节并以 UTF-8 解码
     */
    private String fallbackUtf8(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("UTF-8 兜底读取失败", e);
            return "";
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
