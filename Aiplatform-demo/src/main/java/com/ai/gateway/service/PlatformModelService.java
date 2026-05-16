package com.ai.gateway.service;

import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.mapper.PlatformModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformModelService {

    private final PlatformModelConfigMapper platformModelConfigMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 优化：增加 Caffeine 本地缓存，减少 Redis 网络开销
    private final Cache<String, PlatformModelConfig> localCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

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
     * 根据展示名称获取模型配置（二级缓存：Caffeine -> Redis -> DB）
     */
    public PlatformModelConfig getByDisplayName(String displayName) {
        // 1. 检查本地缓存
        PlatformModelConfig localConfig = localCache.getIfPresent(displayName);
        if (localConfig != null) {
            return localConfig;
        }

        // 2. 检查 Redis 缓存
        String cacheKey = MODEL_CACHE_KEY + displayName;
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                PlatformModelConfig config = objectMapper.readValue(cachedJson, PlatformModelConfig.class);
                localCache.put(displayName, config); // 回写本地缓存
                return config;
            }
        } catch (Exception e) {
            log.warn("解析模型配置缓存失败", e);
        }

        // 3. 查询数据库
        LambdaQueryWrapper<PlatformModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformModelConfig::getDisplayName, displayName)
               .eq(PlatformModelConfig::getStatus, 1);
        PlatformModelConfig config = platformModelConfigMapper.selectOne(wrapper);

        if (config != null) {
            try {
                String json = objectMapper.writeValueAsString(config);
                long randomOffset = ThreadLocalRandom.current().nextInt(0, 6);
                redisTemplate.opsForValue().set(cacheKey, json, 30 + randomOffset, TimeUnit.MINUTES);
                localCache.put(displayName, config);
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
            return new BigDecimal("3");
        }
        return config.getFixedPoints();
    }

    @PostConstruct
    public void warmupCache() {
        try {
            List<PlatformModelConfig> models = listEnabledModels();
            for (PlatformModelConfig config : models) {
                String cacheKey = MODEL_CACHE_KEY + config.getDisplayName();
                String json = objectMapper.writeValueAsString(config);
                long randomOffset = ThreadLocalRandom.current().nextInt(0, 6);
                redisTemplate.opsForValue().set(cacheKey, json, 30 + randomOffset, TimeUnit.MINUTES);
                localCache.put(config.getDisplayName(), config);
            }
            log.info("模型配置缓存预热完成: {} 个模型", models.size());
        } catch (Exception e) {
            log.error("模型配置缓存预热失败", e);
        }
    }
}
