package com.ai.gateway.vo;

import lombok.Data;

@Data
public class OAuthCallbackVO {
    private String mode;
    private String provider;
    private String token;
    private String deviceId;
    private UserInfoVO userInfo;
    private boolean requiresTwoFactor;
    private String challengeToken;
    private boolean firstLogin;
}
