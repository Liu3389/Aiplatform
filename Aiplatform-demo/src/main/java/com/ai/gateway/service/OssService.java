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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 文件存储服务（阿里云 OSS + 本地磁盘双模式）
 * <p>
 * 本地开发 / OSS 宕机时自动降级为本地磁盘存储，保证文件上传、解析、下载全流程可用。
 * - OSS 可用：文件上传到阿里云 OSS，返回 https 访问 URL
 * - OSS 不可用：文件保存到本地磁盘，返回 local:// 前缀的本地路径
 */
@Slf4j
@Service
public class OssService {

    // 本地文件路径前缀（与 OSS URL 区分）
    private static final String LOCAL_PREFIX = "local://";

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

    // 本地存储根目录（可通过 app.storage.local-path 配置覆盖）
    @Value("${app.storage.local-path:./uploads}")
    private String localStoragePath;

    // OSS 欠费或不可用时可显式切换为本地优先，避免每次上传都先等待 OSS 失败。
    @Value("${app.storage.prefer-local:true}")
    private boolean preferLocalStorage;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        // 始终创建本地存储目录（即使 OSS 可用，也作为降级备份）
        try {
            Files.createDirectories(Paths.get(localStoragePath));
        } catch (IOException e) {
            log.error("创建本地存储目录失败: {}", localStoragePath, e);
        }

        if (preferLocalStorage) {
            log.info("已启用本地文件存储优先模式: {}", localStoragePath);
            this.ossClient = null;
            return;
        }

        if (endpoint == null || endpoint.isEmpty() || accessKeyId == null || accessKeyId.isEmpty()) {
            log.warn("阿里云 OSS 未配置，文件存储将使用本地磁盘: {}", localStoragePath);
            this.ossClient = null;
            return;
        }
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
     * 上传文件（OSS 优先，失败自动降级本地磁盘）
     * @param file 文件
     * @param folder 文件夹路径（如：avatars/ 或 documents/userId/）
     * @return 文件访问 URL（OSS: https://...；本地: local://...）
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        validateFileType(file);

        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String fileName = UUID.randomUUID().toString() + suffix;
        String objectName = folder + fileName;

        if (ossClient != null) {
            try (InputStream inputStream = file.getInputStream()) {
                ossClient.putObject(new PutObjectRequest(bucketName, objectName, inputStream));
                return domain.endsWith("/") ? domain + objectName : domain + "/" + objectName;
            } catch (Exception e) {
                log.warn("OSS 文件上传失败，降级为本地存储: objectName={}, reason={}", objectName, e.getMessage());
            }
        }

