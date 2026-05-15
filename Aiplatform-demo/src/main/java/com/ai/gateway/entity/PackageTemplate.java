package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐模板实体
 */
@Data
@TableName("package_template")
public class PackageTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 套餐代码（唯一标识）
     */
    private String packageCode;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 套餐描述
     */
    private String description;

    /**
     * 身份标识（如：至尊、尊享、畅享）
     */
    private String identityLabel;

    /**
     * 包含点数
     */
    private BigDecimal points;

    /**
     * 价格（元）
     */
    private BigDecimal price;

    /**
     * 有效期天数（NULL表示永久）
     */
    private Integer durationDays;

    /**
     * 每日调用限制（0表示无限制）
     */
    private Integer dailyCallLimit;

    /**
     * 每月调用限制（0表示无限制）
     */
    private Integer monthlyCallLimit;

    /**
     * 单次最大Token数（0表示无限制）
     */
    private Integer maxTokensPerCall;

    /**
     * 优先级等级（数字越大优先级越高）
     */
    private Integer priorityLevel;

    /**
     * 特性说明（JSON格式）
     */
    private String features;

    /**
     * 状态：0-下架，1-上架
     */
    private Integer status;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
