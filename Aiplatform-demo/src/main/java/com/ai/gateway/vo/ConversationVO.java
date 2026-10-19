package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话列表VO（返回给前端）
 */
@Data
@Schema(description = "会话列表")
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID")
    private Long id;
    @Schema(description = "会话标题")
    private String title;
    @Schema(description = "使用的模型")
    private String model;
    @Schema(description = "消息数")
    private Integer messageCount;
    @Schema(description = "创建时间(时间戳)")
    private Long createTime;
    @Schema(description = "最后消息时间(时间戳)")
    private Long lastMessageTime;
    @Schema(description = "预览内容")
    private String previewContent;
}
