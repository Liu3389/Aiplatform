package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量开通会员请求")
public class BatchActivateMembershipRequest {
    @Schema(description = "用户ID列表(1-1000)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID列表不能为空")
    @Size(min = 1, max = 1000, message = "批量操作用户数必须在 1-1000 之间")
    private List<Long> userIds;

    @Schema(description = "会员类型: free/silver/gold/diamond", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员类型不能为空")
    private String membership;

    @Schema(description = "有效天数")
    private Integer days;
}
