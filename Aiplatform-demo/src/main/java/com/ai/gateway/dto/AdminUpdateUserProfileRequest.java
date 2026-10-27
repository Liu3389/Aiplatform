package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理员编辑用户资料")
public class AdminUpdateUserProfileRequest {
    @NotNull
    private Long userId;

    @Size(max = 64, message = "昵称最多64个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最多128个字符")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 32, message = "手机号最多32个字符")
    private String phone;
}
