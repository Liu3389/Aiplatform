package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计费记录实体类
 * 
 * @author AI Gateway Platform
 */
@Data
@TableName("billing_record")
public class BillingRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 关联的调用日志ID
     */
    private Long callLogId;

    /**
     * 金额（美元）
     */
    private BigDecimal amount;

    /**
     * 类型：1-扣费，2-充值
     */
    private Integer type;

    /**
     * 操作前余额（美元）
     */
    private BigDecimal balanceBefore;

    /**
     * 操作后余额（美元）
     */
    private BigDecimal balanceAfter;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
