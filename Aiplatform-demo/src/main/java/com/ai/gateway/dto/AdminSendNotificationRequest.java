package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员发送通知请求 DTO
 */
@Data
@Schema(description = "管理员发送通知请求")
public class AdminSendNotificationRequest {

    @Schema(description = "标题(1-128字符)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题不能为空")
    @Size(min = 1, max = 128, message = "标题长度必须在 1-128 字符之间")
    private String title;

    @Schema(description = "内容(1-1024字符)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "内容不能为空")
    @Size(min = 1, max = 1024, message = "内容长度必须在 1-1024 字符之间")
    private String content;

    @Schema(description = "通知类型: system/announcement", example = "system")
    private String type = "system";

    @Schema(description = "目标: all/指定用户", example = "all")
    private String target = "all";

    @Schema(description = "会员等级限制")
    private String membership;

    @Schema(description = "有效天数")
    private Integer days;
}
