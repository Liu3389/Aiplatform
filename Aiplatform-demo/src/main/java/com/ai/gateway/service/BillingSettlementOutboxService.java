package com.ai.gateway.service;

import com.ai.gateway.entity.BillingSettlementOutbox;
import com.ai.gateway.mapper.BillingSettlementOutboxMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 将结算意图持久化为 outbox 事件，并通过至少一次投递交给 RabbitMQ 消费端。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingSettlementOutboxService {

    private static final int PUBLISH_BATCH_SIZE = 100;
    private static final int REDELIVERY_AFTER_SECONDS = 30;

    private final BillingSettlementOutboxMapper outboxMapper;
    private final ObjectProvider<RabbitEventPublisher> rabbitEventPublisherProvider;
    private final PointsService pointsService;
    private final AiChargeIntentService intentService;

    /** 唯一 request_id 让聊天端的重试只产生一条结算意图。 */
    @Transactional(rollbackFor = Exception.class)
    public Long enqueue(Long userId, String requestId, BigDecimal actualPoints, String modelName) {
        BillingSettlementOutbox existing = outboxMapper.selectOne(new LambdaQueryWrapper<BillingSettlementOutbox>()
                .eq(BillingSettlementOutbox::getRequestId, requestId));
        if (existing != null) {
            return existing.getUserId().equals(userId)
                    && existing.getActualPoints().compareTo(actualPoints) == 0 ? existing.getId() : null;
        }

        BillingSettlementOutbox event = new BillingSettlementOutbox();
        event.setRequestId(requestId);
        event.setUserId(userId);
        event.setActualPoints(actualPoints);
        event.setModelName(modelName);
        event.setStatus("PENDING");
        event.setDispatchAttempts(0);
        event.setProcessAttempts(0);
        event.setCreateTime(LocalDateTime.now());
        outboxMapper.insert(event);
        return event.getId();
    }

    /** 关闭 Rabbit 时不关闭可靠性：先本地尝试，未完成事件仍保留给定时重试。 */
    public boolean processLocallyWhenRabbitDisabled(Long eventId) {
        if (rabbitEventPublisherProvider.getIfAvailable() == null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime staleBefore = now.minusSeconds(REDELIVERY_AFTER_SECONDS);
            if (outboxMapper.claimForPublish(eventId, staleBefore, now) == 1) process(eventId);
        }
        return true;
    }

    public boolean isSettled(Long eventId) {
        BillingSettlementOutbox event = outboxMapper.selectById(eventId);
        return event != null && "SETTLED".equals(event.getStatus());
    }

    public boolean refundIntent(String requestId, String reason) {
        var intent = intentService.getByRequestId(requestId);
        if (intent == null) return true;
        if ("REFUNDED".equals(intent.getStatus())) return true;
        if ("SETTLED".equals(intent.getStatus()) || "MESSAGES_PERSISTED".equals(intent.getStatus())
                || "SETTLEMENT_PENDING".equals(intent.getStatus())) return false;
        if (!intentService.markRefundPending(requestId, reason)) return false;
        intent = intentService.getByRequestId(requestId);
        if (intent == null) return false;
        boolean refunded = pointsService.rollbackPreDeduct(intent.getUserId(), requestId,
                intent.getReservedPoints(), Boolean.TRUE.equals(intent.getReservationConfirmed()));
        return refunded && intentService.markRefunded(requestId);
    }

    /**
     * 定时重试未完成结算。数据库行本身是可靠来源，Rabbit 只负责传递 ID；
     * 消息丢失、重复投递或应用重启都不会丢掉结算意图。
     */
    @Scheduled(fixedDelayString = "${app.messaging.outbox-poll-ms:10000}")
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(REDELIVERY_AFTER_SECONDS);
        List<BillingSettlementOutbox> candidates = outboxMapper.selectPublishCandidates(staleBefore, PUBLISH_BATCH_SIZE);
        for (BillingSettlementOutbox event : candidates) {
            if (outboxMapper.claimForPublish(event.getId(), staleBefore, now) != 1) continue;
            RabbitEventPublisher publisher = rabbitEventPublisherProvider.getIfAvailable();
            if (publisher == null) {
                process(event.getId());
            } else if (!publisher.publishSettlementEvent(event.getId())) {
                outboxMapper.markPublishFailure(event.getId(), "RabbitMQ 未确认或消息未路由");
            }
        }
    }

    /** 消费端以 outbox 状态和 Redis Lua 请求标记共同实现重复投递幂等。 */
    public void process(Long eventId) {
        BillingSettlementOutbox event = outboxMapper.selectById(eventId);
        if (event == null || "SETTLED".equals(event.getStatus())) return;
        if (!"PENDING".equals(event.getStatus())) return;

        try {
            var intent = intentService.getByRequestId(event.getRequestId());
            boolean settled = intent == null
                    ? pointsService.settlePoints(event.getUserId(), event.getRequestId(),
                            event.getActualPoints(), event.getModelName())
                    : pointsService.settlePoints(event.getUserId(), event.getRequestId(),
                            event.getActualPoints(), event.getModelName(), intent.getReservedPoints(),
                            Boolean.TRUE.equals(intent.getReservationConfirmed()));
            if (!settled) {
                outboxMapper.markProcessingFailure(eventId, "结算暂未完成，outbox 将在下一轮重试");
                return;
            }
            outboxMapper.markSettled(eventId, LocalDateTime.now());
            intentService.markSettled(event.getRequestId());
            pointsService.cleanupSettlementSnapshot(event.getRequestId());
        } catch (Exception e) {
            outboxMapper.markProcessingFailure(eventId, e.getMessage() == null ? "结算消费异常" : e.getMessage());
            log.error("点数结算消费失败，保留 outbox 等待重试: eventId={}, requestId={}",
                    eventId, event.getRequestId(), e);
        }
    }
}
