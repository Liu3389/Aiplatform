package com.ai.gateway.dto;

import lombok.Data;

/**
 * API Key生成请求DTO
 * 
 * @author AI Gateway Platform
 */
@Data
public class ApiKeyGenerateRequest {

    /**
     * API Key名称/备注
     */
    private String name;

    /**
     * 每分钟请求限制次数（默认100）
     */
    private Integer rateLimit;
}
