package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 会话文件分页响应VO
 */
@Data
@Schema(description = "会话文件分页响应")
public class ConversationFilePageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件列表
     */
    @Schema(description = "文件列表")
    private List<FileDetailVO> files;

    /**
     * 总数量
     */
    @Schema(description = "总数量")
    private Long total;

    /**
     * 当前页码
     */
    @Schema(description = "当前页码")
    private Integer page;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小")
    private Integer size;

    /**
     * 总页数
     */
    @Schema(description = "总页数")
    private Integer totalPages;

    /**
     * 是否有下一页
     */
    @Schema(description = "是否有下一页")
    private Boolean hasNext;
}
