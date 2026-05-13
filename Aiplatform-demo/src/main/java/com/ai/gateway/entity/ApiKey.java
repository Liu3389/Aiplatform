package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Key实体类
 * 
 * @author AI Gateway Platform
 */
@Data
@TableName("api_key")
public class ApiKey implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * API Key（唯一标识）
     */
    private String apiKey;

    /**
     * 用户ID
     */
    private Long userId;

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
     * 过期时间（NULL表示永久有效）
     */
    private LocalDateTime expireTime;
}
