package com.ai.gateway.service;

import com.ai.gateway.config.SpringAiClientFactory;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.ConversationMapper;
import com.ai.gateway.mapper.PackageTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiChatServiceModelAccessTest {

    @Mock private PlatformModelService platformModelService;
    @Mock private CallLogService callLogService;
    @Mock private PointsService pointsService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ChatMessageService chatMessageService;
    @Mock private ConversationMapper conversationMapper;
    @Mock private UserSubscriptionService userSubscriptionService;
    @Mock private PackageTemplateMapper packageTemplateMapper;
    @Mock private OssService ossService;
    @Mock private Executor chatExecutor;
    @Mock private SearxngSearchService searxngSearchService;
    @Mock private SearchIntentService searchIntentService;
    @Mock private BillingSettlementCoordinator billingSettlementCoordinator;
    @Mock private SpringAiClientFactory springAiClientFactory;

    @InjectMocks
    private OpenAiChatService chatService;

    @BeforeEach
    void injectRequiredBillingCoordinator() {
        ReflectionTestUtils.setField(chatService, "billingSettlementCoordinator", billingSettlementCoordinator);
        lenient().when(platformModelService.resolveApiKey(any(PlatformModelConfig.class)))
                .thenAnswer(invocation -> {
                    String storedKey = invocation.<PlatformModelConfig>getArgument(0).getApiKey();
                    return storedKey == null || storedKey.isBlank() ? null : storedKey;
                });
    }

    @Test
    void freeUserIsDeniedRestrictedModelBeforePreDeduction() {
        when(platformModelService.getByDisplayName("restricted-model")).thenReturn(restrictedModel(2));
        when(userSubscriptionService.getCurrentSubscription(77L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(77L, request("restricted-model")));

        assertTrue(exception.getMessage().contains("当前会员等级无法使用模型"));
        verify(pointsService, never()).preDeductPoints(eq(77L), anyString(), eq("restricted-model"));
        verifyNoInteractions(callLogService, chatMessageService, billingSettlementCoordinator,
                springAiClientFactory, chatExecutor);
    }

    @Test
    void missingUpstreamCredentialIsRejectedBeforeReservationOrPreDeduction() {
        PlatformModelConfig unavailable = restrictedModel(0);
        unavailable.setApiKey(" ");
        when(platformModelService.getByDisplayName("unavailable-model")).thenReturn(unavailable);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(77L, request("unavailable-model")));

        assertEquals("模型上游线路未配置，请联系管理员", exception.getMessage());
        verify(pointsService, never()).preDeductPoints(eq(77L), anyString(), eq("unavailable-model"));
        verifyNoInteractions(callLogService, chatMessageService, billingSettlementCoordinator,
                springAiClientFactory, chatExecutor);
    }

    @Test
    void deepSeekServerCredentialFallbackIsUsedForUpstreamConfig() {
        PlatformModelConfig deepseek = restrictedModel(0);
        deepseek.setProvider("deepseek");
        deepseek.setActualModel("deepseek-chat");
        deepseek.setBaseUrl("https://api.deepseek.com");
        deepseek.setApiKey(" ");
        when(platformModelService.getByDisplayName("deepseek-model")).thenReturn(deepseek);
        when(platformModelService.resolveApiKey(deepseek)).thenReturn("server-only-test-key");

        OpenAiChatService.ChatConfig config = ReflectionTestUtils.invokeMethod(
                chatService, "resolveChatConfig", request("deepseek-model"), 77L);

        assertEquals("server-only-test-key", config.apiKey);
        assertEquals("https://api.deepseek.com", config.baseUrl);
    }

    @Test
    void imageRequestWithTextOnlyModelIsRejectedBeforeBilling() {
        PlatformModelConfig textModel = restrictedModel(0);
        when(platformModelService.getByDisplayName("text-model")).thenReturn(textModel);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(77L, requestWithImage("text-model")));

        assertTrue(exception.getMessage().contains("不支持视觉识别"));
        verify(pointsService, never()).preDeductPoints(eq(77L), anyString(), anyString());
        verifyNoInteractions(billingSettlementCoordinator, callLogService, chatMessageService,
                springAiClientFactory, chatExecutor);
    }

    @Test
    void selectedVisionModelUsesResolvedServerKeyAndItsOwnApiAddress() {
        PlatformModelConfig visionModel = restrictedModel(0);
        visionModel.setDisplayName("DeepSeek-V4-Flash-Vision-Exp");
        visionModel.setProvider("deepseek");
        visionModel.setActualModel("deepseek-flash");
        visionModel.setBaseUrl("https://api.deepseek.com");
        visionModel.setApiKey(" ");
        visionModel.setSupportsVision(1);
        when(platformModelService.getByDisplayName("DeepSeek-V4-Flash-Vision-Exp")).thenReturn(visionModel);
        when(platformModelService.resolveApiKey(visionModel)).thenReturn("server-only-test-key");

        ChatRequest imageRequest = requestWithImage("DeepSeek-V4-Flash-Vision-Exp");
        OpenAiChatService.ChatConfig config = ReflectionTestUtils.invokeMethod(
                chatService, "resolveChatConfig", imageRequest, 77L);
        OpenAiChatService.ChatConfig imageConfig = ReflectionTestUtils.invokeMethod(
                chatService, "resolveVisionConfigIfNeeded", imageRequest, config);

        assertEquals("server-only-test-key", imageConfig.apiKey);
        assertEquals("https://api.deepseek.com", imageConfig.baseUrl);
        assertEquals("deepseek-flash", imageConfig.model);
    }

    @Test
    void higherTierUserPassesAccessGateButInsufficientPointsStillStopTheRequest() {
        UserSubscription subscription = new UserSubscription();
        subscription.setPackageId(8L);
        PackageTemplate packageTemplate = new PackageTemplate();
        packageTemplate.setModelAccessLevel(3);
        when(platformModelService.getByDisplayName("restricted-model")).thenReturn(restrictedModel(2));
        when(userSubscriptionService.getCurrentSubscription(77L)).thenReturn(subscription, null);
        when(packageTemplateMapper.selectById(8L)).thenReturn(packageTemplate);
        when(billingSettlementCoordinator.prepareReservation(eq(77L), anyString(), any(),
                eq("restricted-model"), anyString()))
                .thenReturn(true);
        when(pointsService.preDeductPoints(eq(77L), anyString(), eq("restricted-model"))).thenReturn(false);
        when(pointsService.getUserPoints(77L)).thenReturn(BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(77L, request("restricted-model")));

        assertTrue(exception.getMessage().contains("点数不足"));
        verify(pointsService).preDeductPoints(eq(77L), anyString(), eq("restricted-model"));
        verify(billingSettlementCoordinator).prepareReservation(eq(77L), anyString(), any(),
                eq("restricted-model"), anyString());
        verify(billingSettlementCoordinator).rejectReservation(eq(77L), anyString(), anyString());
        verifyNoInteractions(callLogService, chatMessageService, springAiClientFactory, chatExecutor);
    }

    @Test
    void subscriptionLookupFailureFailsClosedBeforePreDeduction() {
        when(platformModelService.getByDisplayName("restricted-model")).thenReturn(restrictedModel(2));
        when(userSubscriptionService.getCurrentSubscription(77L)).thenThrow(new IllegalStateException("database timeout"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(77L, request("restricted-model")));

        assertEquals(503, exception.getCode());
        assertEquals("模型权限校验暂时不可用，请稍后重试", exception.getMessage());
        verify(pointsService, never()).preDeductPoints(eq(77L), anyString(), eq("restricted-model"));
        verifyNoInteractions(callLogService, chatMessageService, billingSettlementCoordinator,
                springAiClientFactory, chatExecutor);
    }

    @Test
    void packageLookupFailureFailsClosed() {
        UserSubscription subscription = new UserSubscription();
        subscription.setPackageId(8L);
        when(platformModelService.getByDisplayName("restricted-model")).thenReturn(restrictedModel(2));
        when(userSubscriptionService.getCurrentSubscription(77L)).thenReturn(subscription);
        when(packageTemplateMapper.selectById(8L)).thenThrow(new IllegalStateException("package database timeout"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(77L, request("restricted-model")));

        assertEquals(503, exception.getCode());
        verify(pointsService, never()).preDeductPoints(eq(77L), anyString(), eq("restricted-model"));
        verifyNoInteractions(callLogService, chatMessageService, billingSettlementCoordinator,
                springAiClientFactory, chatExecutor);
    }

    @Test
    void streamingPermissionLookupFailureReturnsErrorWithoutPreDeduction() {
        when(platformModelService.getByDisplayName("restricted-model")).thenReturn(restrictedModel(2));
        when(userSubscriptionService.getCurrentSubscription(77L)).thenThrow(new IllegalStateException("database timeout"));
        RecordingEmitter emitter = new RecordingEmitter();

        chatService.streamChatWithEmitter(77L, request("restricted-model"), "request-id", emitter);

        assertTrue(emitter.sentError);
        assertTrue(emitter.completed);
        verify(pointsService, never()).preDeductPoints(eq(77L), anyString(), eq("restricted-model"));
        verifyNoInteractions(callLogService, chatMessageService, billingSettlementCoordinator,
                springAiClientFactory, chatExecutor);
    }

    private static PlatformModelConfig restrictedModel(int accessLevel) {
        PlatformModelConfig config = new PlatformModelConfig();
        config.setDisplayName("restricted-model");
        config.setActualModel("mock-model");
        config.setFixedPoints(BigDecimal.ONE);
        config.setProvider("openai");
        config.setBaseUrl("http://127.0.0.1:19090");
        config.setApiKey("sk-local-test");
        config.setStatus(1);
        config.setAccessLevel(accessLevel);
        return config;
    }

    private static ChatRequest request(String model) {
        ChatRequest request = new ChatRequest();
        request.setModel(model);
        ChatRequest.ChatMessage message = new ChatRequest.ChatMessage();
        message.setRole("user");
        message.setContent("hello");
        request.setMessages(List.of(message));
        return request;
    }

    private static ChatRequest requestWithImage(String model) {
        ChatRequest request = new ChatRequest();
        request.setModel(model);
        ChatRequest.ChatMessage message = new ChatRequest.ChatMessage();
        message.setRole("user");
        message.setContent(List.of(
                java.util.Map.of("type", "text", "text", "What is shown?"),
                java.util.Map.of("type", "image_url", "image_url", java.util.Map.of("url", "data:image/png;base64,AA=="))
        ));
        request.setMessages(List.of(message));
        return request;
    }

    private static class RecordingEmitter extends SseEmitter {
        private boolean sentError;
        private boolean completed;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sentError = true;
        }

        @Override
        public void complete() {
            completed = true;
            super.complete();
        }
    }
}
