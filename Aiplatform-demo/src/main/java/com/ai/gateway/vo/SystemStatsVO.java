package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统统计数据")
public class SystemStatsVO {

    @Schema(description = "总用户数")
    private Long totalUsers;
    @Schema(description = "活跃用户数")
    private Long activeUsers;
    @Schema(description = "API Key总数")
    private Long totalApiKeys;
    @Schema(description = "总调用次数")
    private Long totalCalls;
    @Schema(description = "总点数消耗")
    private BigDecimal totalPointsConsumed;
    @Schema(description = "今日调用次数")
    private Long todayCalls;
    @Schema(description = "今日点数消耗")
    private BigDecimal todayPointsConsumed;

    @Schema(description = "用户列表")
    private List<UserStatVO> users;
    @Schema(description = "每日用量")
    private List<Map<String, Object>> dailyUsage;
    @Schema(description = "模型用量")
    private List<Map<String, Object>> modelUsage;
    @Schema(description = "时段流量")
    private List<Map<String, Object>> hourlyTraffic;
    @Schema(description = "头部用户")
    private List<Map<String, Object>> topUsers;
    @Schema(description = "收入趋势")
    private List<Map<String, Object>> revenueTrend;
    @Schema(description = "用户增长")
    private List<Map<String, Object>> userGrowth;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "用户统计")
    public static class UserStatVO {
        @Schema(description = "用户ID")
        private Long id;
        @Schema(description = "用户名")
        private String username;
        @Schema(description = "邮箱")
        private String email;
        @Schema(description = "余额")
        private BigDecimal balance;
        @Schema(description = "角色")
        private String role;
        @Schema(description = "状态")
        private String status;
        @Schema(description = "API Key数量")
        private Integer apiKeyCount;
        @Schema(description = "总调用次数")
        private Long totalCalls;
        @Schema(description = "总点数消耗")
        private BigDecimal totalPointsConsumed;
        @Schema(description = "创建时间")
        private String createTime;
    }
}
