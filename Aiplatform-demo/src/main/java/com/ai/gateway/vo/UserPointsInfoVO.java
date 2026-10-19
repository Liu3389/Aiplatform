package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户点数详情")
public class UserPointsInfoVO {

    @Schema(description = "总点数")
    private BigDecimal totalPoints;

    @Schema(description = "限时点数")
    private BigDecimal temporaryPoints;

    @Schema(description = "永久点数")
    private BigDecimal permanentPoints;

    @Schema(description = "限时点数过期时间")
    private LocalDateTime temporaryExpireTime;

    @Schema(description = "会员标签")
    private String membershipLabel;

    @Schema(description = "每日限时点数")
    private Integer dailyTemporaryPoints;

    @Schema(description = "每日永久点数")
    private Integer dailyPermanentPoints;

    @Schema(description = "是否有有效订阅")
    private boolean hasActiveSubscription;
}
