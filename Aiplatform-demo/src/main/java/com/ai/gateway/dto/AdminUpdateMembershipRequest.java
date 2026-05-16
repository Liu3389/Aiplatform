package com.ai.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateMembershipRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "会员类型不能为空")
    private String membership;
}
