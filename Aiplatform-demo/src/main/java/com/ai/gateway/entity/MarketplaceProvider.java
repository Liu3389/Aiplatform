package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("marketplace_provider")
public class MarketplaceProvider implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String providerName; // 厂商名称
    private String baseUrl; // API 基础地址
    private String platformApiKey; // 平台持有的 Key
    private BigDecimal inputPrice; // 每千输入 Token 价格（元）
    private BigDecimal outputPrice; // 每千输出 Token 价格（元）
    private Integer status; // 状态
    private LocalDateTime createTime;
}
