package com.ai.gateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminRevokeRoleRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
