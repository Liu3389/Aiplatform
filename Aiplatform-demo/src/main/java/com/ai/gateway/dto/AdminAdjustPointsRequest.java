package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "调整用户点数请求(正数为增加,负数为扣减)")
public class AdminAdjustPointsRequest {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "点数调整幅度(-1000000~1000000,正增负减)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点数不能为空")
    @DecimalMin(value = "-1000000", message = "点数调整幅度不能小于 -1,000,000")
    @DecimalMax(value = "1000000", message = "点数调整幅度不能超过 1,000,000")
    private BigDecimal points;

    @Schema(description = "调整原因")
    private String reason;
}
