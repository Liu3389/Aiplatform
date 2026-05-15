package com.ai.gateway.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 对话统计VO
 */
@Data
public class ConversationStatsVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // 总量统计
    private Long totalConversations;     // 总会话数
    private Long totalMessages;          // 总消息数
    private Double avgMessagesPerConv;   // 平均每会话消息数
    private Integer avgDuration;         // 平均单次对话时长（秒）
    
    // 今日统计
    private Integer todayConversations;  // 今日会话数
    private Integer todayMessages;       // 今日消息数
    
    // 模型调用统计
    private ModelUsageStatsVO modelUsage; // 模型使用统计
    
    /**
     * 模型使用统计
     */
    @Data
    public static class ModelUsageStatsVO implements Serializable {
        private Long totalCalls;              // 总调用次数
        private Integer todayCalls;           // 今日调用次数
        private Double successRate;           // 成功率
        private Map<String, Long> modelDistribution; // 模型分布 {模型名: 调用次数}
        private List<ModelRankVO> topModels;  // 热门模型排行
    }
    
    /**
     * 模型排行
     */
    @Data
    public static class ModelRankVO implements Serializable {
        private String modelName;  // 模型名称
        private Long callCount;    // 调用次数
        private Double percentage; // 占比
    }
}
