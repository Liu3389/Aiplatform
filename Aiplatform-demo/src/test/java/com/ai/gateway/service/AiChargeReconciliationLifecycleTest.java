package com.ai.gateway.service;

import com.ai.gateway.entity.AiChargeReviewAudit;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.vo.AdminPageVO;
import com.ai.gateway.vo.AiChargeReviewQueueItemVO;
import com.ai.gateway.vo.AiChargeReviewResolutionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
class AiChargeReconciliationLifecycleTest {

    private static final long USER_ID = 1L;
    private static final long ADMIN_ID = 999999L;
    private static final String MODEL_NAME = "Local Mock (Codex)";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AiChargeIntentService intentService;
    @Autowired private AiChargeReconciliationService reconciliationService;
    @Autowired private PointsService pointsService;
    @Autowired private PlatformModelService platformModelService;
    @Autowired private AiChargeIntentRecoveryService recoveryService;

    private BigDecimal originalPoints;
    private BigDecimal reservedPoints;
    private BigDecimal confirmedActualPoints;
    private String requestId;

    @BeforeEach
    void captureBalance() {
        originalPoints = jdbc.queryForObject("SELECT points FROM user WHERE id = ?", BigDecimal.class, USER_ID);
        assertNotNull(originalPoints, "local smoke user must exist");
        PlatformModelConfig model = platformModelService.getByDisplayName(MODEL_NAME);
        assertNotNull(model, "local mock model must exist");
        reservedPoints = platformModelService.getModelPointsCost(model).setScale(3, RoundingMode.HALF_DOWN);
        assertTrue(reservedPoints.signum() > 0);
        confirmedActualPoints = reservedPoints.divide(new BigDecimal("2"), 3, RoundingMode.DOWN);
        assertTrue(confirmedActualPoints.signum() > 0);
        requestId = "review_" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ai_charge_review_audit WHERE request_id = ?", requestId);
        jdbc.update("DELETE FROM points_bill WHERE business_id = ?", requestId);
        jdbc.update("DELETE FROM billing_record WHERE request_id = ?", requestId);
        jdbc.update("DELETE FROM billing_settlement_outbox WHERE request_id = ?", requestId);
        jdbc.update("DELETE FROM ai_charge_intent WHERE request_id = ?", requestId);
        redis.delete("points:pre_deduct:" + requestId);
        redis.delete("points:refund:" + requestId);
        redis.delete("points:settlement:" + requestId);
        jdbc.update("UPDATE user SET points = ?, points_version = points_version + 1 WHERE id = ?",
                originalPoints, USER_ID);
        redis.opsForValue().set("points:user:" + USER_ID, originalPoints.toPlainString());
    }

    @Test
    void evidenceBackedRefundIsAuditedAndIdempotent() {
        prepareManualReview();

        AiChargeReviewAudit audit = reconciliationService.beginResolution(requestId, ADMIN_ID,
                "REFUND", null, "provider request log 8001: no billable response");
        AiChargeReviewResolutionVO result = reconciliationService.processResolution(requestId);

        assertEquals("REFUNDED", result.getIntentStatus());
        assertEquals("RESOLVED", result.getStatus());
        assertEquals("RESOLVED", auditStatus());
        assertMoneyEquals(originalPoints, databasePoints());
        assertMoneyEquals(originalPoints, redisPoints());
        assertEquals(1, count("SELECT COUNT(*) FROM points_bill WHERE business_id = ? AND change_type = 'ROLLBACK'"));

        reconciliationService.beginResolution(requestId, ADMIN_ID,
                "REFUND", null, "provider request log 8001: no billable response");
        reconciliationService.processResolution(requestId);
        assertMoneyEquals(originalPoints, databasePoints());
        assertEquals(1, count("SELECT COUNT(*) FROM points_bill WHERE business_id = ? AND change_type = 'ROLLBACK'"));
        assertEquals(1L, reconciliationService.listReviewHistory(1, 100).getTotal());
    }

    @Test
    void evidenceBackedSettlementChargesOnlyTheConfirmedActualAmount() {
        prepareManualReview();

        reconciliationService.beginResolution(requestId, ADMIN_ID, "SETTLE", confirmedActualPoints,
                "provider invoice line 412: usage confirmed at the recorded point amount");
        AiChargeReviewResolutionVO result = reconciliationService.processResolution(requestId);

        assertEquals("SETTLED", result.getIntentStatus());
        assertEquals("RESOLVED", result.getStatus());
        assertMoneyEquals(confirmedActualPoints, result.getActualPoints());
        assertMoneyEquals(originalPoints.subtract(confirmedActualPoints), databasePoints());
        assertMoneyEquals(originalPoints.subtract(confirmedActualPoints), redisPoints());
        assertEquals(1, count("SELECT COUNT(*) FROM billing_settlement_outbox WHERE request_id = ? AND status = 'SETTLED'"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM billing_record WHERE request_id = ? AND amount = ?",
                Integer.class, requestId, confirmedActualPoints));
        assertEquals(1, count("SELECT COUNT(*) FROM points_bill WHERE business_id = ? AND change_type = 'SETTLE'"));
    }

