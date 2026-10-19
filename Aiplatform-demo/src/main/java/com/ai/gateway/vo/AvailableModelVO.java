package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "可用模型信息")
public class AvailableModelVO {

    @Schema(description = "模型ID")
    private Long modelId;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "模型编码")
    private String modelCode;

    @Schema(description = "供应商")
    private String provider;

    @Schema(description = "模型描述")
    private String description;

    @Schema(description = "模型类型")
    private String modelType;

    @Schema(description = "固定点数")
    private BigDecimal fixedPoints;

    @Schema(description = "输入token单价")
    private BigDecimal pricePerInputToken;

    @Schema(description = "输出token单价")
    private BigDecimal pricePerOutputToken;

    @Schema(description = "最大输入token数")
    private Integer maxInputTokens;

    @Schema(description = "最大输出token数")
    private Integer maxOutputTokens;

    @Schema(description = "当前用户是否可访问")
    private boolean accessible;

    @Schema(description = "所需会员等级")
    private Integer requiredLevel;

    @Schema(description = "所需等级名称")
    private String requiredLevelName;

    @Schema(description = "升级提示")
    private String upgradeHint;

    @Schema(description = "特性列表")
    private List<String> features;

    @Schema(description = "排序值")
    private Integer sortOrder;
}
