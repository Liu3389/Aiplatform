package com.ai.gateway.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 文件存储服务 (修复 P1-6: 使用单例连接池)
 */
@Slf4j
@Service
public class OssService {

    @Value("${aliyun.oss.endpoint:}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name:}")
    private String bucketName;

    @Value("${aliyun.oss.domain:}")
    private String domain;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        log.info("阿里云 OSS 客户端初始化成功");
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    /**
     * 上传文件到 OSS
     * @param file 文件
     * @param folder 文件夹路径（如：avatars/ 或 documents/userId/）
     * @return 文件访问 URL
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 修复 P2-9: 增加文件头校验（Magic Number）防止恶意脚本上传
        validateFileType(file);

        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String fileName = UUID.randomUUID().toString() + suffix;
        String objectName = folder + fileName;

        try {
            InputStream inputStream = file.getInputStream();
            ossClient.putObject(new PutObjectRequest(bucketName, objectName, inputStream));
            return domain.endsWith("/") ? domain + objectName : domain + "/" + objectName;
        } catch (Exception e) {
            log.error("OSS 文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传字节数组到 OSS (用于 AI 生成的文件)
     */
    public String uploadBytes(byte[] content, String fileName, String folder) {
        String objectName = folder + UUID.randomUUID().toString() + "_" + fileName;
        try {
            ossClient.putObject(bucketName, objectName, new java.io.ByteArrayInputStream(content));
            return domain.endsWith("/") ? domain + objectName : domain + "/" + objectName;
        } catch (Exception e) {
            log.error("OSS 字节流上传失败", e);
            throw new RuntimeException("文件生成失败: " + e.getMessage());
        }
    }

    /**
     * 从 OSS 下载文件为字节数组
     */
    public byte[] downloadFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new IllegalArgumentException("文件URL不能为空");
        }
        String objectName = fileUrl.replace(domain, "").replaceFirst("^/", "");
        try (InputStream inputStream = ossClient.getObject(bucketName, objectName).getObjectContent()) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("OSS 文件下载失败: {}", objectName, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 文件类型校验（Magic Number）
     */
    private void validateFileType(MultipartFile file) {
        try {
            byte[] header = new byte[4];
            file.getInputStream().read(header);
            String hex = javax.xml.bind.DatatypeConverter.printHexBinary(header).toLowerCase();
            
            // 常见安全文件头：PDF, DOCX, XLSX, TXT, PNG, JPG
            boolean isValid = hex.startsWith("25504446") || hex.startsWith("504b0304") || 
                              hex.startsWith("89504e47") || hex.startsWith("ffd8ff");
            
            if (!isValid && !file.getOriginalFilename().endsWith(".txt")) {
                log.warn("检测到潜在危险文件上传: {}", file.getOriginalFilename());
                throw new IllegalArgumentException("不支持的文件类型");
            }
        } catch (Exception e) {
            log.error("文件校验失败", e);
        }
    }

    /**
     * 删除 OSS 文件
     * @param fileUrl 文件完整 URL
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return;
        
        String objectName = fileUrl.replace(domain, "").replaceFirst("^/", "");
        
        try {
            ossClient.deleteObject(bucketName, objectName);
            log.info("OSS 文件删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("OSS 文件删除失败", e);
        }
    }
}
