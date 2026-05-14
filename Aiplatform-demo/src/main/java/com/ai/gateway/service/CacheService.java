package com.ai.gateway.service;

import com.ai.gateway.entity.ModelConfig;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.ModelConfigMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存服务 - 优化热点数据访问
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ModelConfigMapper modelConfigMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 缓存Key前缀
    private static final String MODEL_CONFIG_CACHE_PREFIX = "cache:model_config:";
    private static final String USER_CACHE_PREFIX = "cache:user:";
    private static final String USER_BALANCE_CACHE_PREFIX = "cache:user_balance:";
    private static final String USER_ROLE_CACHE_PREFIX = "cache:user_role:";
    
    // 缓存过期时间
    private static final long MODEL_CONFIG_TTL_MINUTES = 30;
    private static final long USER_INFO_TTL_MINUTES = 10;
    private static final long USER_BALANCE_TTL_MINUTES = 5;

    /**
     * 获取模型配置（带缓存）
     * @param modelName 模型名称
     * @return 模型配置
     */
    public ModelConfig getModelConfig(String modelName) {
        String cacheKey = MODEL_CONFIG_CACHE_PREFIX + modelName;
        
        // 尝试从缓存获取
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, ModelConfig.class);
            } catch (Exception e) {
                log.warn("解析缓存的模型配置失败: modelName={}", modelName, e);
            }
        }
        
        // 缓存未命中，从数据库查询
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getModelName, modelName)
               .eq(ModelConfig::getStatus, 1);
        ModelConfig config = modelConfigMapper.selectOne(wrapper);
        
        if (config != null) {
            // 存入缓存
            try {
                String json = objectMapper.writeValueAsString(config);
                redisTemplate.opsForValue().set(cacheKey, json, MODEL_CONFIG_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("模型配置已缓存: modelName={}", modelName);
            } catch (Exception e) {
                log.error("缓存模型配置失败: modelName={}", modelName, e);
            }
        }
        
        return config;
    }

    /**
     * 获取所有启用的模型配置列表（带缓存）
     * @return 模型配置列表
     */
    @SuppressWarnings("unchecked")
    public List<ModelConfig> getAllEnabledModelConfigs() {
        String cacheKey = MODEL_CONFIG_CACHE_PREFIX + "all_enabled";
        
        // 尝试从缓存获取
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, List.class);
            } catch (Exception e) {
                log.warn("解析缓存的模型配置列表失败", e);
            }
        }
        
        // 缓存未命中，从数据库查询
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getStatus, 1)
               .orderByAsc(ModelConfig::getModelName);
        List<ModelConfig> configs = modelConfigMapper.selectList(wrapper);
        
        // 存入缓存
        try {
            String json = objectMapper.writeValueAsString(configs);
            redisTemplate.opsForValue().set(cacheKey, json, MODEL_CONFIG_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("模型配置列表已缓存: count={}", configs.size());
        } catch (Exception e) {
            log.error("缓存模型配置列表失败", e);
        }
        
        return configs;
    }

    /**
     * 获取用户信息（带缓存）
     * @param userId 用户ID
     * @return 用户信息
     */
    public User getUserInfo(Long userId) {
        String cacheKey = USER_CACHE_PREFIX + userId;
        
        // 尝试从缓存获取
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, User.class);
            } catch (Exception e) {
                log.warn("解析缓存的用户信息失败: userId={}", userId, e);
            }
        }
        
        // 缓存未命中，从数据库查询
        User user = userMapper.selectById(userId);
        
        if (user != null) {
            // 存入缓存（不缓存密码）
            user.setPassword(null);
            try {
                String json = objectMapper.writeValueAsString(user);
                redisTemplate.opsForValue().set(cacheKey, json, USER_INFO_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("用户信息已缓存: userId={}", userId);
            } catch (Exception e) {
                log.error("缓存用户信息失败: userId={}", userId, e);
            }
        }
        
        return user;
    }

    /**
     * 获取用户余额（带缓存）
     * @param userId 用户ID
     * @return 用户余额
     */
    public java.math.BigDecimal getUserBalance(Long userId) {
        String cacheKey = USER_BALANCE_CACHE_PREFIX + userId;
        
        // 尝试从缓存获取
        String cachedBalance = redisTemplate.opsForValue().get(cacheKey);
        if (cachedBalance != null) {
            try {
                return new java.math.BigDecimal(cachedBalance);
            } catch (Exception e) {
                log.warn("解析缓存的用户余额失败: userId={}", userId, e);
            }
        }
        
        // 缓存未命中，从数据库查询
        User user = userMapper.selectById(userId);
        java.math.BigDecimal balance = user != null ? user.getBalance() : java.math.BigDecimal.ZERO;
        
        // 存入缓存
        redisTemplate.opsForValue().set(cacheKey, balance.toString(), USER_BALANCE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("用户余额已缓存: userId={}, balance={}", userId, balance);
        
        return balance;
    }

    /**
     * 获取用户角色（带缓存）
     * @param userId 用户ID
     * @return 用户角色
     */
    public String getUserRole(Long userId) {
        String cacheKey = USER_ROLE_CACHE_PREFIX + userId;
        
        // 尝试从缓存获取
        String role = redisTemplate.opsForValue().get(cacheKey);
        if (role != null) {
            return role;
        }
        
        // 缓存未命中，从数据库查询
        User user = userMapper.selectById(userId);
        String userRole = user != null ? user.getRole() : "USER";
        
        // 存入缓存
        redisTemplate.opsForValue().set(cacheKey, userRole, USER_INFO_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("用户角色已缓存: userId={}, role={}", userId, userRole);
        
        return userRole;
    }

    /**
     * 清除模型配置缓存
     * @param modelName 模型名称，null表示清除所有
     */
    public void clearModelConfigCache(String modelName) {
        if (modelName == null) {
            // 清除所有模型配置缓存
            redisTemplate.delete(MODEL_CONFIG_CACHE_PREFIX + "all_enabled");
            log.info("已清除所有模型配置缓存");
        } else {
            // 清除指定模型缓存
            redisTemplate.delete(MODEL_CONFIG_CACHE_PREFIX + modelName);
            redisTemplate.delete(MODEL_CONFIG_CACHE_PREFIX + "all_enabled");
            log.info("已清除模型配置缓存: modelName={}", modelName);
        }
    }

    /**
     * 清除用户相关缓存
     * @param userId 用户ID
     */
    public void clearUserCache(Long userId) {
        redisTemplate.delete(USER_CACHE_PREFIX + userId);
        redisTemplate.delete(USER_BALANCE_CACHE_PREFIX + userId);
        redisTemplate.delete(USER_ROLE_CACHE_PREFIX + userId);
        log.info("已清除用户缓存: userId={}", userId);
    }

    /**
     * 更新用户余额缓存
     * @param userId 用户ID
     * @param newBalance 新余额
     */
    public void updateUserBalanceCache(Long userId, java.math.BigDecimal newBalance) {
        String cacheKey = USER_BALANCE_CACHE_PREFIX + userId;
        redisTemplate.opsForValue().set(cacheKey, newBalance.toString(), USER_BALANCE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("用户余额缓存已更新: userId={}, balance={}", userId, newBalance);
    }
}
