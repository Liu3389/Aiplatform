package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件详情VO（用于会话历史消息关联文件展示）
 */
@Data
@Schema(description = "文件详情")
public class FileDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private String fileId;

    /**
     * 原始文件名
     */
    @Schema(description = "原始文件名")
    private String fileName;

    /**
     * 文件类型（txt/md/docx/xlsx/pdf等）
     */
    @Schema(description = "文件类型: txt/md/docx/xlsx/pdf等")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    /**
     * 解析状态：PENDING/PARSING/SUCCESS/FAILED
     */
    @Schema(description = "解析状态: PENDING/PARSING/SUCCESS/FAILED")
    private String parseStatus;

    /**
     * OSS访问地址
     */
    @Schema(description = "OSS访问地址")
    private String ossUrl;

    /**
     * 文本摘要（AI生成，50-100字）
     */
    @Schema(description = "文本摘要(AI生成,50-100字)")
    private String textSummary;

    /**
     * 解析后文本长度
     */
    @Schema(description = "解析后文本长度")
    private Integer parsedTextLength;

    /**
     * 上传时间
     */
    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    /**
     * 是否已在对话中使用
     */
    @Schema(description = "是否已在对话中使用")
    private Boolean isUsed;
}
