package com.ai.gateway.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminAdjustPointsRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "点数不能为空")
    @DecimalMin(value = "-1000000", message = "点数调整幅度不能小于 -1,000,000")
    @DecimalMax(value = "1000000", message = "点数调整幅度不能超过 1,000,000")
    private BigDecimal points;

    private String reason;
}
