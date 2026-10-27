package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminUserDetailVO {
    private Long id;
    private String username;
    private String nickname;
    private String displayName;
    private String avatarUrl;
    private boolean customAvatar;
    private String email;
    private boolean emailVerified;
    private String phone;
    private boolean phoneVerified;
    private BigDecimal balance;
    private BigDecimal points;
    private String role;
    private String status;
    private String membership;
    private LocalDateTime membershipExpireTime;
    private Integer apiKeyCount;
    private Long totalCalls;
    private BigDecimal totalPointsConsumed;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private List<OAuthAccount> oauthAccounts;

    @Data
    public static class OAuthAccount {
        private String provider;
        private String displayName;
        private String email;
        private String avatarUrl;
        private LocalDateTime createTime;
    }
}
