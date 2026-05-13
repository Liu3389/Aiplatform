package com.ai.gateway.service;

import com.ai.gateway.common.Constants;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.ModelConfig;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.ModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

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

    /**
     * 根据模型名称查询模型配置
     * 
     * @param modelName 模型名称
     * @return 模型配置
     */
    public ModelConfig getModelByName(String modelName) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getModelName, modelName);
        return modelConfigMapper.selectOne(wrapper);
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
     * 获取所有启用的模型列表
     * 
     * @return 模型配置列表
     */
    public List<ModelConfig> listEnabledModels() {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getStatus, Constants.MODEL_STATUS_ENABLED);
        return modelConfigMapper.selectList(wrapper);
    }
}
