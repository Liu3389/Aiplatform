package com.ai.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginEmailCodeRequest {
    @NotBlank(message = "邮箱不能为空")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    @Pattern(regexp = "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$", message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "请先完成图形验证码")
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "图形验证码已失效，请刷新")
    private String captchaId;

    @NotBlank(message = "请输入图形验证码")
    @Size(min = 1, max = 8, message = "图形验证码不正确")
    private String captchaCode;
}
