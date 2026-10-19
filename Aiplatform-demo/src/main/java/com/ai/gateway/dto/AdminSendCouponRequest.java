package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "管理员发放优惠券请求")
public class AdminSendCouponRequest {
    @Schema(description = "优惠券金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "优惠券金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠券金额必须大于0")
    private BigDecimal amount;

    @Schema(description = "标题(1-128字符)")
    @Size(min = 1, max = 128, message = "标题长度必须在 1-128 字符之间")
    private String title;

    @Schema(description = "内容(最长1024字符)")
    @Size(max = 1024, message = "内容长度不能超过1024字符")
    private String content;

    @Schema(description = "使用门槛金额")
    private BigDecimal thresholdAmount;

    @Schema(description = "适用套餐")
    private String applicablePlan;

    @Schema(description = "过期时间")
    private String expireTime;

    @Schema(description = "发放目标: all/指定用户ID")
    private String target;

    @Schema(description = "会员等级限制")
    private String membership;

    @Schema(description = "有效天数")
    private Integer days;
}
