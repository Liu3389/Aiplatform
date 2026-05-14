package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点数明细账单实体
 */
@Data
@TableName("points_bill")
public class PointsBill implements Serializable {

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
     * 变动类型：RECHARGE-充值, DEDUCT-扣除, GRANT-发放, EXPIRE-过期清零, REFUND-退款
     */
    private String changeType;

    /**
     * 变动点数（正数为增加，负数为减少）
     */
    private BigDecimal pointsChange;

    /**
     * 变动前点数余额
     */
    private BigDecimal balanceBefore;

    /**
     * 变动后点数余额
     */
    private BigDecimal balanceAfter;

    /**
     * 关联业务ID（如对话ID、充值订单ID等）
     */
    private String businessId;

    /**
     * 业务描述
     */
    private String description;

    /**
     * 模型名称（如果是对话消耗）
     */
    private String modelName;

    /**
     * 有效期（过期清零时使用）
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 备注
     */
    private String remark;
}
