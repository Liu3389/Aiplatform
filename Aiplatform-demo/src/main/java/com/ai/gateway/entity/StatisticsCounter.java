package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实时计数器实体类
 */
@Data
@TableName("statistics_counter")
public class StatisticsCounter implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 计数器类型：USER_TOTAL/CONVERSATION_TOTAL/MESSAGE_TOTAL等
     */
    @TableField("counter_type")
    private String counterType;
    
    /**
     * 计数值
     */
    @TableField("counter_value")
    private Long counterValue;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
