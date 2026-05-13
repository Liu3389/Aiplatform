package com.ai.gateway.service;

import cn.hutool.core.util.IdUtil;
import com.ai.gateway.common.Constants;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.ApiKeyMapper;
import com.ai.gateway.vo.ApiKeyInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;
    private final TokenService tokenService;

    @Transactional(rollbackFor = Exception.class)
    public ApiKeyInfoVO generateApiKey(Long userId, String name, Integer rateLimit) {
        String rawKey = "sk-" + IdUtil.simpleUUID();
        String encryptedKey = tokenService.encryptApiKey(rawKey);

        ApiKey entity = new ApiKey();
        entity.setApiKey(encryptedKey);
        entity.setUserId(userId);
        entity.setName(name);
        entity.setStatus(Constants.API_KEY_STATUS_ENABLED);
        entity.setRateLimit(rateLimit != null ? rateLimit : Constants.DEFAULT_RATE_LIMIT);
        entity.setCreateTime(LocalDateTime.now());

        apiKeyMapper.insert(entity);

        log.info("API Key生成成功(加密存储): userId={}, keyId={}", userId, entity.getId());

        // Return the PLAIN key for one-time display
        ApiKeyInfoVO vo = convertToVO(entity);
        vo.setApiKey(rawKey);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long apiKeyId, Long userId) {
        ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API Key不存在");
        }
        if (!apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权操作此API Key");
        }
        apiKeyMapper.deleteById(apiKeyId);
        log.info("API Key删除成功: apiKeyId={}, userId={}", apiKeyId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableApiKey(Long apiKeyId, Long userId) {
        updateApiKeyStatus(apiKeyId, userId, Constants.API_KEY_STATUS_ENABLED);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableApiKey(Long apiKeyId, Long userId) {
        updateApiKeyStatus(apiKeyId, userId, Constants.API_KEY_STATUS_DISABLED);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRateLimit(Long apiKeyId, Long userId, Integer rateLimit) {
        ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API Key不存在");
        }
        if (!apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权操作此API Key");
        }
        apiKey.setRateLimit(rateLimit);
        apiKeyMapper.updateById(apiKey);
        log.info("限流阈值更新: apiKeyId={}, rateLimit={}", apiKeyId, rateLimit);
    }

    public List<ApiKeyInfoVO> listApiKeys(Long userId) {
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKey::getUserId, userId)
               .orderByDesc(ApiKey::getCreateTime);

        List<ApiKey> apiKeys = apiKeyMapper.selectList(wrapper);
        return apiKeys.stream()
                .map(entity -> {
                    ApiKeyInfoVO vo = convertToVO(entity);
                    vo.setApiKey(null);  // Never expose keys in list
                    return vo;
                })
                .collect(Collectors.toList());
    }

    public ApiKey getByApiKey(String encryptedKey) {
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKey::getApiKey, encryptedKey);
        return apiKeyMapper.selectOne(wrapper);
    }

    public ApiKey validateApiKey(String rawApiKey) {
        // Try to find by encrypted key direct match (for new encrypted keys)
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKey::getApiKey, tokenService.encryptApiKey(rawApiKey));
        ApiKey entity = apiKeyMapper.selectOne(wrapper);

        // Fallback: try raw key match (for old unencrypted keys)
        if (entity == null) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ApiKey::getApiKey, rawApiKey);
            entity = apiKeyMapper.selectOne(wrapper);
        }

        if (entity == null) {
            throw new BusinessException(ResultCode.INVALID_API_KEY);
        }

        if (entity.getStatus() == Constants.API_KEY_STATUS_DISABLED) {
            throw new BusinessException(ResultCode.API_KEY_DISABLED);
        }

        if (entity.getExpireTime() != null && entity.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.INVALID_API_KEY.getCode(), "API Key已过期");
        }

        return entity;
    }

    private void updateApiKeyStatus(Long apiKeyId, Long userId, Integer status) {
        ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API Key不存在");
        }
        if (!apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权操作此API Key");
        }
        apiKey.setStatus(status);
        apiKeyMapper.updateById(apiKey);
        log.info("API Key状态更新: apiKeyId={}, status={}", apiKeyId, status);
    }

    private ApiKeyInfoVO convertToVO(ApiKey entity) {
        ApiKeyInfoVO vo = new ApiKeyInfoVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
