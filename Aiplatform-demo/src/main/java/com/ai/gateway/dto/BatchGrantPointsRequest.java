package com.ai.gateway.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量发放点数请求 DTO
 */
@Data
public class BatchGrantPointsRequest {
    
    @NotNull(message = "用户ID列表不能为空")
    private List<Long> userIds;
    
    @NotNull(message = "点数不能为空")
    @Positive(message = "发放点数必须大于0")
    private BigDecimal points;
}
