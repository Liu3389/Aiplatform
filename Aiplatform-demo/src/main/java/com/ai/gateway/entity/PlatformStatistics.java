package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 平台统计数据实体（每日统计）
 */
@Data
@TableName("platform_statistics")
public class PlatformStatistics implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 统计日期
     */
    private LocalDate statDate;
    
    /**
     * 总用户数
     */
    private Integer totalUsers;
    
    /**
     * 新增用户数
     */
    private Integer newUsers;
    
    /**
     * 活跃用户数
     */
    private Integer activeUsers;
    
    /**
     * 留存用户数
     */
    private Integer retainedUsers;
    
    /**
     * 总对话次数
     */
    private Long totalCalls;
    
    /**
     * 总Token消耗
     */
    private Long totalTokens;
    
    /**
     * 总点数消耗
     */
    private BigDecimal totalPointsConsumed;
    
    /**
     * 总营收金额
     */
    private BigDecimal totalRevenue;
    
    /**
     * 充值金额
     */
    private BigDecimal rechargeAmount;
    
    /**
     * 套餐销量
     */
    private Integer packageSales;
    
    /**
     * 平均客单价
     */
    private BigDecimal avgOrderValue;
    
    /**
     * 各模型使用量（JSON格式）
     */
    private String modelUsage;
    
    /**
     * 小时流量分布（JSON格式）
     */
    private String hourlyTraffic;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
