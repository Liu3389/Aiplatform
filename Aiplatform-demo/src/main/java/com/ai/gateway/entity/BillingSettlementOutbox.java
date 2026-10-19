package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 本地事务消息表：先把结算意图写进 MySQL，再由定时投递器发送 RabbitMQ。 */
@Data
@TableName("billing_settlement_outbox")
public class BillingSettlementOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private BigDecimal actualPoints;
    private String modelName;
    private String status;
    private Integer dispatchAttempts;
    private Integer processAttempts;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime createTime;
    private LocalDateTime settledAt;
    private String lastError;
}