        return saveToLocal(file, objectName);
    }

    /**
     * 上传字节数组（用于 AI 生成的文件，OSS 失败自动降级本地）
     */
    public String uploadBytes(byte[] content, String fileName, String folder) {
        String objectName = folder + UUID.randomUUID().toString() + "_" + fileName;
        if (ossClient != null) {
            try {
                ossClient.putObject(bucketName, objectName, new java.io.ByteArrayInputStream(content));
                return domain.endsWith("/") ? domain + objectName : domain + "/" + objectName;
            } catch (Exception e) {
                log.warn("OSS 字节流上传失败，降级为本地存储: objectName={}, reason={}", objectName, e.getMessage());
            }
        }
        return saveBytesToLocal(content, objectName);
    }

    /**
     * 从存储下载文件为字节数组（本地/OSS 自动识别）
     */
    public byte[] downloadFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new IllegalArgumentException("文件URL不能为空");
        }

        // 本地存储文件
        if (fileUrl.startsWith(LOCAL_PREFIX)) {
            String objectName = fileUrl.substring(LOCAL_PREFIX.length());
            try {
                return Files.readAllBytes(resolveLocalPath(objectName));
            } catch (IOException e) {
                log.error("本地文件读取失败: {}", objectName, e);
                throw new RuntimeException("文件下载失败: " + e.getMessage());
            }
        }

        // OSS 文件
        if (ossClient == null) {
            throw new RuntimeException("OSS 未配置且文件不是本地存储: " + fileUrl);
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
     * 删除文件（本地/OSS 自动识别）
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return;

        if (fileUrl.startsWith(LOCAL_PREFIX)) {
            String objectName = fileUrl.substring(LOCAL_PREFIX.length());
            try {
                Files.deleteIfExists(resolveLocalPath(objectName));
                log.info("本地文件删除成功: {}", objectName);
            } catch (IOException e) {
                log.error("本地文件删除失败: {}", objectName, e);
            }
            return;
        }

        if (ossClient == null) return;
        String objectName = fileUrl.replace(domain, "").replaceFirst("^/", "");
        try {
            ossClient.deleteObject(bucketName, objectName);
            log.info("OSS 文件删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("OSS 文件删除失败", e);
        }
    }

    /**
     * 将内部存储 URL 转换为前端可访问的 HTTP URL
     * <p>
     * - local://path → /api/uploads/path （本地存储文件，通过静态资源映射访问）
     * - OSS URL → 原样返回（已是公网 HTTP URL）
     * - null/空 → 原样返回
     *
     * @param url 内部存储 URL（数据库中存储的原始值）
     * @return 前端浏览器可直接访问的 HTTP URL
     */
    public String toAccessibleUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.startsWith(LOCAL_PREFIX)) {
            // local://documents/userId/xxx.pdf → /api/uploads/documents/userId/xxx.pdf
            return "/api/uploads/" + url.substring(LOCAL_PREFIX.length());
        }
        return url;
    }

    /**
     * 把前端可访问的本地文件 URL 还原为存储引用，供服务端读取并转成模型可识别的 data URL。
     * OSS URL 返回 null，让调用方继续使用原始远程地址。
     */
    public String toStorageReference(String accessibleUrl) {
        if (accessibleUrl == null || accessibleUrl.isBlank()) return null;
        if (accessibleUrl.startsWith(LOCAL_PREFIX)) return accessibleUrl;

        String path = accessibleUrl;
        try {
            String parsedPath = URI.create(accessibleUrl).getPath();
            if (parsedPath != null && !parsedPath.isBlank()) path = parsedPath;
        } catch (IllegalArgumentException ignored) {
            // 继续按相对路径处理
        }

        String[] markers = {"/api/uploads/", "/uploads/"};
        for (String marker : markers) {
            int markerIndex = path.indexOf(marker);
            if (markerIndex >= 0) {
                String objectName = path.substring(markerIndex + marker.length());
                if (!objectName.isBlank()) return LOCAL_PREFIX + objectName;
            }
        }
        return null;
    }

    /**
     * 保存 MultipartFile 到本地磁盘
     */
    private String saveToLocal(MultipartFile file, String objectName) {
        try {
            Path target = resolveLocalPath(objectName);
            Files.createDirectories(target.getParent());
            target = resolveLocalPath(objectName);
            try (InputStream inputStream = file.getInputStream()) {
                // The stream overload does not support NOFOLLOW_LINKS; its default CREATE_NEW behavior
                // also prevents a colliding name or existing symlink from being overwritten.
                Files.copy(inputStream, target);
            }
            log.info("文件已保存到本地: {}", target);
            return LOCAL_PREFIX + objectName;
        } catch (IOException e) {
            log.error("本地文件存储失败: {}", objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 保存字节数组到本地磁盘
     */
    private String saveBytesToLocal(byte[] content, String objectName) {
        try {
            Path target = resolveLocalPath(objectName);
            Files.createDirectories(target.getParent());
            target = resolveLocalPath(objectName);
            // Names are generated uniquely; CREATE_NEW rejects collisions and symlink targets.
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            log.info("字节内容已保存到本地: {}", target);
            return LOCAL_PREFIX + objectName;
        } catch (IOException e) {
            log.error("本地字节内容存储失败: {}", objectName, e);
            throw new RuntimeException("文件生成失败: " + e.getMessage());
        }
    }

    /**
     * 把本地对象路径限制在真实存储根目录内，并检查现存路径段的符号链接目标。
     */
    private Path resolveLocalPath(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("本地文件路径不能为空");
        }

        String relativeName = objectName.trim();
        if (relativeName.indexOf('\\') >= 0 || relativeName.startsWith("/") ||
                relativeName.matches("^[A-Za-z]:.*") || relativeName.matches("(?i).*%(2e|2f|5c).*") ||
                java.util.Arrays.stream(relativeName.split("/", -1)).anyMatch(".."::equals)) {
            throw new IllegalArgumentException("本地文件路径不合法");
        }

        Path relativePath;
        Path realRoot;
        try {
            relativePath = Paths.get(relativeName);
            if (relativePath.isAbsolute()) {
                throw new IllegalArgumentException("本地文件路径必须是相对路径");
            }
            realRoot = Paths.get(localStoragePath).toAbsolutePath().normalize().toRealPath();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("本地文件路径不合法", e);
        } catch (IOException e) {
            throw new IllegalStateException("本地存储根目录不可用", e);
        }

        Path candidate = realRoot.resolve(relativePath).normalize();
        if (!candidate.startsWith(realRoot)) {
            throw new IllegalArgumentException("本地文件路径超出存储目录");
        }

        Path existingAncestor = candidate;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            throw new IllegalArgumentException("本地文件路径不合法");
        }
        try {
            if (!existingAncestor.toRealPath().startsWith(realRoot)) {
                throw new IllegalArgumentException("本地文件路径超出存储目录");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("本地文件路径不合法", e);
        }
        return candidate;
    }

    /**
     * 文件类型校验（Magic Number）
     */
    private void validateFileType(MultipartFile file) {
        try {
            byte[] header = new byte[4];
            file.getInputStream().read(header);
            String hex = javax.xml.bind.DatatypeConverter.printHexBinary(header).toLowerCase();

            // 常见安全文件头：PDF, DOCX, XLSX, PNG, JPG
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
}
