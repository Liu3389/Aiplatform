package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String couponCode;

    // 修复 P0-7: 统一金额为 3 位小数，与数据库 schema.sql 保持一致
    private BigDecimal amount;

    private BigDecimal thresholdAmount;

    private String applicablePlan;

    private LocalDateTime expireTime;

    private Boolean used;

    private LocalDateTime usedTime;

    private LocalDateTime createTime;
}
