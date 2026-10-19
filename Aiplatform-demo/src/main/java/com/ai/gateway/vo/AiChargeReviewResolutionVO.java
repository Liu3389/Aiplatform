package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiChargeReviewResolutionVO {
    private String requestId;
    private Long userId;
    private String action;
    private String status;
    private String intentStatus;
    private BigDecimal actualPoints;
    private String message;
}
