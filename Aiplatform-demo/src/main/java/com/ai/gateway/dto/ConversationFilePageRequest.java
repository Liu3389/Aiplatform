package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话文件分页查询请求
 */
@Data
@Schema(description = "会话文件分页查询请求")
public class ConversationFilePageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /**
     * 页码（从1开始）
     */
    @Schema(description = "页码(从1开始)", example = "1")
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    /**
     * 每页大小（默认10，最大50）
     */
    @Schema(description = "每页大小(默认10)", example = "10")
    @Min(value = 1, message = "每页大小最小为1")
    private Integer size = 10;
}
