package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "登录响应")
public class LoginResponseVO {

    /**
     * 访问令牌（这里简化处理，实际应使用JWT）
     */
    @Schema(description = "访问令牌，后续接口需作为 X-User-Token 请求头携带", example = "abc123def456")
    private String token;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    private UserInfoVO userInfo;

    /**
     * 设备ID（用于会话管理）
     */
    @Schema(description = "设备ID（用于会话管理）")
    private String deviceId;

    public LoginResponseVO(String token, UserInfoVO userInfo) {
        this.token = token;
        this.userInfo = userInfo;
    }
}
