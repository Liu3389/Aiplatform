package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserPointsInfoVO {

    private BigDecimal totalPoints;

    private BigDecimal temporaryPoints;

    private BigDecimal permanentPoints;

    private LocalDateTime temporaryExpireTime;

    private String membershipLabel;

    private Integer dailyTemporaryPoints;

    private Integer dailyPermanentPoints;

    private boolean hasActiveSubscription;
}