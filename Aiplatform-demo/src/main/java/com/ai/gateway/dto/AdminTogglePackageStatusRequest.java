package com.ai.gateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminTogglePackageStatusRequest {
    @NotNull(message = "套餐ID不能为空")
    private Long id;

    @NotNull(message = "状态值不能为空")
    private Integer status;
}
