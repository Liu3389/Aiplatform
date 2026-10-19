package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * 用户统计VO
 */
@Data
@Schema(description = "用户统计数据")
public class UserStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // 基础统计
    @Schema(description = "总用户数")
    private Integer totalUsers;           // 总用户数
    @Schema(description = "今日新增")
    private Integer todayNewUsers;        // 今日新增
    @Schema(description = "近7天活跃用户")
    private Integer activeUsers7d;        // 近7天活跃用户
    @Schema(description = "近30天未登录(流失用户)")
    private Integer inactiveUsers30d;     // 近30天未登录（流失用户）

    // 状态分布
    @Schema(description = "用户状态分布")
    private Map<String, Integer> statusDistribution;  // 用户状态分布

    // 分层统计
    @Schema(description = "用户分层")
    private UserSegmentVO userSegments;   // 用户分层

    /**
     * 用户分层
     */
    @Data
    @Schema(description = "用户分层")
    public static class UserSegmentVO implements Serializable {
        @Schema(description = "高频用户(近7天对话>10次)")
        private Integer highActive;    // 高频用户（近7天对话>10次）
        @Schema(description = "中频用户(近7天对话3-10次)")
        private Integer mediumActive;  // 中频用户（近7天对话3-10次）
        @Schema(description = "低频用户(近7天对话1-2次)")
        private Integer lowActive;     // 低频用户（近7天对话1-2次）
        @Schema(description = "未活跃用户(近7天无对话)")
        private Integer inactive;      // 未活跃用户（近7天无对话）
    }
}
