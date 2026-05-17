package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PackageVO {

    private Long id;

    private String packageCode;

    private String packageName;

    private String description;

    private String identityLabel;

    private BigDecimal pointsGranted;

    private BigDecimal dailyPoints;

    private BigDecimal priceMonthly;

    private BigDecimal priceQuarterly;

    private BigDecimal priceYearly;

    private Integer maxInputTokens;

    private Integer maxOutputTokens;

    private Integer modelAccessLevel;

    private Integer priorityLevel;

    private List<String> features;

    private Integer status;

    private Integer sortOrder;
}