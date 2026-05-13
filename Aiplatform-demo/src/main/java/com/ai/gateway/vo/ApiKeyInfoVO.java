package com.ai.gateway.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key信息VO
 * 
 * @author AI Gateway Platform
 */
@Data
public class ApiKeyInfoVO {

    /**
     * API Key ID
     */
    private Long id;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * API Key名称/备注
     */
    private String name;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 每分钟请求限制次数
     */
    private Integer rateLimit;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
}
