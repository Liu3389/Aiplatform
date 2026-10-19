package com.ai.gateway.service;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.mapper.ConversationMapper;
import com.ai.gateway.mapper.PackageTemplateMapper;
import com.ai.gateway.service.OpenAiChatService.ChatConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpenAiChatServiceProviderTokenPayloadTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiChatService service;
    private ChatConfig config;

    @BeforeEach
    void setUp() {
        PlatformModelConfig model = new PlatformModelConfig();
        model.setProvider("openai");
        model.setActualModel("local-model");
        model.setMaxOutputTokens(1_536);
        config = new ChatConfig("local-key", "http://127.0.0.1", "openai", model, false, "local-model");
        service = new OpenAiChatService(
                mock(PlatformModelService.class),
                mock(CallLogService.class),
                mock(PointsService.class),
                mock(StringRedisTemplate.class),
                mock(ChatMessageService.class),
                mock(ConversationMapper.class),
                mock(UserSubscriptionService.class),
                mock(PackageTemplateMapper.class),
                mock(OssService.class));
    }

    @Test
    void allProviderPayloadsSerializePositiveClampedLimitsWithTheirProtocolField() throws Exception {
        List<TokenCase> cases = List.of(
                new TokenCase(null, 1_536),
                new TokenCase(-9, 1_536),
                new TokenCase(50_000, 1_536),
                new TokenCase(768, 768));

        for (TokenCase tokenCase : cases) {
            ChatRequest request = request(tokenCase.requested());
            assertProtocolField(service.buildOpenAiPayload(request, config, false), "max_tokens", tokenCase.expected());
            assertProtocolField(service.buildDeepSeekResponsesPayload(request, config, false),
                    "max_output_tokens", tokenCase.expected());
            assertProtocolField(service.buildAnthropicPayload(request, config, false), "max_tokens", tokenCase.expected());
            assertEquals(tokenCase.expected(), service.resolveOutputTokenLimit(request, config));
        }
    }

    private void assertProtocolField(Map<String, Object> payload, String expectedKey, int expectedValue) throws Exception {
        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsBytes(payload));
        assertTrue(serialized.has(expectedKey), serialized.toString());
        assertTrue(serialized.path(expectedKey).isIntegralNumber(), serialized.toString());
        assertTrue(serialized.path(expectedKey).asInt() > 0, serialized.toString());
        assertEquals(expectedValue, serialized.path(expectedKey).asInt(), serialized.toString());
        for (String otherKey : List.of("max_tokens", "max_output_tokens")) {
            if (!otherKey.equals(expectedKey)) assertFalse(serialized.has(otherKey), serialized.toString());
        }
    }

    private ChatRequest request(Integer maxTokens) {
        ChatRequest request = new ChatRequest();
        request.setModel("Local Mock");
        request.setMaxTokens(maxTokens);
        ChatRequest.ChatMessage message = new ChatRequest.ChatMessage();
        message.setRole("user");
        message.setContent("token limit serialization test");
        request.setMessages(List.of(message));
        return request;
    }

    private record TokenCase(Integer requested, int expected) {
    }
}
