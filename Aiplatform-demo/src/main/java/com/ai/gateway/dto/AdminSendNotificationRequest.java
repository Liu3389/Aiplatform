package com.ai.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员发送通知请求 DTO
 */
@Data
public class AdminSendNotificationRequest {
    
    @NotBlank(message = "标题不能为空")
    @Size(min = 1, max = 128, message = "标题长度必须在 1-128 字符之间")
    private String title;
    
    @NotBlank(message = "内容不能为空")
    @Size(min = 1, max = 1024, message = "内容长度必须在 1-1024 字符之间")
    private String content;
    
    private String type = "system";
    
    private String target = "all";
    
    private String membership;
    
    private Integer days;
}
