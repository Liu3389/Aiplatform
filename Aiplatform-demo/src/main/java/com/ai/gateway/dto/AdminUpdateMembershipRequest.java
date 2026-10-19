package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新用户会员等级请求")
public class AdminUpdateMembershipRequest {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "会员类型: free/silver/gold/diamond", example = "gold", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员类型不能为空")
    private String membership;
}
