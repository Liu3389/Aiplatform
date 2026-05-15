package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日统计表实体类
 */
@Data
@TableName("daily_statistics")
public class DailyStatistics implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 统计日期
     */
    @TableField("stat_date")
    private LocalDate statDate;
    
    // 用户统计
    @TableField("new_users")
    private Integer newUsers;
    
    @TableField("total_users")
    private Integer totalUsers;
    
    @TableField("active_users")
    private Integer activeUsers;
    
    // 对话统计
    @TableField("new_conversations")
    private Integer newConversations;
    
    @TableField("total_conversations")
    private Integer totalConversations;
    
    @TableField("total_messages")
    private Integer totalMessages;
    
    @TableField("total_message_count")
    private Long totalMessageCount;
    
    // Token统计
    @TableField("total_input_tokens")
    private Long totalInputTokens;
    
    @TableField("total_output_tokens")
    private Long totalOutputTokens;
    
    @TableField("total_tokens")
    private Long totalTokens;
    
    // 点数统计
    @TableField("total_points_consumed")
    private BigDecimal totalPointsConsumed;
    
    // API调用统计
    @TableField("api_call_success")
    private Integer apiCallSuccess;
    
    @TableField("api_call_failed")
    private Integer apiCallFailed;
    
    @TableField("api_call_total")
    private Integer apiCallTotal;
    
    // 模型使用统计（JSON格式）
    @TableField("model_usage")
    private String modelUsage;
    
    // 时段分布（JSON格式）
    @TableField("hourly_distribution")
    private String hourlyDistribution;
    
    // 错误统计
    @TableField("error_count")
    private Integer errorCount;
    
    @TableField("timeout_count")
    private Integer timeoutCount;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
