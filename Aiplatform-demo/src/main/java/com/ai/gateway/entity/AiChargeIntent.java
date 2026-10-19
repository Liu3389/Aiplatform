package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Durable lifecycle for an AI request's point reservation and settlement. */
@Data
@TableName("ai_charge_intent")
public class AiChargeIntent {
    public static final String PREPARING = "PREPARING";
    public static final String RESERVED = "RESERVED";
    public static final String IN_FLIGHT = "IN_FLIGHT";
    public static final String RESPONSE_RECEIVED = "RESPONSE_RECEIVED";
    public static final String MESSAGES_PERSISTED = "MESSAGES_PERSISTED";
    public static final String SETTLEMENT_PENDING = "SETTLEMENT_PENDING";
    public static final String REFUND_PENDING = "REFUND_PENDING";
    public static final String SETTLED = "SETTLED";
    public static final String REFUNDED = "REFUNDED";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String requestFingerprint;
    private Long userId;
    private BigDecimal reservedPoints;
    private BigDecimal actualPoints;
    private String modelName;
    private String status;
    private Boolean reservationConfirmed;
    private LocalDateTime upstreamStartedAt;
    private LocalDateTime responseReceivedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime recoveryClaimedAt;
    private String lastError;
}
