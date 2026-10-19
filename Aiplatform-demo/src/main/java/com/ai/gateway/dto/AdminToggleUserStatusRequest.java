package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "启用/禁用用户请求")
public class AdminToggleUserStatusRequest {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "状态: 1启用 0禁用", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "状态值不能为空")
    private String status;
}
