package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * API Key生成请求DTO
 *
 * @author AI Gateway Platform
 */
@Data
@Schema(description = "API Key生成请求")
public class ApiKeyGenerateRequest {

    /**
     * API Key名称/备注
     */
    @Schema(description = "API Key名称/备注", example = "我的测试Key")
    private String name;

    /**
     * 每分钟请求限制次数（默认100）
     */
    @Schema(description = "每分钟请求限制次数(默认100)", example = "100")
    private Integer rateLimit;
}
