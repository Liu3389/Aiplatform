package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型配置实体类
 * 
 * @author AI Gateway Platform
 */
@Data
@TableName("model_config")
public class ModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型名称（如gpt-3.5-turbo）
     */
    private String modelName;

    /**
     * 厂商：openai/doubao/deepseek等
     */
    private String provider;

    /**
     * API基础地址
     */
    private String baseUrl;

    /**
     * 厂商API Key
     */
    private String apiKey;

    /**
     * 每千输入Token价格（美元）
     */
    private BigDecimal inputPrice;

    /**
     * 每千输出Token价格（美元）
     */
    private BigDecimal outputPrice;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
