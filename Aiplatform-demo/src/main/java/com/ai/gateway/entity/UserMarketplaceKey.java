package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_marketplace_key")
public class UserMarketplaceKey implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long providerId;
    private String apiKey;
    private String keyName;
    private Long totalRequests;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private BigDecimal totalCost;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
