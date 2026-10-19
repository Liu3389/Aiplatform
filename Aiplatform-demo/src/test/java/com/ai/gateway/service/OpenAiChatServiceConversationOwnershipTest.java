package com.ai.gateway.service;

import com.ai.gateway.config.SpringAiClientFactory;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.entity.Conversation;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.ConversationMapper;
import com.ai.gateway.mapper.PackageTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiChatServiceConversationOwnershipTest {

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

    @Test
    void crossUserConversationIsRejectedBeforeModelLookupAndBilling() {
        when(conversationMapper.selectById(88L)).thenReturn(conversation(88L, 200L, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(100L, request(88L)));

        assertEquals(404, exception.getCode());
        assertEquals("会话不存在或无权访问", exception.getMessage());
        verify(conversationMapper).selectById(88L);
        verifyNoMoreInteractions(conversationMapper);
        verifyNoInteractions(platformModelService, callLogService, pointsService, redisTemplate,
                chatMessageService, userSubscriptionService, packageTemplateMapper, ossService,
                chatExecutor, searxngSearchService, searchIntentService,
                billingSettlementCoordinator, springAiClientFactory);
    }

    @Test
    void missingOrDeletedConversationIsRejected() {
        when(conversationMapper.selectById(88L)).thenReturn(null, conversation(88L, 100L, 1));

        BusinessException missing = assertThrows(BusinessException.class,
                () -> chatService.chat(100L, request(88L)));
        BusinessException deleted = assertThrows(BusinessException.class,
                () -> chatService.chat(100L, request(88L)));

        assertEquals(404, missing.getCode());
        assertEquals(404, deleted.getCode());
        verify(conversationMapper, org.mockito.Mockito.times(2)).selectById(88L);
        verifyNoMoreInteractions(conversationMapper);
        verifyNoInteractions(platformModelService, callLogService, pointsService,
                chatMessageService, billingSettlementCoordinator);
    }

    @Test
    void streamRejectsCrossUserConversationWithErrorAndNoSideEffects() {
        when(conversationMapper.selectById(88L)).thenReturn(conversation(88L, 200L, 0));
        RecordingEmitter emitter = new RecordingEmitter();

        SseEmitter result = chatService.streamChatWithEmitter(100L, request(88L), "request-id", emitter);

        assertNotNull(result);
        assertTrue(emitter.sentError);
        assertTrue(emitter.completed);
        verify(conversationMapper).selectById(88L);
        verifyNoMoreInteractions(conversationMapper);
        verifyNoInteractions(platformModelService, callLogService, pointsService, redisTemplate,
                chatMessageService, userSubscriptionService, packageTemplateMapper, ossService,
                chatExecutor, searxngSearchService, searchIntentService,
                billingSettlementCoordinator, springAiClientFactory);
    }

    @Test
    void ownedConversationPassesOwnershipCheck() {
        when(conversationMapper.selectById(88L)).thenReturn(conversation(88L, 100L, 0));
        when(platformModelService.getByDisplayName("local-model")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(100L, request(88L)));

        assertEquals("模型不存在或已禁用: local-model", exception.getMessage());
        verify(conversationMapper).selectById(88L);
        verify(platformModelService).getByDisplayName("local-model");
    }

    private static Conversation conversation(Long id, Long userId, Integer isDeleted) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setUserId(userId);
        conversation.setIsDeleted(isDeleted);
        return conversation;
    }

    private static ChatRequest request(Long conversationId) {
        ChatRequest request = new ChatRequest();
        request.setModel("local-model");
        request.setConversationId(conversationId);
        ChatRequest.ChatMessage message = new ChatRequest.ChatMessage();
        message.setRole("user");
        message.setContent("hello");
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
