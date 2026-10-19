package com.ai.gateway.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthBindingVO {
    private String provider;
    private String displayName;
    private String email;
}
