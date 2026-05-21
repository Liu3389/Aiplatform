package com.ai.gateway.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话列表VO（返回给前端）
 */
@Data
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String model;
    private Integer messageCount;
    private Long createTime;
    private Long lastMessageTime;
    private String previewContent;
}
