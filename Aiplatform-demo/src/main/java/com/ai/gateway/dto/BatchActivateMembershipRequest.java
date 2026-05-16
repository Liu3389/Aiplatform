package com.ai.gateway.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BatchActivateMembershipRequest {
    @NotNull(message = "用户ID列表不能为空")
    @Size(min = 1, max = 1000, message = "批量操作用户数必须在 1-1000 之间")
    private List<Long> userIds;

    @NotNull(message = "会员类型不能为空")
    private String membership;

    private Integer days;
}
