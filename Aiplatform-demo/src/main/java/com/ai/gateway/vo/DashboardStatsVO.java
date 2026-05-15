package com.ai.gateway.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计数据VO
 */
@Data
public class DashboardStatsVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // 核心数字卡片
    private Long totalUsers;              // 总注册用户数
    private Integer todayNewUsers;        // 今日新增用户数
    private Long totalConversations;      // 总会话数
    private Integer todayConversations;   // 今日会话数
    private Integer todayActiveUsers;     // 今日活跃用户数
    private String systemStatus;          // 系统运行状态（normal/error）
    
    // 趋势数据（近7天/30天）
    private List<TrendDataVO> userTrend;       // 用户新增趋势
    private List<TrendDataVO> conversationTrend; // 对话量趋势
    private List<TrendDataVO> activeUserTrend;   // 活跃用户趋势
    
    // 快速统计
    private QuickStatsVO quickStats;
    
    /**
     * 趋势数据
     */
    @Data
    public static class TrendDataVO implements Serializable {
        private String date;        // 日期
        private Long value;         // 数值
        private Double growthRate;  // 增长率（可选）
    }
    
    /**
     * 快速统计
     */
    @Data
    public static class QuickStatsVO implements Serializable {
        private Long totalMessages;           // 总消息数
        private BigDecimal totalPointsConsumed; // 总点数消耗
        private Double apiSuccessRate;        // API成功率
        private Integer avgConversationLength; // 平均会话长度
    }
}
