package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("platform_model_config")
public class PlatformModelConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String displayName; // 展示名称
    private String actualModel; // 实际调用模型
    private BigDecimal fixedPoints; // 固定点数消耗
    private String provider; // 关联厂商
    private String baseUrl; // 厂商 API 地址
    private String apiKey; // 平台持有的 API Key
    private Integer sortOrder; // 排序字段 (P3-20)
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
