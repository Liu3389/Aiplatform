package com.ai.gateway.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 用户统计VO
 */
@Data
public class UserStatsVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // 基础统计
    private Integer totalUsers;           // 总用户数
    private Integer todayNewUsers;        // 今日新增
    private Integer activeUsers7d;        // 近7天活跃用户
    private Integer inactiveUsers30d;     // 近30天未登录（流失用户）
    
    // 状态分布
    private Map<String, Integer> statusDistribution;  // 用户状态分布
    
    // 分层统计
    private UserSegmentVO userSegments;   // 用户分层
    
    /**
     * 用户分层
     */
    @Data
    public static class UserSegmentVO implements Serializable {
        private Integer highActive;    // 高频用户（近7天对话>10次）
        private Integer mediumActive;  // 中频用户（近7天对话3-10次）
        private Integer lowActive;     // 低频用户（近7天对话1-2次）
        private Integer inactive;      // 未活跃用户（近7天无对话）
    }
}
