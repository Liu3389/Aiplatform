package com.ai.gateway.service;

import com.ai.gateway.dto.ConversationFilePageRequest;
import com.ai.gateway.entity.FileParseTask;
import com.ai.gateway.mapper.FileParseTaskMapper;
import com.ai.gateway.vo.ConversationFilePageVO;
import com.ai.gateway.vo.FileDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件解析任务服务
 */
@Slf4j
@Service
public class FileParseTaskService {

    private final FileParseTaskMapper fileParseTaskMapper;
    private final ChatMessageService chatMessageService;

    public FileParseTaskService(FileParseTaskMapper fileParseTaskMapper,
                                ChatMessageService chatMessageService) {
        this.fileParseTaskMapper = fileParseTaskMapper;
        this.chatMessageService = chatMessageService;
    }

    /**
     * 分页查询会话关联的文件详情
     *
     * @param userId  用户ID（用于权限校验）
     * @param request 分页请求
     * @return 文件分页结果
     */
    public ConversationFilePageVO getFileDetailsByConversationId(Long userId, ConversationFilePageRequest request) {
        Long conversationId = request.getConversationId();
        Integer page = request.getPage() != null ? request.getPage() : 1;
        Integer size = request.getSize() != null ? request.getSize() : 10;

        // 限制最大每页大小
        if (size > 50) {
            size = 50;
        }

        log.info("查询会话文件: userId={}, conversationId={}, page={}, size={}",
                userId, conversationId, page, size);

        // 1. 获取会话关联的所有fileId列表
        List<String> allFileIds = chatMessageService.getFileIdsByConversationId(conversationId);

        if (allFileIds.isEmpty()) {
            log.info("会话没有关联文件: conversationId={}", conversationId);
            return createEmptyPageVO(page, size);
        }

        // 2. 分页处理
        int total = allFileIds.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        if (fromIndex >= total) {
            log.warn("页码超出范围: page={}, total={}", page, total);
            return createEmptyPageVO(page, size);
        }

        List<String> pageFileIds = allFileIds.subList(fromIndex, toIndex);

        // 3. 批量查询文件详情
        LambdaQueryWrapper<FileParseTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FileParseTask::getFileId, pageFileIds)
                .eq(FileParseTask::getUserId, userId)  // 权限校验
                .orderByDesc(FileParseTask::getUploadTime);

        List<FileParseTask> tasks = fileParseTaskMapper.selectList(wrapper);

        // 4. 转换为VO
        List<FileDetailVO> fileDetails = tasks.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 5. 构建分页响应
        ConversationFilePageVO vo = new ConversationFilePageVO();
        vo.setFiles(fileDetails);
        vo.setTotal((long) total);
        vo.setPage(page);
        vo.setSize(size);
        vo.setTotalPages((int) Math.ceil((double) total / size));
        vo.setHasNext(toIndex < total);

        log.info("查询完成: total={}, pageCount={}, resultSize={}",
                total, vo.getTotalPages(), fileDetails.size());

        return vo;
    }

    /**
     * 转换实体为VO
     */
    private FileDetailVO convertToVO(FileParseTask task) {
        FileDetailVO vo = new FileDetailVO();
        vo.setFileId(task.getFileId());
        vo.setFileName(task.getOriginalName());
        vo.setFileType(task.getFileType());
        vo.setFileSize(task.getFileSize());
        vo.setParseStatus(task.getParseStatus());
        vo.setOssUrl(task.getOssUrl());
        vo.setTextSummary(task.getTextSummary());
        vo.setParsedTextLength(task.getParsedTextLength());
        vo.setUploadTime(task.getUploadTime());
        vo.setIsUsed(task.getIsUsed());
        return vo;
    }

    /**
     * 创建空的分页结果
     */
    private ConversationFilePageVO createEmptyPageVO(Integer page, Integer size) {
        ConversationFilePageVO vo = new ConversationFilePageVO();
        vo.setFiles(List.of());
        vo.setTotal(0L);
        vo.setPage(page);
        vo.setSize(size);
        vo.setTotalPages(0);
        vo.setHasNext(false);
        return vo;
    }
}
