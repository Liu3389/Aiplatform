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
     * 身份标识（如：普通用户、白银会员、黄金会员、钻石会员）
     */
    private String identityLabel;

    /**
     * 开通赠送点数（永久有效）
     */
    private BigDecimal pointsGranted;

    /**
     * 每日发放点数（限时，当日有效）
     */
    private BigDecimal dailyPoints;

    /**
     * 月付价格（元）
     */
    private BigDecimal priceMonthly;

    /**
     * 季付价格（元）
     */
    private BigDecimal priceQuarterly;

    /**
     * 年付价格（元）
     */
    private BigDecimal priceYearly;

    /**
     * 每日调用限制（0表示无限制）
     * @deprecated 已取消次数限制，改用点数控制
     */
    @Deprecated
    private Integer dailyCallLimit;

    /**
     * 每月调用限制（0表示无限制）
     * @deprecated 已取消次数限制，改用点数控制
     */
    @Deprecated
    private Integer monthlyCallLimit;

    /**
     * 单次最大Token数（向后兼容）
     * @deprecated 使用 maxInputTokens 和 maxOutputTokens 替代
     */
    @Deprecated
    private Integer maxTokensPerCall;

    /**
     * 单次最大输入Token数（0表示无限制）
     */
    private Integer maxInputTokens;

    /**
     * 单次最大输出Token数（0表示无限制）
     */
    private Integer maxOutputTokens;

    /**
     * 模型访问等级（0-基础，1-中端，2-高端，3-全部）
     */
    private Integer modelAccessLevel;

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
