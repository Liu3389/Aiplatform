package com.ai.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Spring AI 客户端工厂
 * 为每个 PlatformModelConfig 动态创建 OpenAiChatClient 实例
 */
@Slf4j
@Component
public class SpringAiClientFactory {

    // 缓存已创建的 ChatClient 实例，key = baseUrl + apiKey
    private final Cache<String, OpenAiChatClient> clientCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    /**
     * 获取或创建 OpenAiChatClient 实例
     * @param baseUrl API 基础 URL（兼容完整 endpoint 与根地址两种格式）
     * @param apiKey API 密钥
     * @return OpenAiChatClient 实例
     */
    public OpenAiChatClient getChatModel(String baseUrl, String apiKey) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String cacheKey = normalizedBaseUrl + "|" + apiKey;
        return clientCache.get(cacheKey, key -> {
            log.info("创建新的 OpenAiChatClient 实例: baseUrl={}", normalizedBaseUrl);
            OpenAiApi openAiApi = new OpenAiApi(normalizedBaseUrl, apiKey);
            return new OpenAiChatClient(openAiApi);
        });
    }

    /**
     * 将数据库中的完整 API 地址标准化为 Spring AI 所需的根地址。
     * 数据库存的是自封装时代的完整 endpoint（如 https://api.deepseek.com/v1/chat/completions），
     * 而 OpenAiApi 会自动拼接 /v1/chat/completions，不处理会导致 URL 双重拼接。
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String[] suffixes = {"/v1/chat/completions", "/chat/completions", "/v1/messages", "/v1"};
        for (String suffix : suffixes) {
            if (url.endsWith(suffix)) {
                return url.substring(0, url.length() - suffix.length());
            }
        }
        return url;
    }
}
