package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求DTO
 *
 * @author AI Gateway Platform
 */
@Data
@Schema(description = "用户登录请求")
public class UserLoginRequest {

    /**
     * 用户名或已验证邮箱
     */
    @Schema(description = "用户名或已验证邮箱", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名或邮箱不能为空")
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码", example = "Admin123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录验证码 ID */
    @Schema(description = "登录验证码 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "验证码不能为空")
    private String captchaId;

    /** 登录图形验证码 */
    @Schema(description = "登录图形验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    /** 账户已启用两步验证时，填写六位 TOTP 或一次性备用码。 */
    @Schema(description = "两步验证码或备用码；仅启用两步验证的账户需要")
    private String twoFactorCode;
}
