package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "套餐信息")
public class PackageVO {

    @Schema(description = "套餐ID")
    private Long id;

    @Schema(description = "套餐编码")
    private String packageCode;

    @Schema(description = "套餐名称")
    private String packageName;

    @Schema(description = "套餐描述")
    private String description;

    @Schema(description = "身份标签")
    private String identityLabel;

    @Schema(description = "赠送点数")
    private BigDecimal pointsGranted;

    @Schema(description = "每日点数")
    private BigDecimal dailyPoints;

    @Schema(description = "月费价格")
    private BigDecimal priceMonthly;

    @Schema(description = "季费价格")
    private BigDecimal priceQuarterly;

    @Schema(description = "年费价格")
    private BigDecimal priceYearly;

    @Schema(description = "最大输入token数")
    private Integer maxInputTokens;

    @Schema(description = "最大输出token数")
    private Integer maxOutputTokens;

    @Schema(description = "模型访问等级")
    private Integer modelAccessLevel;

    @Schema(description = "优先级")
    private Integer priorityLevel;

    @Schema(description = "特性列表")
    private List<String> features;

    @Schema(description = "状态: 1上架 0下架")
    private Integer status;

    @Schema(description = "排序值")
    private Integer sortOrder;
}
