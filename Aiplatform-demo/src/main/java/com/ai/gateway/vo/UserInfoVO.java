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

    private Integer userPoints;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
