package com.ai.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 按运行环境选择可靠异步结算或本地同步结算，聊天服务不需要知道 RabbitMQ 细节。 */
@Service
@RequiredArgsConstructor
public class BillingSettlementCoordinator {

    private final BillingSettlementOutboxService outboxService;
    private final AiChargeIntentService intentService;

    public boolean prepareReservation(Long userId, String requestId, BigDecimal reservedPoints, String modelName) {
        return intentService.prepare(userId, requestId, reservedPoints, modelName, null);
    }

    public boolean prepareReservation(Long userId, String requestId, BigDecimal reservedPoints,
                                     String modelName, String requestFingerprint) {
        return intentService.prepare(userId, requestId, reservedPoints, modelName, requestFingerprint);
    }

    public boolean markReserved(String requestId) {
        return intentService.markReserved(requestId);
    }

    public boolean markUpstreamStarted(String requestId) {
        return intentService.markUpstreamStarted(requestId);
    }

    public boolean markResponseReceived(String requestId, BigDecimal actualPoints) {
        return intentService.markResponseReceived(requestId, actualPoints);
    }

    public boolean markMessagesPersisted(String requestId) {
        return intentService.markMessagesPersisted(requestId);
    }

    public com.ai.gateway.entity.AiChargeIntent getIntent(String requestId) {
        return intentService.getByRequestId(requestId);
    }

    public boolean rejectReservation(Long userId, String requestId, String reason) {
        return outboxService.refundIntent(requestId, reason);
    }

    /** Settle only after the generated response has been durably stored; otherwise refund. */
    public boolean refundAfterFailure(Long userId, String requestId, String reason) {
        var intent = intentService.getByRequestId(requestId);
        if (intent == null) return true;
        if ("MESSAGES_PERSISTED".equals(intent.getStatus()) || "SETTLEMENT_PENDING".equals(intent.getStatus())
                || "SETTLED".equals(intent.getStatus())) {
            return settle(userId, requestId, intent.getActualPoints(), intent.getModelName());
        }
        if ("MANUAL_REVIEW".equals(intent.getStatus())) return false;
        return outboxService.refundIntent(requestId, reason);
    }

    /** Client disconnect during an active upstream call is ambiguous; retain the hold for reconciliation. */
    public boolean handleClientDisconnect(Long userId, String requestId) {
        var intent = intentService.getByRequestId(requestId);
        if (intent == null) return true;
        if ("IN_FLIGHT".equals(intent.getStatus())) {
            return intentService.markManualReview(requestId,
                    "客户端在上游响应完成前断开；保留预扣，需依据上游调用记录对账");
        }
        if ("MESSAGES_PERSISTED".equals(intent.getStatus()) || "SETTLEMENT_PENDING".equals(intent.getStatus())) {
            return settle(userId, requestId, intent.getActualPoints(), intent.getModelName());
        }
        if ("SETTLED".equals(intent.getStatus()) || "REFUNDED".equals(intent.getStatus())) return true;
        return outboxService.refundIntent(requestId, "客户端在调用上游前断开");
    }

    public boolean settle(Long userId, String requestId, BigDecimal actualPoints, String modelName) {
        if (!intentService.markSettlementPending(requestId, actualPoints)) return false;
        Long eventId = outboxService.enqueue(userId, requestId, actualPoints, modelName);
        // 结算意图提交后即获得可靠恢复点；未启用 RabbitMQ 时先本地处理，失败仍由扫描器重试。
        if (eventId == null) return false;
        boolean accepted = outboxService.processLocallyWhenRabbitDisabled(eventId);
        if (outboxService.isSettled(eventId)) intentService.markSettled(requestId);
        return accepted;
    }
}
