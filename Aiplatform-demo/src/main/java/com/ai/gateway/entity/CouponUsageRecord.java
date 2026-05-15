package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券使用记录实体
 */
@Data
@TableName("coupon_usage_record")
public class CouponUsageRecord implements Serializable {

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
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 优惠券码
     */
    private String couponCode;

    /**
     * 优惠券面额
     */
    private BigDecimal couponAmount;

    /**
     * 订单类型：RECHARGE-充值, SUBSCRIPTION-订阅, PACKAGE-套餐
     */
    private String orderType;

    /**
     * 关联订单ID（如充值订单号、订阅ID等）
     */
    private String orderId;

    /**
     * 订单原始金额
     */
    private BigDecimal orderAmount;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 实际支付金额
     */
    private BigDecimal actualAmount;

    /**
     * 使用时间
     */
    private LocalDateTime usageTime;

    /**
     * 备注
     */
    private String remark;
}
