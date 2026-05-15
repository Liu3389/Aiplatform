package com.ai.gateway.service;

import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.mapper.PlatformModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformModelService {

    private final PlatformModelConfigMapper platformModelConfigMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MODEL_CACHE_KEY = "platform:model:";

    /**
     * 获取所有启用的平台对话模型
     */
    public List<PlatformModelConfig> listEnabledModels() {
        LambdaQueryWrapper<PlatformModelConfig> wrapper = new LambdaQueryWrapper<>();
        // 修复 P3-20: 增加排序逻辑
        wrapper.eq(PlatformModelConfig::getStatus, 1)
               .orderByAsc(PlatformModelConfig::getSortOrder);
        return platformModelConfigMapper.selectList(wrapper);
    }

    /**
     * 根据展示名称获取模型配置（带缓存）
     */
    public PlatformModelConfig getByDisplayName(String displayName) {
        // 修复 P1-7: 缓存完整的 JSON 对象，而不是简单的标记
        String cacheKey = MODEL_CACHE_KEY + displayName;
        
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, PlatformModelConfig.class);
            }
        } catch (Exception e) {
            log.warn("解析模型配置缓存失败", e);
        }

        LambdaQueryWrapper<PlatformModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformModelConfig::getDisplayName, displayName)
               .eq(PlatformModelConfig::getStatus, 1);
        PlatformModelConfig config = platformModelConfigMapper.selectOne(wrapper);

        if (config != null) {
            try {
                String json = objectMapper.writeValueAsString(config);
                redisTemplate.opsForValue().set(cacheKey, json, 30, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("缓存模型配置失败", e);
            }
        }
        return config;
    }

    /**
     * 获取模型固定点数消耗
     */
    public BigDecimal getModelPointsCost(PlatformModelConfig config) {
        if (config == null || config.getFixedPoints() == null) {
            return new BigDecimal("3"); // 默认 3 点
        }
        return config.getFixedPoints();
    }
}
