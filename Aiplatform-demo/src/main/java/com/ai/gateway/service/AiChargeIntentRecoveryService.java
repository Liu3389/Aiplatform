package com.ai.gateway.service;

import com.ai.gateway.entity.AiChargeIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Recovers durable AI charge intents after a process restart. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChargeIntentRecoveryService {

    private static final int BATCH_SIZE = 100;
    private static final int ACTIVE_REQUEST_TIMEOUT_MINUTES = 5;

    private final AiChargeIntentService intentService;
    private final BillingSettlementCoordinator settlementCoordinator;
    private final BillingSettlementOutboxService outboxService;
    private final ChatMessageService chatMessageService;

    @Scheduled(fixedDelayString = "${app.messaging.intent-recovery-ms:10000}")
    public void recoverStaleIntents() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusMinutes(ACTIVE_REQUEST_TIMEOUT_MINUTES);
        for (AiChargeIntent intent : intentService.recoveryCandidates(staleBefore, BATCH_SIZE)) {
            if (!intentService.claimRecovery(intent, staleBefore, now)) continue;
            try {
                switch (intent.getStatus()) {
                    case AiChargeIntent.PREPARING, AiChargeIntent.RESERVED, AiChargeIntent.REFUND_PENDING ->
                            outboxService.refundIntent(intent.getRequestId(), "预扣请求超时后由恢复扫描器退款");
                    case AiChargeIntent.RESPONSE_RECEIVED -> {
                        if (chatMessageService.findByRequestIdAndRole(intent.getRequestId(), "assistant") == null) {
                            outboxService.refundIntent(intent.getRequestId(), "上游响应未能持久化为聊天消息，已退款");
                            break;
                        }
                        if (!intentService.markMessagesPersisted(intent.getRequestId())) {
                            intentService.releaseRecoveryClaim(intent, "找到聊天回复，但消息持久化状态更新失败");
                            break;
                        }
                        boolean queued = settlementCoordinator.settle(intent.getUserId(), intent.getRequestId(),
                                intent.getActualPoints(), intent.getModelName());
                        if (!queued) intentService.releaseRecoveryClaim(intent, "消息已持久化，但结算意图恢复失败");
                    }
                    case AiChargeIntent.MESSAGES_PERSISTED, AiChargeIntent.SETTLEMENT_PENDING -> {
                        boolean queued = settlementCoordinator.settle(intent.getUserId(), intent.getRequestId(),
                                intent.getActualPoints(), intent.getModelName());
                        if (!queued) intentService.releaseRecoveryClaim(intent, "响应已收到，但结算意图恢复失败");
                    }
                    case AiChargeIntent.IN_FLIGHT -> {
                        intentService.markManualReview(intent.getRequestId(),
                                "上游请求超时且无完整响应记录；预扣保留，需根据供应商日志对账");
                        log.error("AI 请求进入人工对账: userId={}, requestId={}, model={}",
                                intent.getUserId(), intent.getRequestId(), intent.getModelName());
                    }
                    default -> intentService.releaseRecoveryClaim(intent, "不支持的 AI 点数请求状态");
                }
            } catch (Exception e) {
                intentService.releaseRecoveryClaim(intent, e.getMessage());
                log.error("AI 点数请求恢复失败: requestId={}, status={}", intent.getRequestId(), intent.getStatus(), e);
            }
        }
    }
}
