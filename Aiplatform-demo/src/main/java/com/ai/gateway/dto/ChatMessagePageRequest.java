package com.ai.gateway.dto;

import lombok.Data;

/**
 * 聊天记录分页请求DTO（游标分页）
 * 
 * @author AI Gateway Platform
 */
@Data
public class ChatMessagePageRequest {

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 游标ID（上一页最后一条记录的ID，首次查询不传）
     */
    private Long lastId;

    /**
     * 每页条数（默认10）
     */
    private Integer limit = 10;
}
