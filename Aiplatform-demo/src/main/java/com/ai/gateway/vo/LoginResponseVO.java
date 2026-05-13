package com.ai.gateway.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应VO
 * 
 * @author AI Gateway Platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseVO {

    /**
     * 访问令牌（这里简化处理，实际应使用JWT）
     */
    private String token;

    /**
     * 用户信息
     */
    private UserInfoVO userInfo;
}
