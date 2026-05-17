package com.ai.gateway.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 会话文件分页响应VO
 */
@Data
public class ConversationFilePageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件列表
     */
    private List<FileDetailVO> files;

    /**
     * 总数量
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 总页数
     */
    private Integer totalPages;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;
}
