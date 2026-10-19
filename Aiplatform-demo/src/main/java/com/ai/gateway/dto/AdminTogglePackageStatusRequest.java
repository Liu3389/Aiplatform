package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "套餐上下架请求")
public class AdminTogglePackageStatusRequest {
    @Schema(description = "套餐ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "套餐ID不能为空")
    private Long id;

    @Schema(description = "状态: 1上架 0下架", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态值不能为空")
    private Integer status;
}
