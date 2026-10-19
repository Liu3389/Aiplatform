package com.ai.gateway.service;

import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.Conversation;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.mapper.ChatMessageMapper;
import com.ai.gateway.mapper.ConversationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.messaging.rabbit-enabled=false",
        "app.messaging.intent-recovery-ms=3600000"
})
class ChatMessagePersistenceLifecycleTest {

    private static final long USER_ID = 1L;
    private static final String MODEL_NAME = "Local Mock (Codex)";
    private static final String TRIGGER_NAME = "trg_d2_fail_assistant_insert";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ChatMessageMapper chatMessageMapper;
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private PlatformModelService platformModelService;
    @Autowired private ChatMessageService chatMessageService;
    @Autowired private AiChargeIntentService intentService;
    @Autowired private BillingSettlementCoordinator settlementCoordinator;
    @Autowired private AiChargeIntentRecoveryService recoveryService;
    @Autowired private PointsService pointsService;
    @Autowired private StringRedisTemplate redisTemplate;

    private final List<String> requestIds = new ArrayList<>();
    private final List<Long> conversationIds = new ArrayList<>();
    private BigDecimal originalPoints;

    @BeforeEach
    void captureBalance() {
        originalPoints = jdbcTemplate.queryForObject("SELECT points FROM user WHERE id = ?", BigDecimal.class, USER_ID);
        assertNotNull(originalPoints, "local smoke user 1 must exist");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + TRIGGER_NAME);
        for (String requestId : requestIds) {
            jdbcTemplate.update("DELETE FROM chat_message WHERE request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM chat_message_history WHERE request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM points_bill WHERE business_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM billing_record WHERE request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM billing_settlement_outbox WHERE request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM ai_charge_intent WHERE request_id = ?", requestId);
            redisTemplate.delete("points:pre_deduct:" + requestId);
            redisTemplate.delete("points:refund:" + requestId);
            redisTemplate.delete("points:settlement:" + requestId);
        }
        for (Long conversationId : conversationIds) {
            jdbcTemplate.update("DELETE FROM conversation WHERE id = ? AND user_id = ?", conversationId, USER_ID);
        }
        jdbcTemplate.update("UPDATE user SET points = ?, points_version = points_version + 1 WHERE id = ?",
                originalPoints, USER_ID);
        redisTemplate.opsForValue().set("points:user:" + USER_ID, originalPoints.toPlainString());
        requestIds.clear();
        conversationIds.clear();
    }

    @Test
    void assistantInsertFailureRollsBackWholeTurnAndRefundsReservation() {
        String requestId = nextRequestId();
        Long conversationId = createConversation();
        BigDecimal reserved = modelPointsCost();
        prepareResponse(requestId, reserved, reserved);
        createAssistantFailureTrigger(requestId);

        assertThrows(RuntimeException.class, () -> chatMessageService.saveConversationTurn(
                requestId, conversationId, USER_ID, "D2 persistence failure", "mock response",
                MODEL_NAME, 12, 0.1, null, null));

        assertEquals(0, count("SELECT COUNT(*) FROM chat_message WHERE request_id = ?", requestId),
                "user insert must roll back when assistant insert fails");
        assertEquals(0, conversationMessageCount(conversationId), "conversation count must roll back too");
        assertEquals("RESPONSE_RECEIVED", intentService.getByRequestId(requestId).getStatus(),
                "the message marker must share the failed transaction");

        assertTrue(settlementCoordinator.refundAfterFailure(USER_ID, requestId, "D2 test persistence failure"));
        assertEquals("REFUNDED", intentService.getByRequestId(requestId).getStatus());
        assertEquals(originalPoints.setScale(2), pointsService.getUserPoints(USER_ID));
        assertEquals(0, count("SELECT COUNT(*) FROM billing_settlement_outbox WHERE request_id = ?", requestId));
        assertEquals(0, count("SELECT COUNT(*) FROM billing_record WHERE request_id = ?", requestId));
    }

    @Test
    void persistedTurnCanBeRetriedWithoutDuplicateMessagesOrCharge() {
        String requestId = nextRequestId();
        Long conversationId = createConversation();
        BigDecimal reserved = modelPointsCost();
        prepareResponse(requestId, reserved, reserved);

        chatMessageService.saveConversationTurn(requestId, conversationId, USER_ID,
                "D2 retry-safe user message", "D2 retry-safe assistant response", MODEL_NAME,
                19, 0.2, null, null);
        assertEquals("MESSAGES_PERSISTED", intentService.getByRequestId(requestId).getStatus());
        assertTrue(settlementCoordinator.settle(USER_ID, requestId, reserved, MODEL_NAME));

        // Same request key and content models a caller retry after the response was lost.
        chatMessageService.saveConversationTurn(requestId, conversationId, USER_ID,
                "D2 retry-safe user message", "D2 retry-safe assistant response", MODEL_NAME,
                19, 0.2, null, null);
        assertTrue(settlementCoordinator.settle(USER_ID, requestId, reserved, MODEL_NAME));

        assertEquals(2, count("SELECT COUNT(*) FROM chat_message WHERE request_id = ?", requestId));
        assertEquals(2, conversationMessageCount(conversationId));
        assertEquals(1, count("SELECT COUNT(*) FROM billing_settlement_outbox WHERE request_id = ?", requestId));
        assertEquals(1, count("SELECT COUNT(*) FROM billing_record WHERE request_id = ?", requestId));
        assertEquals(originalPoints.subtract(reserved).setScale(2), pointsService.getUserPoints(USER_ID));
        assertTrue("SETTLED".equals(intentService.getByRequestId(requestId).getStatus()));
    }

    @Test
    void recoveryRefundsUnpersistedResponseAndSettlesOnlyWhenAssistantMessageExists() {
        BigDecimal reserved = modelPointsCost();

        String missingMessageId = nextRequestId();
        prepareResponse(missingMessageId, reserved, reserved);
        makeStale(missingMessageId);
        recoveryService.recoverStaleIntents();
        assertEquals("REFUNDED", intentService.getByRequestId(missingMessageId).getStatus());
        assertEquals(0, count("SELECT COUNT(*) FROM billing_settlement_outbox WHERE request_id = ?", missingMessageId));
        assertEquals(originalPoints.setScale(2), pointsService.getUserPoints(USER_ID));

        String persistedMessageId = nextRequestId();
        Long conversationId = createConversation();
        prepareResponse(persistedMessageId, reserved, reserved);
        insertMessage(persistedMessageId, conversationId, "user", "recovery user message", 0);
        insertMessage(persistedMessageId, conversationId, "assistant", "recovery assistant message", 8);
        assertEquals(1, conversationMapper.incrementConversationMessageCount(
                conversationId, USER_ID, 2, LocalDateTime.now()));
        makeStale(persistedMessageId);

        recoveryService.recoverStaleIntents();

        assertEquals("SETTLED", intentService.getByRequestId(persistedMessageId).getStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM billing_record WHERE request_id = ?", persistedMessageId));
        assertEquals(originalPoints.subtract(reserved).setScale(2), pointsService.getUserPoints(USER_ID));
    }

    private void prepareResponse(String requestId, BigDecimal reserved, BigDecimal actual) {
        String fingerprint = "d2".repeat(32);
        assertTrue(settlementCoordinator.prepareReservation(USER_ID, requestId, reserved, MODEL_NAME, fingerprint));
        assertTrue(pointsService.preDeductPoints(USER_ID, requestId, MODEL_NAME));
        assertTrue(settlementCoordinator.markReserved(requestId));
        assertTrue(settlementCoordinator.markUpstreamStarted(requestId));
        assertTrue(settlementCoordinator.markResponseReceived(requestId, actual));
    }

    private Long createConversation() {
        Conversation conversation = new Conversation();
        conversation.setUserId(USER_ID);
        conversation.setTitle("D2 lifecycle test");
        conversation.setModel(MODEL_NAME);
        conversation.setMessageCount(0);
        conversation.setLastMessageTime(LocalDateTime.now());
        conversation.setIsDeleted(0);
        conversationMapper.insert(conversation);
        conversationIds.add(conversation.getId());
        return conversation.getId();
    }

    private void insertMessage(String requestId, Long conversationId, String role, String content, int tokens) {
        ChatMessage message = new ChatMessage();
        message.setRequestId(requestId);
        message.setConversationId(conversationId);
        message.setUserId(USER_ID);
        message.setRole(role);
        message.setContent(content);
        message.setModel(MODEL_NAME);
        message.setTokens(tokens);
        message.setCreateTime(LocalDateTime.now());
        assertEquals(1, chatMessageMapper.insert(message));
    }

    private void createAssistantFailureTrigger(String requestId) {
        jdbcTemplate.execute("CREATE TRIGGER " + TRIGGER_NAME + " BEFORE INSERT ON chat_message "
                + "FOR EACH ROW BEGIN IF NEW.request_id = '" + requestId + "' AND NEW.role = 'assistant' THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'D2 test persistence failure'; END IF; END");
    }

    private void makeStale(String requestId) {
        jdbcTemplate.update("UPDATE ai_charge_intent SET update_time = DATE_SUB(NOW(), INTERVAL 10 MINUTE), "
                + "recovery_claimed_at = NULL WHERE request_id = ?", requestId);
    }

    private BigDecimal modelPointsCost() {
        PlatformModelConfig model = platformModelService.getByDisplayName(MODEL_NAME);
        assertNotNull(model, "local mock model configuration must exist");
        BigDecimal cost = platformModelService.getModelPointsCost(model);
        assertFalse(cost.signum() <= 0, "local mock model must use a positive reservation");
        return cost;
    }

    private int conversationMessageCount(Long conversationId) {
        return jdbcTemplate.queryForObject("SELECT message_count FROM conversation WHERE id = ?", Integer.class,
                conversationId);
    }

    private int count(String sql, String value) {
        return jdbcTemplate.queryForObject(sql, Integer.class, value);
    }

    private String nextRequestId() {
        String requestId = "d2_" + UUID.randomUUID().toString().replace("-", "");
        requestIds.add(requestId);
        return requestId;
    }
}
