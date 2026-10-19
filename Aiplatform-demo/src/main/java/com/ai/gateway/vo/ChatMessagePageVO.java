package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天记录分页响应VO（游标分页）
 *
 * @author AI Gateway Platform
 */
@Data
@Schema(description = "聊天记录分页响应(游标分页)")
public class ChatMessagePageVO {

    /**
     * 消息列表
     */
    @Schema(description = "消息列表")
    private List<ChatMessageVO> messages;

    /**
     * 是否有更多数据
     */
    @Schema(description = "是否有更多数据")
    private Boolean hasMore;

    /**
     * 下一页游标ID（用于继续上翻）
     */
    @Schema(description = "下一页游标ID(用于继续上翻)")
    private Long nextCursorId;

    /** 下一页时间游标，跨热表与历史表分页时使用 */
    @Schema(description = "下一页时间游标(跨冷热表分页)")
    private LocalDateTime nextCursorTime;

    /**
     * 总消息数（可选，避免COUNT查询）
     */
    @Schema(description = "总消息数(可选,避免COUNT查询)")
    private Integer totalCount;

    /**
     * 消息VO
     */
    @Data
    @Schema(description = "消息详情")
    public static class ChatMessageVO {
        /**
         * 消息ID
         */
        @Schema(description = "消息ID")
        private Long id;

        /**
         * 角色：user/assistant/system
         */
        @Schema(description = "角色: user/assistant/system")
        private String role;

        /**
         * 消息内容
         */
        @Schema(description = "消息内容")
        private String content;

        /**
         * 使用的模型
         */
        @Schema(description = "使用的模型")
        private String model;

        /**
         * Token数量
         */
        @Schema(description = "Token数量")
        private Integer tokens;

        /**
         * 创建时间
         */
        @Schema(description = "创建时间")
        private LocalDateTime createTime;

        /**
         * 思考耗时（秒），仅 AI 消息有值
         */
        @Schema(description = "思考耗时（秒）")
        private Double thinkingTime;

        /**
         * 关联文件URL列表（JSON数组字符串，兼容旧数据）
         * 前端解析后用于显示文件卡片和下载链接
         */
        @Schema(description = "关联文件URL列表(JSON数组字符串)")
        private String fileUrls;

        /**
         * 元数据（JSON格式字符串，含文件详情：fileName/fileSize/fileType/ossUrl/uploadTime/expireTime）
         * 前端解析后恢复用户消息上方的文件卡片
         */
        @Schema(description = "元数据(JSON字符串,含文件详情)")
        private String metadata;
    }
}
