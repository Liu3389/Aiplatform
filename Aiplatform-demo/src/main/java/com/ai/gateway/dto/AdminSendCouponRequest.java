package com.ai.gateway.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminSendCouponRequest {
    @NotNull(message = "优惠券金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠券金额必须大于0")
    private BigDecimal amount;

    @Size(min = 1, max = 128, message = "标题长度必须在 1-128 字符之间")
    private String title;

    @Size(max = 1024, message = "内容长度不能超过1024字符")
    private String content;

    private BigDecimal thresholdAmount;

    private String applicablePlan;

    private String expireTime;

    private String target;

    private String membership;

    private Integer days;
}
