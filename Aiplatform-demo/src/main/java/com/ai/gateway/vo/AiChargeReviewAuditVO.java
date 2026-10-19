package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AiChargeReviewAuditVO {
    private String requestId;
    private Long userId;
    private Long adminId;
    private String action;
    private BigDecimal actualPoints;
    private String evidence;
    private String status;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime completedAt;
}
