package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key信息VO
 *
 * @author AI Gateway Platform
 */
@Data
@Schema(description = "API Key信息")
public class ApiKeyInfoVO {

    /**
     * API Key ID
     */
    @Schema(description = "API Key ID")
    private Long id;

    /**
     * API Key
     */
    @Schema(description = "API Key")
    private String apiKey;

    /**
     * API Key名称/备注
     */
    @Schema(description = "API Key名称/备注")
    private String name;

    /**
     * 状态：0-禁用，1-启用
     */
    @Schema(description = "状态: 0禁用 1启用")
    private Integer status;

    /**
     * 每分钟请求限制次数
     */
    @Schema(description = "每分钟请求限制次数")
    private Integer rateLimit;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    private LocalDateTime expireTime;
}
