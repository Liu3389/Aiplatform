package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量发放点数请求 DTO
 */
@Data
@Schema(description = "批量发放点数请求")
public class BatchGrantPointsRequest {

    @Schema(description = "用户ID列表(1-1000)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID列表不能为空")
    @Size(min = 1, max = 1000, message = "批量操作用户数必须在 1-1000 之间")
    private List<Long> userIds;

    @Schema(description = "发放点数(大于0)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点数不能为空")
    @Positive(message = "发放点数必须大于0")
    private BigDecimal points;
}
