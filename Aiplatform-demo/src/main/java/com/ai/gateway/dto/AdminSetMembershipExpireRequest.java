package com.ai.gateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminSetMembershipExpireRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "过期时间不能为空")
    private String expireTime;
}
