package com.ai.gateway.service;

import com.ai.gateway.common.Constants;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.ModelConfig;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.ModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 模型配置服务类
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String MODEL_LIST_CACHE_KEY = "ai_gateway:model_list";
    private static final String MODEL_CACHE_PREFIX = "ai_gateway:model:";
    private static final long MODEL_LIST_CACHE_TTL = 5;
    private static final long MODEL_CACHE_TTL = 10;

    /**
     * 根据模型名称查询模型配置（Redis缓存10分钟）
     * 
     * @param modelName 模型名称
     * @return 模型配置
     */
    public ModelConfig getModelByName(String modelName) {
        String cacheKey = MODEL_CACHE_PREFIX + modelName;
        try {
            String cached = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return objectMapper.readValue(cached, ModelConfig.class);
            }
        } catch (Exception e) {
            log.warn("读取模型缓存失败: {}", modelName, e);
        }

        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getModelName, modelName);
        ModelConfig model = modelConfigMapper.selectOne(wrapper);

        if (model != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey,
                        objectMapper.writeValueAsString(model),
                        MODEL_CACHE_TTL, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("缓存模型失败: {}", modelName, e);
            }
        }

        return model;
    }

    /**
     * 验证模型是否可用
     * 
     * @param modelName 模型名称
     * @return 模型配置
     */
    public ModelConfig validateModel(String modelName) {
        ModelConfig modelConfig = getModelByName(modelName);
        
        if (modelConfig == null) {
            throw new BusinessException(ResultCode.MODEL_NOT_FOUND);
        }

        if (modelConfig.getStatus() == Constants.MODEL_STATUS_DISABLED) {
            throw new BusinessException(ResultCode.MODEL_DISABLED);
        }

        return modelConfig;
    }

    /**
     * 计算调用费用
     * 
     * @param modelConfig 模型配置
     * @param inputTokens 输入Token数
     * @param outputTokens 输出Token数
     * @return 费用（美元）
     */
    public BigDecimal calculateCost(ModelConfig modelConfig, int inputTokens, int outputTokens) {
        BigDecimal inputCost = modelConfig.getInputPrice()
                .multiply(new BigDecimal(inputTokens))
                .divide(new BigDecimal(Constants.TOKEN_PRICE_BASE), 6, BigDecimal.ROUND_HALF_UP);
        
        BigDecimal outputCost = modelConfig.getOutputPrice()
                .multiply(new BigDecimal(outputTokens))
                .divide(new BigDecimal(Constants.TOKEN_PRICE_BASE), 6, BigDecimal.ROUND_HALF_UP);

        return inputCost.add(outputCost);
    }

    /**
     * 获取所有启用的模型列表（Redis缓存5分钟）
     * 
     * @return 模型配置列表
     */
    public List<ModelConfig> listEnabledModels() {
        try {
            String cached = (String) redisTemplate.opsForValue().get(MODEL_LIST_CACHE_KEY);
            if (cached != null && !cached.isEmpty()) {
                return objectMapper.readValue(cached, new TypeReference<List<ModelConfig>>() {});
            }
        } catch (Exception e) {
            log.warn("读取模型列表缓存失败，回源数据库", e);
        }

        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getStatus, Constants.MODEL_STATUS_ENABLED);
        List<ModelConfig> models = modelConfigMapper.selectList(wrapper);

        try {
            redisTemplate.opsForValue().set(MODEL_LIST_CACHE_KEY,
                    objectMapper.writeValueAsString(models),
                    MODEL_LIST_CACHE_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("缓存模型列表失败", e);
        }

        return models;
    }

    /**
     * 清除模型列表缓存（模型变更时调用）
     */
    public void evictModelListCache() {
        redisTemplate.delete(MODEL_LIST_CACHE_KEY);
    }
}
