package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest {

    @Schema(description = "用户名（3-32位字母数字下划线）", example = "newuser", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在3-32个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @Schema(description = "密码（8-64位，含大小写字母和数字）", example = "User1234", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在8-64个字符之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$", message = "密码必须包含至少一个大写字母、一个小写字母和一个数字")
    private String password;

    @Schema(description = "启用邮箱验证码注册时使用的邮箱", example = "user@example.com")
    @Pattern(regexp = "^$|^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$", message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;

    @Schema(description = "启用邮箱验证码注册时使用的6位验证码")
    @Pattern(regexp = "^$|^\\d{6}$", message = "邮箱验证码为6位数字")
    private String emailCode;

    @Schema(description = "旧注册方式的图形验证码标识")
    @Pattern(regexp = "^$|^[a-fA-F0-9]{32}$", message = "图形验证码标识不正确")
    private String captchaId;

    @Schema(description = "旧注册方式的图形验证码答案")
    @Pattern(regexp = "^$|^[a-zA-Z0-9]{1,8}$", message = "图形验证码不正确")
    private String captchaCode;
}
