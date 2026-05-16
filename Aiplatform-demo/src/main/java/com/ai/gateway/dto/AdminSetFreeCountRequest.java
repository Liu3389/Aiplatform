package com.ai.gateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminSetFreeCountRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "免费次数不能为空")
    private Integer count;
}
