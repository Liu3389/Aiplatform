package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "邮箱验证码登录请求")
public class EmailCodeLoginRequest {

    @Schema(description = "已注册邮箱", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "邮箱不能为空")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    @Pattern(regexp = "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$", message = "邮箱格式不正确")
    private String email;

    @Schema(description = "6位邮箱验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入邮箱验证码")
    @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码为6位数字")
    private String code;

    @Schema(description = "账户已启用两步验证时，填写六位 TOTP 或一次性备用码")
    private String twoFactorCode;
}
