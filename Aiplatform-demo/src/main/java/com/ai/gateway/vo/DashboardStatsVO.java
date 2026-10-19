package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘统计数据VO
 */
@Data
@Schema(description = "仪表盘统计数据")
public class DashboardStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // 核心数字卡片
    @Schema(description = "总注册用户数")
    private Long totalUsers;              // 总注册用户数
    @Schema(description = "今日新增用户数")
    private Integer todayNewUsers;        // 今日新增用户数
    @Schema(description = "总会话数")
    private Long totalConversations;      // 总会话数
    @Schema(description = "今日会话数")
    private Integer todayConversations;   // 今日会话数
    @Schema(description = "今日活跃用户数")
    private Integer todayActiveUsers;     // 今日活跃用户数
    @Schema(description = "系统运行状态: normal/error")
    private String systemStatus;          // 系统运行状态（normal/error）

    // 趋势数据（近7天/30天）
    @Schema(description = "用户新增趋势")
    private List<TrendDataVO> userTrend;       // 用户新增趋势
    @Schema(description = "对话量趋势")
    private List<TrendDataVO> conversationTrend; // 对话量趋势
    @Schema(description = "活跃用户趋势")
    private List<TrendDataVO> activeUserTrend;   // 活跃用户趋势

    // 快速统计
    @Schema(description = "快速统计")
    private QuickStatsVO quickStats;

    /**
     * 趋势数据
     */
    @Data
    @Schema(description = "趋势数据")
    public static class TrendDataVO implements Serializable {
        @Schema(description = "日期")
        private String date;        // 日期
        @Schema(description = "数值")
        private Long value;         // 数值
        @Schema(description = "增长率(可选)")
        private Double growthRate;  // 增长率（可选）
    }

    /**
     * 快速统计
     */
    @Data
    @Schema(description = "快速统计")
    public static class QuickStatsVO implements Serializable {
        @Schema(description = "总消息数")
        private Long totalMessages;           // 总消息数
        @Schema(description = "总点数消耗")
        private BigDecimal totalPointsConsumed; // 总点数消耗
        @Schema(description = "API成功率")
        private Double apiSuccessRate;        // API成功率
        @Schema(description = "平均会话长度")
        private Integer avgConversationLength; // 平均会话长度
    }
}
