package com.ai.gateway.service;

import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.mapper.PlatformModelConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformModelServiceAvailabilityTest {

    @Mock private PlatformModelConfigMapper platformModelConfigMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private PlatformModelService platformModelService;

    @Test
    void availableModelsExposeOnlyWhetherTheirUpstreamIsConfigured() {
        PlatformModelConfig configured = model("configured", "openai", "model-a",
                "http://127.0.0.1:19090", "local-test-key");
        PlatformModelConfig missingKey = model("missing-key", "deepseek", "deepseek-chat",
                "https://api.deepseek.com", " ");
        PlatformModelConfig missingBaseUrl = model("missing-url", "openai", "model-b",
                " ", "configured-key");
        PlatformModelConfig missingProvider = model("missing-provider", " ", "model-c",
                "https://example.test", "configured-key");
        when(platformModelConfigMapper.selectList(any())).thenReturn(
                List.of(configured, missingKey, missingBaseUrl, missingProvider));

        List<Map<String, Object>> result = platformModelService.getAvailableModels(0);

        assertEquals(List.of(true, false, false, false),
                result.stream().map(model -> model.get("chatEnabled")).toList());
        result.forEach(model -> {
            org.junit.jupiter.api.Assertions.assertFalse(model.containsKey("apiKey"));
            org.junit.jupiter.api.Assertions.assertFalse(model.containsKey("api_key"));
        });
    }

    @Test
    void deepSeekCanUseServerCredentialFallbackWithoutExposingIt() {
        ReflectionTestUtils.setField(platformModelService, "deepseekApiKey", "server-only-test-key");
        PlatformModelConfig deepseek = model("deepseek", "deepseek", "deepseek-chat",
                "https://api.deepseek.com", " ");
        PlatformModelConfig openAi = model("openai", "openai", "gpt-test",
                "https://example.test", " ");
        PlatformModelConfig storedCredential = model("stored-key", "deepseek", "deepseek-chat",
                "https://api.deepseek.com", "stored-model-key");
        when(platformModelConfigMapper.selectList(any())).thenReturn(List.of(deepseek));

        assertEquals("server-only-test-key", platformModelService.resolveApiKey(deepseek));
        assertEquals("stored-model-key", platformModelService.resolveApiKey(storedCredential));
        org.junit.jupiter.api.Assertions.assertNull(platformModelService.resolveApiKey(openAi));
        assertEquals(true, platformModelService.isChatEnabled(deepseek));

        Map<String, Object> publicModel = platformModelService.getAvailableModels(0).get(0);
        assertEquals(true, publicModel.get("chatEnabled"));
        org.junit.jupiter.api.Assertions.assertFalse(publicModel.containsKey("apiKey"));
        org.junit.jupiter.api.Assertions.assertFalse(publicModel.containsKey("api_key"));
    }

    private static PlatformModelConfig model(String name, String provider, String actualModel,
                                             String baseUrl, String apiKey) {
        PlatformModelConfig model = new PlatformModelConfig();
        model.setDisplayName(name);
        model.setProvider(provider);
        model.setActualModel(actualModel);
        model.setBaseUrl(baseUrl);
        model.setApiKey(apiKey);
        model.setStatus(1);
        model.setFixedPoints(BigDecimal.ONE);
        return model;
    }
}
