package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserInfoVO {

    private Long id;

    private String username;

    private String email;

    private BigDecimal balance;

    private Integer status;

    private String role;

    private String freeApiStrategy;

    private Integer freeQuota;

    private Integer dailyCallLimit;

    private Integer monthlyCallLimit;

    private String membership;

    /**
     * 会员过期时间
     */
    private LocalDateTime membershipExpireTime;

    /**
     * 点数余额（保留三位小数）
     */
    private BigDecimal points;

    private Integer userPoints;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
