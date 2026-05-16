package com.ai.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminToggleUserStatusRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "状态值不能为空")
    private String status;
}
