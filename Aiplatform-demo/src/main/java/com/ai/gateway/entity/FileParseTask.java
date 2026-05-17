package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件解析任务实体
 */
@Data
@TableName("file_parse_task")
public class FileParseTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文件唯一ID（UUID）
     */
    private String fileId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * OSS存储地址
     */
    private String ossUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型（txt/md/docx/xlsx等）
     */
    private String fileType;

    /**
     * 解析状态：PENDING/PARSING/SUCCESS/FAILED
     */
    private String parseStatus;

    /**
     * 解析后文本长度
     */
    private Integer parsedTextLength;

    /**
     * 文本摘要（AI生成，50-100字）
     */
    private String textSummary;

    /**
     * 错误信息（解析失败时）
     */
    private String errorMessage;

    /**
     * 上传时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;

    /**
     * 开始解析时间
     */
    private LocalDateTime parseStartTime;

    /**
     * 解析完成时间
     */
    private LocalDateTime parseEndTime;

    /**
     * 关联会话ID（对话时使用）
     */
    private Long conversationId;

    /**
     * 是否已在对话中使用
     */
    private Boolean isUsed;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
