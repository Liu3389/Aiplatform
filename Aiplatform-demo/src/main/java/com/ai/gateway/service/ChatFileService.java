package com.ai.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFileService {

    private final OssService ossService;
    private final ObjectMapper objectMapper = new ObjectMapper().configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_SELF_REFERENCES, false);

    /**
     * 1. 处理用户上传的文件：存入 OSS 并返回 URL
     */
    public String handleUserUpload(MultipartFile file, Long userId) {
        return ossService.uploadFile(file, "chat_uploads/user_" + userId + "/");
    }

    /**
     * 2. 处理 AI 生成的文件：将内容转为字节流存入 OSS
     */
    public String handleAiGeneratedFile(byte[] content, String fileName, Long userId) {
        return ossService.uploadBytes(content, fileName, "chat_generated/user_" + userId + "/");
    }

    /**
     * 3. 云端同步：打包对话数据及关联文件为 ZIP 上传至 OSS
     */
    public String syncConversationToOss(String conversationData, List<String> fileUrls, Long userId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 写入对话 JSON
            ZipEntry jsonEntry = new ZipEntry("conversation.json");
            zos.putNextEntry(jsonEntry);
            // 修复 P1-11: 显式指定 UTF-8 编码，防止中文乱码
            zos.write(conversationData.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 修复 P0-4: 从 OSS 下载关联文件并打入 ZIP
            if (fileUrls != null && !fileUrls.isEmpty()) {
                for (String url : fileUrls) {
                    try {
                        String fileName = url.substring(url.lastIndexOf("/") + 1);
                        ZipEntry fileEntry = new ZipEntry("files/" + fileName);
                        zos.putNextEntry(fileEntry);
                        
                        byte[] fileContent = ossService.downloadFile(url);
                        zos.write(fileContent);
                        zos.closeEntry();
                    } catch (Exception e) {
                        log.warn("打包文件失败: {}", url, e);
                    }
                }
            }

            zos.finish();
            return ossService.uploadBytes(baos.toByteArray(), "sync_" + System.currentTimeMillis() + ".zip", 
                                          "chat_syncs/user_" + userId + "/");
        } catch (Exception e) {
            log.error("对话同步失败", e);
            throw new RuntimeException("同步失败");
        }
    }
}
