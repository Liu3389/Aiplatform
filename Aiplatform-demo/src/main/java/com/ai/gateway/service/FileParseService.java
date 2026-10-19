package com.ai.gateway.service;

import com.ai.gateway.dto.FileUploadResult;
import com.ai.gateway.dto.ParseStatusDTO;
import com.ai.gateway.entity.FileParseTask;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.FileParseTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 文件解析服务
 * <p>
 * 核心流程：
 * 1. 接收文件上传 → 立即返回fileId
 * 2. RabbitMQ 启用时投递持久化任务 ID；未启用时交给本地线程池
 * 3. 读取本地临时文件 → 解析内容
 * 4. 异步二次上传到OSS（IO解耦）
 * 5. 解析完成后存入Redis缓存（TTL 30分钟）
 * 6. 通过SSE推送解析完成状态给前端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseService {

    // 支持的文件类型与大小限制 (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    /**
     * 允许上传的文件扩展名白名单，作为文件上传链路的唯一校验入口
     */
    private static final String[] ALLOWED_EXTENSIONS = {
            // 纯文本 / 标记
            ".txt", ".md", ".markdown", ".text", ".log", ".rtf",
            // Office 文档
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".csv", ".tsv",
            // PDF / OpenDocument / 电子书（由 Apache Tika 统一解析）
            ".pdf", ".odt", ".ods", ".odp", ".epub",
            // 图片（上传后由视觉模型直接理解，不依赖文本解析结果）
            ".png", ".jpg", ".jpeg", ".gif", ".webp",
            // 代码 — C 系
            ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hxx",
            ".java", ".kt", ".kts", ".scala", ".dart", ".groovy",
            // 代码 — 脚本/其他
            ".py", ".pyw", ".rb", ".go", ".rs", ".swift",
            ".php", ".pl", ".pm", ".lua", ".sh", ".bash", ".zsh", ".bat", ".cmd", ".ps1",
            ".sql", ".r", ".m", ".jl", ".gradle", ".cmake",
            // Web 前端
            ".html", ".htm", ".css", ".scss", ".less",
            ".js", ".jsx", ".ts", ".tsx", ".mjs", ".vue", ".svelte",
            // 配置 / 数据
            ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".conf", ".cfg", ".properties", ".env"
    };
    // 解析内容缓存 key 前缀和 TTL（30分钟）
    private static final String PARSED_FILE_KEY_PREFIX = "file:parsed:";
    private static final long PARSED_FILE_TTL_MINUTES = 30;
    private static final int PARSE_LEASE_SECONDS = 300;
    private static final int MAX_PARSE_ATTEMPTS = 5;
    // SSE emitter 存储，用于推送解析进度
    private static final Map<String, List<SseEmitter>> EMITTER_MAP = new ConcurrentHashMap<>();
    private final OssService ossService;
    private final FileParseTaskMapper taskMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiDocService aiDocService;
    private final com.ai.gateway.service.OpenAiChatService openAiChatService;
    private final ObjectProvider<FileParseTaskDispatchService> taskDispatchServiceProvider;
    private final ObjectProvider<LocalFileParseTaskRecoveryService> localRecoveryServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, String> activeParseLeases = new ConcurrentHashMap<>();

    @Autowired
    @Qualifier("fileParseExecutor")
    private Executor fileParseExecutor;

    /**
     * 批量上传文件（最多8个）
     */
    public List<FileUploadResult> uploadFiles(List<MultipartFile> files, Long userId) {
        // 1. 校验文件数量
        if (files == null || files.isEmpty()) {
            throw new BusinessException("文件列表不能为空");
        }
        if (files.size() > 8) {
            throw new BusinessException("最多支持上传8个文件");
        }

        List<FileUploadResult> results = new ArrayList<>();

        // 2. 逐个处理文件
        for (MultipartFile file : files) {
            try {
                FileUploadResult result = processSingleFile(file, userId);
                results.add(result);
            } catch (Exception e) {
                log.error("文件上传失败: fileName={}", file.getOriginalFilename(), e);
                // 继续处理其他文件，不中断整个流程
            }
        }

        return results;
    }

    /**
     * 处理单个文件
     */
    private FileUploadResult processSingleFile(MultipartFile file, Long userId) {
        // 1. 校验文件大小
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件为空或超过10MB限制: " + file.getOriginalFilename());
        }

        // 2. 校验文件格式
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new BusinessException("文件名无效");
        }

        String suffix = getFileExtension(originalName);
        if (!isAllowedExtension(suffix)) {
            throw new BusinessException("不支持的文件格式: " + suffix + "，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // 3. 上传到OSS
        String ossUrl = ossService.uploadFile(file, "documents/" + userId + "/");

        // 4. 生成fileId
        String fileId = UUID.randomUUID().toString().replace("-", "");

        // 5. 创建解析任务记录
        FileParseTask task = new FileParseTask();
        task.setFileId(fileId);
        task.setUserId(userId);
        task.setOriginalName(originalName);
        task.setOssUrl(ossUrl);
        task.setFileSize(file.getSize());
        task.setFileType(suffix.substring(1)); // 去掉点号
        task.setParseStatus("PENDING");
        task.setIsUsed(false);
        taskMapper.insert(task);

        // 6. 先尝试可靠投递任务 ID；未启用或 Broker 不可用时退回有界线程池。
        // 任务行已先落库，消费者不信任消息里传来的文件路径或用户信息。
        FileParseTaskDispatchService dispatchService = taskDispatchServiceProvider.getIfAvailable();
        LocalFileParseTaskRecoveryService localRecoveryService = localRecoveryServiceProvider.getIfAvailable();
        boolean queued = dispatchService != null
                ? dispatchService.dispatchNow(task.getId())
                : localRecoveryService != null && localRecoveryService.dispatchNow(task.getId());
        if (!queued) {
            fileParseExecutor.execute(() -> processFileTask(task.getId()));
        }

        // 7. 构建返回结果（ossUrl 转换为前端可访问的 HTTP URL）
        return new FileUploadResult(
                fileId,
                originalName,
                file.getSize(),
                suffix.substring(1),
                "PENDING",
                System.currentTimeMillis(),
                ossService.toAccessibleUrl(ossUrl)
        );
    }

    /**
     * 异步解析文件
     * <p>
     * 流程：
     * 1. 更新状态为PARSING → 推送给前端
     * 2. 从OSS下载文件
     * 3. 解析文件内容为文本
     * 4. 缓存到Redis（TTL 30分钟）
     * 5. 生成文本摘要
     * 6. 更新状态为SUCCESS → 推送给前端
     */
    public void processFileTask(Long taskId) {
        LocalDateTime startTime = LocalDateTime.now();
        String fileId = String.valueOf(taskId); // 异常发生在加载任务前，也能在日志里定位任务 ID。
        String leaseToken = UUID.randomUUID().toString();

        try {
            LocalDateTime leaseUntil = startTime.plusSeconds(PARSE_LEASE_SECONDS);
            LocalDateTime legacyStaleBefore = startTime.minusSeconds(PARSE_LEASE_SECONDS);
            // 单条条件更新完成领取；Rabbit 重投、本地扫描与旧 worker 恢复之间只允许一个令牌有效。
            if (taskMapper.claimForParsing(taskId, leaseToken, startTime, leaseUntil,
                    legacyStaleBefore, MAX_PARSE_ATTEMPTS) != 1) {
                log.info("文件任务当前不可领取，跳过重复或未过期消息: taskId={}", taskId);
                return;
            }
            activeParseLeases.put(taskId, leaseToken);
            FileParseTask sourceTask = taskMapper.selectById(taskId);
            if (sourceTask == null) return;
            fileId = sourceTask.getFileId();
            String ossUrl = sourceTask.getOssUrl();
            String fileType = sourceTask.getFileType();
            log.info("开始解析文件: fileId={}, ossUrl={}", fileId, ossUrl);

            // 推送PARSING状态
            FileParseTask parsingTask = taskMapper.selectById(taskId);
            if (parsingTask != null) {
                notifyParseProgress(fileId, convertToDTO(parsingTask));
            }

            // 2. 从OSS下载文件
            byte[] fileBytes = ossService.downloadFile(ossUrl);

            // 3. 解析文件内容为文本
            String content = aiDocService.parseFileToText(
                    new ByteArrayInputStream(fileBytes),
                    "temp" + fileType
            );

            // 4. 先完成摘要计算；只有仍持有租约的 worker 才能写解析缓存。
            String cacheKey = PARSED_FILE_KEY_PREFIX + fileId;

            // 5. 生成文本摘要（AI生成，50-100字）
            String summary = generateTextSummary(content);

            // 6. 续租后写缓存，再由带 fencing token 的条件更新提交 SUCCESS。
            LocalDateTime endTime = LocalDateTime.now();
            if (!renewLeaseIfOwned(taskId, leaseToken, endTime)) {
                log.info("文件解析租约已失效，丢弃旧 worker 结果: taskId={}", taskId);
                return;
            }
            redisTemplate.opsForValue().set(cacheKey, content, PARSED_FILE_TTL_MINUTES, TimeUnit.MINUTES);
            int updated = taskMapper.updateStatusToSuccess(taskId, leaseToken, content.length(), summary, endTime);
            if (updated != 1) {
                log.info("文件解析租约已被其他 worker 接管，丢弃旧 worker 结果: taskId={}", taskId);
                return;
            }
            log.info("文件解析成功: fileId={}, textLength={}, summaryLength={}", fileId, content.length(), summary != null ? summary.length() : 0);

            // 推送SUCCESS状态
            FileParseTask successTask = taskMapper.selectById(taskId);
            if (successTask != null) {
                notifyParseProgress(fileId, convertToDTO(successTask));
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            int updated = 0;
            try {
                updated = taskMapper.updateStatusToFailed(taskId, leaseToken, errorMsg, LocalDateTime.now());
            } catch (Exception persistenceError) {
                log.error("文件解析失败状态写入异常，租约到期后恢复扫描器会重新接管: taskId={}", taskId,
                        persistenceError);
            }
            log.error("文件解析失败: fileId={}", fileId, e);

            // 推送FAILED状态
            if (updated == 1) {
                FileParseTask failedTask = taskMapper.selectById(taskId);
                if (failedTask != null) notifyParseProgress(fileId, convertToDTO(failedTask));
            }
        } finally {
            activeParseLeases.remove(taskId, leaseToken);
        }
    }

    /** 活跃解析期间续租；JVM 崩溃后租约自然到期，由 Rabbit 或本地扫描器恢复。 */
    @Scheduled(fixedDelayString = "${app.messaging.file-parse-lease-renew-ms:30000}")
    public void renewActiveParseLeases() {
        for (Map.Entry<Long, String> entry : activeParseLeases.entrySet()) {
            LocalDateTime now = LocalDateTime.now();
            int renewed;
            try {
                renewed = taskMapper.renewParsingLease(entry.getKey(), entry.getValue(), now,
                        now.plusSeconds(PARSE_LEASE_SECONDS));
            } catch (Exception e) {
                log.warn("文件解析租约续期失败，最终状态仍受 fencing token 保护: taskId={}", entry.getKey(), e);
                continue;
            }
            if (renewed != 1) {
                activeParseLeases.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean renewLeaseIfOwned(Long taskId, String leaseToken, LocalDateTime now) {
        return taskMapper.renewParsingLease(taskId, leaseToken, now,
                now.plusSeconds(PARSE_LEASE_SECONDS)) == 1;
    }

    /**
     * 查询解析状态
     */
    public ParseStatusDTO getParseStatus(String fileId, Long userId) {
        FileParseTask task = taskMapper.selectByFileIdAndUserId(fileId, userId);
        if (task == null) {
            throw new BusinessException("文件不存在或无权限访问");
        }

        return convertToDTO(task);
    }

    /**
     * 获取当前用户拥有的文件任务，供文件问答链路读取原始存储地址。
     */
    public FileParseTask getTaskForUser(String fileId, Long userId) {
        FileParseTask task = taskMapper.selectByFileIdAndUserId(fileId, userId);
        if (task == null) {
            throw new BusinessException("文件不存在或无权限访问");
        }
        return task;
    }

    /**
     * 返回前端可访问的文件地址；本地存储会转换成 /api/uploads/...。
     */
    public String getAccessibleFileUrl(String fileId, Long userId) {
        return ossService.toAccessibleUrl(getTaskForUser(fileId, userId).getOssUrl());
    }

    /**
     * 获取已解析的文件内容
     */
    public String getParsedContent(String fileId, Long userId) {
        // 1. 校验文件归属
        FileParseTask task = taskMapper.selectByFileIdAndUserId(fileId, userId);
        if (task == null) {
            throw new BusinessException("文件不存在或无权限访问");
        }

        // 2. 校验解析状态
        if (!"SUCCESS".equals(task.getParseStatus())) {
            throw new BusinessException("文件尚未解析成功，当前状态：" + task.getParseStatus());
        }

        // 3. 从Redis获取内容
        String cacheKey = PARSED_FILE_KEY_PREFIX + fileId;
        String content = (String) redisTemplate.opsForValue().get(cacheKey);

        if (content == null) {
            // 原始文件已经持久化到本地磁盘/OSS，Redis 只做加速缓存；缓存过期时自动重建，避免历史文件失效。
            try {
                byte[] fileBytes = ossService.downloadFile(task.getOssUrl());
                String fileType = task.getFileType() == null ? "txt" : task.getFileType();
                content = aiDocService.parseFileToText(
                        new ByteArrayInputStream(fileBytes), "restored." + fileType);
                redisTemplate.opsForValue().set(cacheKey, content, PARSED_FILE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("解析缓存重建失败: fileId={}", fileId, e);
                throw new BusinessException("文件内容读取失败，请重新上传");
            }
        }

        return content;
    }

    /**
     * 标记文件为已使用
     */
    public void markFileAsUsed(String fileId, Long conversationId) {
        taskMapper.markAsUsed(fileId, conversationId);
        log.info("文件标记为已使用: fileId={}, conversationId={}", fileId, conversationId);
    }

    /**
     * 删除未使用的文件（用户取消或清理）
     */
    public boolean deleteUnusedFile(String fileId, Long userId) {
        FileParseTask task = taskMapper.selectByFileIdAndUserId(fileId, userId);
        if (task == null) {
            return false;
        }

        // 只能删除未使用的文件
        if (Boolean.TRUE.equals(task.getIsUsed())) {
            throw new BusinessException("文件已在对话中使用，无法删除");
        }

        // 删除数据库记录
        taskMapper.deleteById(task.getId());

        // 删除Redis缓存
        String cacheKey = PARSED_FILE_KEY_PREFIX + fileId;
        redisTemplate.delete(cacheKey);

        log.info("删除未使用文件: fileId={}", fileId);
        return true;
    }

    /**
     * SSE流式监听文件解析进度
     * <p>
     * 前端订阅此接口后，当解析状态变化时会收到推送
     */
    public SseEmitter watchParseProgress(String fileId, Long userId) {
        // 校验文件归属
        FileParseTask task = taskMapper.selectByFileIdAndUserId(fileId, userId);
        if (task == null) {
            SseEmitter emitter = new SseEmitter(60000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(Map.of("message", "文件不存在或无权限访问"))));
                emitter.complete();
            } catch (IOException e) {
                log.error("发送错误事件失败", e);
            }
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        // 将emitter加入监听列表
        EMITTER_MAP.computeIfAbsent(fileId, k -> new ArrayList<>()).add(emitter);

        // 设置回调清理
        emitter.onCompletion(() -> {
            removeEmitter(fileId, emitter);
            log.debug("SSE连接完成: fileId={}", fileId);
        });

        emitter.onTimeout(() -> {
            removeEmitter(fileId, emitter);
            log.debug("SSE连接超时: fileId={}", fileId);
        });

        emitter.onError((ex) -> {
            removeEmitter(fileId, emitter);
            log.debug("SSE连接错误: fileId={}", fileId, ex);
        });

        // 立即发送当前状态
        try {
            ParseStatusDTO status = convertToDTO(task);
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(objectMapper.writeValueAsString(status)));

            // 如果已经解析成功，立即关闭连接
            if ("SUCCESS".equals(task.getParseStatus())) {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(objectMapper.writeValueAsString(Map.of("message", "解析已完成"))));
                emitter.complete();
            }
        } catch (IOException e) {
            log.error("发送初始状态失败", e);
            emitter.completeWithError(e);
        }

        log.info("开始监听文件解析进度: fileId={}, userId={}", fileId, userId);
        return emitter;
    }

    /**
     * 移除SSE emitter
     */
    private void removeEmitter(String fileId, SseEmitter emitter) {
        List<SseEmitter> emitters = EMITTER_MAP.get(fileId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                EMITTER_MAP.remove(fileId);
            }
        }
    }

    /**
     * 通知所有监听的客户端解析状态更新
     */
    private void notifyParseProgress(String fileId, ParseStatusDTO status) {
        List<SseEmitter> emitters = EMITTER_MAP.get(fileId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        try {
            String jsonData = objectMapper.writeValueAsString(status);
            List<SseEmitter> toRemove = new ArrayList<>();

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data(jsonData));

                    // 如果解析完成，发送done事件并关闭
                    if ("SUCCESS".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data(objectMapper.writeValueAsString(Map.of("message", "解析完成"))));
                        emitter.complete();
                        toRemove.add(emitter);
                    }
                } catch (IOException e) {
                    log.warn("发送SSE事件失败，移除emitter: fileId={}", fileId, e);
                    toRemove.add(emitter);
                }
            }

            // 清理失效的emitter
            emitters.removeAll(toRemove);
            if (emitters.isEmpty()) {
                EMITTER_MAP.remove(fileId);
            }
        } catch (Exception e) {
            log.error("通知解析进度失败: fileId={}", fileId, e);
        }
    }

    /**
     * 转换为DTO
     */
    private ParseStatusDTO convertToDTO(FileParseTask task) {
        ParseStatusDTO dto = new ParseStatusDTO();
        dto.setFileId(task.getFileId());
        dto.setFileName(task.getOriginalName());
        dto.setStatus(task.getParseStatus());
        dto.setParsedTextLength(task.getParsedTextLength());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setUploadTime(task.getUploadTime() != null ?
                task.getUploadTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        dto.setParseStartTime(task.getParseStartTime() != null ?
                task.getParseStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        dto.setParseEndTime(task.getParseEndTime() != null ?
                task.getParseEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        return dto;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex).toLowerCase();
    }

    /**
     * 检查是否为允许的文件类型
     */
    private boolean isAllowedExtension(String extension) {
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成文本摘要（AI生成，50-100字）
     */
    private String generateTextSummary(String content) {
        try {
            // 截取前2000字符作为摘要输入（避免token过多）
            String input = content.length() > 2000 ? content.substring(0, 2000) : content;

            // 调用AI生成摘要
            String prompt = "请用不超过80个字概括以下内容的核心要点：\n\n" + input;

            // 这里简化处理，实际应调用openAiChatService进行流式对话
            // 由于是后台任务，使用同步调用
            // TODO: 实现真正的AI摘要生成
            String summary = extractSimpleSummary(content);

            log.info("生成文本摘要成功: length={}", summary != null ? summary.length() : 0);
            return summary;

        } catch (Exception e) {
            log.warn("生成文本摘要失败，使用简单摘要", e);
            return extractSimpleSummary(content);
        }
    }

    /**
     * 提取简单摘要（截取前100字）
     */
    private String extractSimpleSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "无内容";
        }

        // 截取前100个字符
        int length = Math.min(100, content.length());
        String summary = content.substring(0, length).trim();

        // 如果超过50字，添加省略号
        if (content.length() > 100) {
            summary += "...";
        }

        return summary;
    }

}
