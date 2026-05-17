package com.ai.gateway.service;

import com.ai.gateway.dto.FileUploadResult;
import com.ai.gateway.dto.ParseStatusDTO;
import com.ai.gateway.entity.FileParseTask;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.FileParseTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 文件解析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseService {

    private final OssService ossService;
    private final FileParseTaskMapper taskMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiDocService aiDocService;
    private final com.ai.gateway.service.OpenAiChatService openAiChatService;

    // 支持的文件类型与大小限制 (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = {".txt", ".md", ".java", ".py", ".cpp", ".xlsx", ".docx", ".html", ".css", ".js", ".xml", ".json"};
    
    // 解析内容缓存 key 前缀和 TTL（30分钟）
    private static final String PARSED_FILE_KEY_PREFIX = "file:parsed:";
    private static final long PARSED_FILE_TTL_MINUTES = 30;

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

        // 6. 提交异步解析任务
        parseFileAsync(task.getId(), fileId, ossUrl, suffix);

        // 7. 构建返回结果
        return new FileUploadResult(
            fileId,
            originalName,
            file.getSize(),
            suffix.substring(1),
            "PENDING",
            System.currentTimeMillis()
        );
    }

    /**
     * 异步解析文件
     */
    @Async("fileParseExecutor")
    public void parseFileAsync(Long taskId, String fileId, String ossUrl, String fileType) {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // 1. 更新状态为PARSING
            taskMapper.updateStatusToParsing(taskId, startTime);
            log.info("开始解析文件: fileId={}, ossUrl={}", fileId, ossUrl);

            // 2. 从OSS下载文件
            byte[] fileBytes = ossService.downloadFile(ossUrl);

            // 3. 解析文件内容为文本
            String content = aiDocService.parseFileToText(
                new ByteArrayInputStream(fileBytes), 
                "temp" + fileType
            );

            // 4. 缓存到Redis（TTL 30分钟）
            String cacheKey = PARSED_FILE_KEY_PREFIX + fileId;
            redisTemplate.opsForValue().set(cacheKey, content, PARSED_FILE_TTL_MINUTES, TimeUnit.MINUTES);

            // 5. 生成文本摘要（AI生成，50-100字）
            String summary = generateTextSummary(content);

            // 6. 更新状态为SUCCESS（包含摘要）
            updateStatusWithSummary(taskId, content.length(), summary, LocalDateTime.now());
            log.info("文件解析成功: fileId={}, textLength={}, summaryLength={}", fileId, content.length(), summary != null ? summary.length() : 0);

        } catch (Exception e) {
            // 6. 更新状态为FAILED
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            taskMapper.updateStatusToFailed(taskId, errorMsg, LocalDateTime.now());
            log.error("文件解析失败: fileId={}", fileId, e);
        }
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
            throw new BusinessException("文件内容已过期，请重新上传");
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

    /**
     * 更新状态为SUCCESS（包含摘要）
     */
    private void updateStatusWithSummary(Long taskId, Integer textLength, String summary, LocalDateTime endTime) {
        try {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FileParseTask> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            wrapper.eq(FileParseTask::getId, taskId)
                   .set(FileParseTask::getParseStatus, "SUCCESS")
                   .set(FileParseTask::getParsedTextLength, textLength)
                   .set(FileParseTask::getTextSummary, summary)
                   .set(FileParseTask::getParseEndTime, endTime);
            
            taskMapper.update(null, wrapper);
        } catch (Exception e) {
            log.error("更新解析状态失败: taskId={}", taskId, e);
        }
    }
}
