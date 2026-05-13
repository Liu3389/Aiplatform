package com.ai.gateway.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ai.gateway.entity.UserApiKey;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.UserApiKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserApiKeyService {

    private final UserApiKeyMapper userApiKeyMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> PROVIDER_BASE_URLS = new HashMap<>();
    static {
        PROVIDER_BASE_URLS.put("openai", "https://api.openai.com");
        PROVIDER_BASE_URLS.put("anthropic", "https://api.anthropic.com");
        PROVIDER_BASE_URLS.put("deepseek", "https://api.deepseek.com");
        PROVIDER_BASE_URLS.put("moonshot", "https://api.moonshot.cn");
        PROVIDER_BASE_URLS.put("zhipu", "https://open.bigmodel.cn");
        PROVIDER_BASE_URLS.put("qwen", "https://dashscope.aliyuncs.com");
        PROVIDER_BASE_URLS.put("siliconflow", "https://api.siliconflow.cn");
    }

    public List<UserApiKey> listByUser(Long userId) {
        LambdaQueryWrapper<UserApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserApiKey::getUserId, userId)
               .orderByDesc(UserApiKey::getCreateTime);
        List<UserApiKey> keys = userApiKeyMapper.selectList(wrapper);
        for (UserApiKey k : keys) {
            k.setApiKey(maskApiKey(k.getApiKey()));
        }
        return keys;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserApiKey addKey(Long userId, String provider, String displayName, String apiKey, String baseUrl) {
        if (provider == null || provider.isBlank()) throw new BusinessException("请选择服务商");

        String testBaseUrl = resolveBaseUrl(provider, baseUrl);

        try {
            testConnection(provider, testBaseUrl, apiKey);
        } catch (Exception e) {
            log.warn("API Key 验证失败: provider={}, error={}", provider, e.getMessage());
            throw new BusinessException("API Key 验证失败: " + e.getMessage() + "。请检查 Key 格式和有效性。");
        }

        long count = userApiKeyMapper.selectCount(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
        );
        if (count >= 20) {
            throw new BusinessException("最多存储20个API Key，请先删除不用的");
        }

        UserApiKey entity = new UserApiKey();
        entity.setUserId(userId);
        entity.setProvider(provider);
        entity.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : provider);
        entity.setApiKey(apiKey);
        entity.setBaseUrl(testBaseUrl);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        userApiKeyMapper.insert(entity);

        log.info("用户API Key添加成功: userId={}, provider={}, keyId={}", userId, provider, entity.getId());

        entity.setApiKey(maskApiKey(apiKey));
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKey(Long userId, Long keyId) {
        UserApiKey key = userApiKeyMapper.selectById(keyId);
        if (key == null || !key.getUserId().equals(userId)) {
            throw new BusinessException("API Key 不存在");
        }
        userApiKeyMapper.deleteById(keyId);
        log.info("用户API Key已删除: userId={}, keyId={}", userId, keyId);
    }

    public UserApiKey getKeyForUse(Long keyId, Long userId) {
        UserApiKey key = userApiKeyMapper.selectById(keyId);
        if (key == null || !key.getUserId().equals(userId)) {
            throw new BusinessException("API Key 不存在");
        }
        return key;
    }

    private String resolveBaseUrl(String provider, String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) return baseUrl;
        return PROVIDER_BASE_URLS.getOrDefault(provider.toLowerCase(), "https://api.openai.com");
    }

    private void testConnection(String provider, String baseUrl, String apiKey) {
        String p = provider.toLowerCase();
        String testUrl;
        Map<String, String> headers = new HashMap<>();
        Map<String, Object> body = new HashMap<>();

        switch (p) {
            case "openai":
            case "deepseek":
            case "moonshot":
            case "siliconflow":
            case "qwen":
            case "zhipu":
                testUrl = baseUrl.replaceAll("/$", "") + "/v1/models";
                headers.put("Authorization", "Bearer " + apiKey);
                break;
            case "anthropic":
                testUrl = baseUrl.replaceAll("/$", "") + "/v1/messages";
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", "2023-06-01");
                body.put("model", "claude-3-haiku-20240307");
                body.put("max_tokens", 1);
                body.put("messages", List.of(Map.of("role", "user", "content", "hi")));
                break;
            default:
                testUrl = baseUrl.replaceAll("/$", "") + "/v1/models";
                headers.put("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpResponse response;
            if (!body.isEmpty()) {
                response = HttpRequest.post(testUrl)
                        .headerMap(headers, true)
                        .header("Content-Type", "application/json")
                        .body(objectMapper.writeValueAsString(body))
                        .timeout(10000)
                        .execute();
            } else {
                response = HttpRequest.get(testUrl)
                        .headerMap(headers, true)
                        .timeout(10000)
                        .execute();
            }

            int status = response.getStatus();
            if (status == 200) {
                log.info("API Key 验证通过: provider={}", provider);
                return;
            }
            if (status == 401 || status == 403) {
                throw new BusinessException("API Key 无效或权限不足 (HTTP " + status + ")");
            }
            log.info("API Key 验证返回 HTTP {}，尝试 models 接口", status);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (!(e instanceof BusinessException)) {
                try {
                    HttpResponse fallback = HttpRequest.get("https://httpbin.org/get")
                            .timeout(5000)
                            .execute();
                } catch (Exception ignored) {}
                throw new BusinessException("无法连接到 " + baseUrl + "，请检查网络或 Base URL 是否正确");
            }
            throw (BusinessException) e;
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 8) return key;
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
