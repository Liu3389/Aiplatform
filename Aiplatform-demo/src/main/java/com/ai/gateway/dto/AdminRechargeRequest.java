package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "管理员充值点数请求")
public class AdminRechargeRequest {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "充值点数(0.001-1000000)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点数不能为空")
    @DecimalMin(value = "0.001", message = "充值点数必须大于0")
    @DecimalMax(value = "1000000", message = "单次充值点数不能超过 1,000,000 点")
    private BigDecimal points;
}
