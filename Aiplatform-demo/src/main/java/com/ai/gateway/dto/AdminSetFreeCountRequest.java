package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "设置用户免费次数请求")
public class AdminSetFreeCountRequest {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "免费次数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "免费次数不能为空")
    private Integer count;
}
