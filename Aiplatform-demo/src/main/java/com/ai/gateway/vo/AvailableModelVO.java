package com.ai.gateway.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AvailableModelVO {

    private Long modelId;

    private String displayName;

    private String modelCode;

    private String provider;

    private String description;

    private String modelType;

    private BigDecimal fixedPoints;

    private BigDecimal pricePerInputToken;

    private BigDecimal pricePerOutputToken;

    private Integer maxInputTokens;

    private Integer maxOutputTokens;

    private boolean accessible;

    private Integer requiredLevel;

    private String requiredLevelName;

    private String upgradeHint;

    private List<String> features;

    private Integer sortOrder;
}