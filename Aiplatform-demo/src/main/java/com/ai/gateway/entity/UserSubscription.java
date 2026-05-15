package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户订阅记录实体
 */
@Data
@TableName("user_subscription")
public class UserSubscription implements Serializable {

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
     * 套餐模板ID
     */
    private Long packageId;

    /**
     * 套餐代码
     */
    private String packageCode;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 身份标识
     */
    private String identityLabel;

    /**
     * 授予点数
     */
    private BigDecimal pointsGranted;

    /**
     * 实际支付价格
     */
    private BigDecimal pricePaid;

    /**
     * 生效时间
     */
    private LocalDateTime startTime;

    /**
     * 过期时间（NULL表示永久）
     */
    private LocalDateTime expireTime;

    /**
     * 状态：ACTIVE-生效中，EXPIRED-已过期，CANCELLED-已取消
     */
    private String status;

    /**
     * 是否自动续费
     */
    private Boolean autoRenew;

    /**
     * 关联订单ID
     */
    private String orderId;

    /**
     * 使用的优惠券ID
     */
    private Long couponId;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
