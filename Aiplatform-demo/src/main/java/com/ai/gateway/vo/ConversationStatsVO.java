package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 对话统计VO
 */
@Data
@Schema(description = "对话统计数据")
public class ConversationStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // 总量统计
    @Schema(description = "总会话数")
    private Long totalConversations;     // 总会话数
    @Schema(description = "总消息数")
    private Long totalMessages;          // 总消息数
    @Schema(description = "平均每会话消息数")
    private Double avgMessagesPerConv;   // 平均每会话消息数
    @Schema(description = "平均单次对话时长(秒)")
    private Integer avgDuration;         // 平均单次对话时长（秒）

    // 今日统计
    @Schema(description = "今日会话数")
    private Integer todayConversations;  // 今日会话数
    @Schema(description = "今日消息数")
    private Integer todayMessages;       // 今日消息数

    // 模型调用统计
    @Schema(description = "模型使用统计")
    private ModelUsageStatsVO modelUsage; // 模型使用统计

    /**
     * 模型使用统计
     */
    @Data
    @Schema(description = "模型使用统计")
    public static class ModelUsageStatsVO implements Serializable {
        @Schema(description = "总调用次数")
        private Long totalCalls;              // 总调用次数
        @Schema(description = "今日调用次数")
        private Integer todayCalls;           // 今日调用次数
        @Schema(description = "成功率")
        private Double successRate;           // 成功率
        @Schema(description = "模型分布 {模型名: 调用次数}")
        private Map<String, Long> modelDistribution; // 模型分布 {模型名: 调用次数}
        @Schema(description = "热门模型排行")
        private List<ModelRankVO> topModels;  // 热门模型排行
    }

    /**
     * 模型排行
     */
    @Data
    @Schema(description = "模型排行")
    public static class ModelRankVO implements Serializable {
        @Schema(description = "模型名称")
        private String modelName;  // 模型名称
        @Schema(description = "调用次数")
        private Long callCount;    // 调用次数
        @Schema(description = "占比")
        private Double percentage; // 占比
    }
}
