package com.ai.gateway.service;

import com.ai.gateway.entity.AiChargeIntent;
import com.ai.gateway.mapper.AiChargeIntentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChargeIntentService {

    private final AiChargeIntentMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean prepare(Long userId, String requestId, BigDecimal reservedPoints, String modelName,
                           String requestFingerprint) {
        AiChargeIntent intent = new AiChargeIntent();
        intent.setRequestId(requestId);
        intent.setRequestFingerprint(requestFingerprint);
        intent.setUserId(userId);
        intent.setReservedPoints(reservedPoints);
        intent.setActualPoints(reservedPoints);
        intent.setModelName(modelName);
        intent.setStatus(AiChargeIntent.PREPARING);
        intent.setReservationConfirmed(false);
        intent.setCreateTime(LocalDateTime.now());
        intent.setUpdateTime(intent.getCreateTime());
        // INSERT IGNORE and the request_id unique key elect exactly one upstream-call owner.
        return mapper.insertIfAbsent(intent) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markReserved(String requestId) {
        if (mapper.markReserved(requestId, LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && AiChargeIntent.RESERVED.equals(intent.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markUpstreamStarted(String requestId) {
        if (mapper.markUpstreamStarted(requestId, LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && AiChargeIntent.IN_FLIGHT.equals(intent.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markResponseReceived(String requestId, BigDecimal actualPoints) {
        if (mapper.markResponseReceived(requestId, actualPoints, LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && (AiChargeIntent.RESPONSE_RECEIVED.equals(intent.getStatus())
                || AiChargeIntent.MESSAGES_PERSISTED.equals(intent.getStatus())
                || AiChargeIntent.SETTLEMENT_PENDING.equals(intent.getStatus())
                || AiChargeIntent.SETTLED.equals(intent.getStatus()))
                && intent.getActualPoints() != null && intent.getActualPoints().compareTo(actualPoints) == 0;
    }

    /** Joins the message transaction so the durable-response marker cannot commit alone. */
    @Transactional(rollbackFor = Exception.class)
    public boolean markMessagesPersisted(String requestId) {
        if (mapper.markMessagesPersisted(requestId, LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && (AiChargeIntent.MESSAGES_PERSISTED.equals(intent.getStatus())
                || AiChargeIntent.SETTLEMENT_PENDING.equals(intent.getStatus())
                || AiChargeIntent.SETTLED.equals(intent.getStatus()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markSettlementPending(String requestId, BigDecimal actualPoints) {
        if (mapper.markSettlementPending(requestId, actualPoints, LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && (AiChargeIntent.MESSAGES_PERSISTED.equals(intent.getStatus())
                || AiChargeIntent.SETTLEMENT_PENDING.equals(intent.getStatus())
                || AiChargeIntent.SETTLED.equals(intent.getStatus()))
                && intent.getActualPoints() != null && intent.getActualPoints().compareTo(actualPoints) == 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markRefundPending(String requestId, String reason) {
        if (mapper.markRefundPending(requestId, safeReason(reason), LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && AiChargeIntent.REFUND_PENDING.equals(intent.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markRefunded(String requestId) {
        if (mapper.markRefunded(requestId, LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && AiChargeIntent.REFUNDED.equals(intent.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markSettled(String requestId) {
        if (mapper.markSettled(requestId, LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && AiChargeIntent.SETTLED.equals(intent.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markManualReview(String requestId, String reason) {
        if (mapper.markManualReview(requestId, safeReason(reason), LocalDateTime.now()) == 1) return true;
        AiChargeIntent intent = mapper.selectByRequestId(requestId);
        return intent != null && AiChargeIntent.MANUAL_REVIEW.equals(intent.getStatus());
    }

    @Transactional(readOnly = true)
    public AiChargeIntent getByRequestId(String requestId) {
        return mapper.selectByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public List<AiChargeIntent> recoveryCandidates(LocalDateTime staleBefore, int limit) {
        return mapper.selectRecoveryCandidates(staleBefore, limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claimRecovery(AiChargeIntent intent, LocalDateTime staleBefore, LocalDateTime now) {
        return mapper.claimForRecovery(intent.getId(), intent.getStatus(), staleBefore, now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releaseRecoveryClaim(AiChargeIntent intent, String reason) {
        mapper.releaseRecoveryClaim(intent.getId(), safeReason(reason));
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "AI 请求状态恢复";
        return reason.length() > 480 ? reason.substring(0, 480) : reason;
    }
}
