package com.ai.gateway.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsVO {

    private Long totalUsers;
    private Long activeUsers;
    private Long totalApiKeys;
    private Long totalCalls;
    private BigDecimal totalCost;
    private Long todayCalls;
    private BigDecimal todayCost;

    private List<UserStatVO> users;
    private List<Map<String, Object>> dailyUsage;
    private List<Map<String, Object>> modelUsage;
    private List<Map<String, Object>> hourlyTraffic;
    private List<Map<String, Object>> topUsers;
    private List<Map<String, Object>> revenueTrend;
    private List<Map<String, Object>> userGrowth;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStatVO {
        private Long id;
        private String username;
        private String email;
        private BigDecimal balance;
        private String role;
        private String status;
        private Integer apiKeyCount;
        private Long totalCalls;
        private BigDecimal totalCost;
        private String createTime;
    }
}
