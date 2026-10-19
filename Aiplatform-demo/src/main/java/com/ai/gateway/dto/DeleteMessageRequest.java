package com.ai.gateway.dto;

import lombok.Data;

/**
 * 删除单条消息请求
 * 通过 conversationId 路径参数 + role/content 定位消息（避免依赖前端临时ID）
 */
@Data
public class DeleteMessageRequest {
    /** 消息角色：user / assistant */
    private String role;
    /** 消息完整内容（精确匹配） */
    private String content;
}
