package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AiChargeReviewQueueItemVO {
    private String requestId;
    private Long userId;
    private BigDecimal reservedPoints;
    private String modelName;
    private LocalDateTime upstreamStartedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String reason;
}
