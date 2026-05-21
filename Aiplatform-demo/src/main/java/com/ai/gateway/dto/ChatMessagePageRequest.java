package com.ai.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /**
     * 游标ID（上一页最后一条记录的ID，首次查询不传）
     */
    private Long lastId;

    /**
     * 每页条数（默认10）
     */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Integer limit = 10;
}