    @Test
    void persistedManualSettlementResumesAfterCrashAndHistoryReflectsCompletion() {
        prepareManualReview();
        reconciliationService.beginResolution(requestId, ADMIN_ID, "SETTLE", confirmedActualPoints,
                "provider invoice line 413: usage confirmed at the recorded point amount");
        jdbc.update("UPDATE ai_charge_intent SET update_time = DATE_SUB(NOW(), INTERVAL 10 MINUTE) "
                + "WHERE request_id = ?", requestId);

        recoveryService.recoverStaleIntents();
        assertEquals("SETTLED", intentStatus());
        assertEquals("RESOLVED", reconciliationService.listReviewHistory(1, 20)
                .getRecords().stream().filter(audit -> requestId.equals(audit.getRequestId()))
                .findFirst().orElseThrow().getStatus());
        assertMoneyEquals(originalPoints.subtract(confirmedActualPoints), databasePoints());
        assertEquals(1, count("SELECT COUNT(*) FROM billing_record WHERE request_id = ?"));
    }

    @Test
    void invalidOrConflictingResolutionDoesNotMoveTheHeldBalance() {
        prepareManualReview();

        assertThrows(IllegalArgumentException.class, () -> reconciliationService.beginResolution(requestId,
                ADMIN_ID, "SETTLE", reservedPoints.add(new BigDecimal("0.001")), "provider invoice confirms usage"));
        assertEquals("MANUAL_REVIEW", intentStatus());

        reconciliationService.beginResolution(requestId, ADMIN_ID,
                "REFUND", null, "provider request log 8002: request was not billed");
        assertThrows(IllegalStateException.class, () -> reconciliationService.beginResolution(requestId,
                ADMIN_ID, "SETTLE", confirmedActualPoints, "provider invoice confirms usage"));

        assertEquals("REFUND_PENDING", intentStatus());
        assertMoneyEquals(originalPoints, databasePoints());
        assertMoneyEquals(originalPoints.subtract(reservedPoints), redisPoints());
        assertEquals(1, count("SELECT COUNT(*) FROM ai_charge_review_audit WHERE request_id = ?"));
    }

    @Test
    void evidenceRejectsSecretLikeValuesBeforePersistingTheAudit() {
        prepareManualReview();

        assertThrows(IllegalArgumentException.class, () -> reconciliationService.beginResolution(requestId,
                ADMIN_ID, "REFUND", null, "provider log api_key=sk-12345678901234567890"));

        assertEquals(0, count("SELECT COUNT(*) FROM ai_charge_review_audit WHERE request_id = ?"));
        assertEquals("MANUAL_REVIEW", intentStatus());
        assertMoneyEquals(originalPoints.subtract(reservedPoints), redisPoints());
    }

    @Test
    void queueExposesOnlyManualReviewFieldsAndClampsPageSize() {
        prepareManualReview();
        AdminPageVO<AiChargeReviewQueueItemVO> page = reconciliationService.listManualReview(0, 1000);

        assertTrue(page.getSize() <= 100);
        AiChargeReviewQueueItemVO item = page.getRecords().stream()
                .filter(candidate -> requestId.equals(candidate.getRequestId())).findFirst().orElseThrow();
        assertEquals(USER_ID, item.getUserId());
        assertMoneyEquals(reservedPoints, item.getReservedPoints());
        assertNotNull(item.getReason());
        assertFalse(item.toString().contains("requestFingerprint"));
        assertEquals("MANUAL_REVIEW", intentStatus());
    }

    private void prepareManualReview() {
        assertTrue(intentService.prepare(USER_ID, requestId, reservedPoints, MODEL_NAME, "f".repeat(64)));
        assertTrue(pointsService.preDeductPoints(USER_ID, requestId, MODEL_NAME));
        assertTrue(intentService.markReserved(requestId));
        assertTrue(intentService.markUpstreamStarted(requestId));
        assertTrue(intentService.markManualReview(requestId, "上游结果未知，等待人工核验"));
        jdbc.update("UPDATE ai_charge_intent SET update_time = ? WHERE request_id = ?",
                LocalDateTime.now().minusMinutes(10), requestId);
    }

    private String auditStatus() {
        return jdbc.queryForObject("SELECT status FROM ai_charge_review_audit WHERE request_id = ?",
                String.class, requestId);
    }

    private String intentStatus() {
        return jdbc.queryForObject("SELECT status FROM ai_charge_intent WHERE request_id = ?",
                String.class, requestId);
    }

    private BigDecimal databasePoints() {
        return jdbc.queryForObject("SELECT points FROM user WHERE id = ?", BigDecimal.class, USER_ID);
    }

    private BigDecimal redisPoints() {
        return new BigDecimal(redis.opsForValue().get("points:user:" + USER_ID));
    }

    private void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, requestId);
    }
}
