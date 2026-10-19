package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Durable audit trail for an administrator's evidence-based resolution of an ambiguous AI charge. */
@Data
@TableName("ai_charge_review_audit")
public class AiChargeReviewAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
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
