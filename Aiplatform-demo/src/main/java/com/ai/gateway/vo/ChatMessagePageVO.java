package com.ai.gateway.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天记录分页响应VO（游标分页）
 * 
 * @author AI Gateway Platform
 */
@Data
public class ChatMessagePageVO {

    /**
     * 消息列表
     */
    private List<ChatMessageVO> messages;

    /**
     * 是否有更多数据
     */
    private Boolean hasMore;

    /**
     * 下一页游标ID（用于继续上翻）
     */
    private Long nextCursorId;

    /**
     * 总消息数（可选，避免COUNT查询）
     */
    private Integer totalCount;

    /**
     * 消息VO
     */
    @Data
    public static class ChatMessageVO {
        /**
         * 消息ID
         */
        private Long id;

        /**
         * 角色：user/assistant/system
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 使用的模型
         */
        private String model;

        /**
         * Token数量
         */
        private Integer tokens;

        /**
         * 创建时间
         */
        private LocalDateTime createTime;
    }
}
