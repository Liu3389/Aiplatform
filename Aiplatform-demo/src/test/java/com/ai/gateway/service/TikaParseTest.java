package com.ai.gateway.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Apache Tika 集成冒烟测试：验证 Tika 能在运行时自动检测并提取富文档文本。
 * 不依赖 Spring 上下文，纯 Tika 调用。
 */
class TikaParseTest {

    @Test
    void parseRtf_returnsExtractedText() throws Exception {
        // 一段最小 RTF（二进制富文档，走 Tika 路径而非 UTF-8 直读）
        // 注：RTF 中文需用 Unicode 转义序列，这里仅验证英文提取；中文提取能力由 parseHtml 测试覆盖
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff0 "
                + "{\\fonttbl{\\f0 Arial;}}"
                + "\\f0 Hello from Apache Tika integration. RTF parse works.}";

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "sample.rtf");
        BodyContentHandler handler = new BodyContentHandler(5 * 1024 * 1024);
        AutoDetectParser parser = new AutoDetectParser();

        parser.parse(new ByteArrayInputStream(rtf.getBytes()), handler, metadata, new ParseContext());

        String text = handler.toString();
        System.out.println("=== TIKA PARSED RTF TEXT ===");
        System.out.println(text);
        System.out.println("=== DETECTED CONTENT TYPE: " + metadata.get("Content-Type") + " ===");

        assertTrue(text.contains("Hello from Apache Tika integration"),
                "Tika 应能从 RTF 提取文本，实际: " + text);
    }

    @Test
    void parseHtml_returnsExtractedText() throws Exception {
        // HTML 也走 Tika 路径（.html 不在 TEXT_EXTENSIONS 白名单中）
        String html = "<html><head><title>Doc</title></head>"
                + "<body><h1>季度报告</h1><p>营收增长 23%。</p></body></html>";

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "report.html");
        BodyContentHandler handler = new BodyContentHandler(5 * 1024 * 1024);
        AutoDetectParser parser = new AutoDetectParser();

        parser.parse(new ByteArrayInputStream(html.getBytes()), handler, metadata, new ParseContext());

        String text = handler.toString();
        System.out.println("=== TIKA PARSED HTML TEXT ===");
        System.out.println(text);

        assertTrue(text.contains("季度报告"), "Tika 应能从 HTML 提取标题");
        assertTrue(text.contains("营收增长"), "Tika 应能从 HTML 提取正文");
    }

    @Test
    void parseDocx_returnsText() throws Exception {
        // 用 POI 生成一个最小 .docx（zip 容器），再用 Tika 解析
        // 复现并验证 commons-compress 版本冲突修复：旧版 1.21 会抛 NoSuchMethodError
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Hello DOCX from Tika. 季度报告测试。");
            doc.write(baos);
        }
        byte[] docxBytes = baos.toByteArray();

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.docx");
        BodyContentHandler handler = new BodyContentHandler(5 * 1024 * 1024);
        AutoDetectParser parser = new AutoDetectParser();

        parser.parse(new ByteArrayInputStream(docxBytes), handler, metadata, new ParseContext());

        String text = handler.toString();
        System.out.println("=== TIKA PARSED DOCX TEXT ===");
        System.out.println(text);
        System.out.println("=== DETECTED: " + metadata.get("Content-Type") + " ===");

        assertTrue(text.contains("Hello DOCX from Tika"), "Tika 应能解析 docx 英文，实际: " + text);
        assertTrue(text.contains("季度报告测试"), "Tika 应能解析 docx 中文，实际: " + text);
    }
}
