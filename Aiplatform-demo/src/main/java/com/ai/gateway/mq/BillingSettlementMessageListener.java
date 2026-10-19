package com.ai.gateway.mq;

import com.ai.gateway.service.BillingSettlementOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Rabbit 消息仅包含 outbox 主键；结算金额和用户身份从数据库读取。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "rabbit-enabled", havingValue = "true")
public class BillingSettlementMessageListener {

    private final BillingSettlementOutboxService outboxService;

    @RabbitListener(queues = "${app.messaging.settlement-queue:ai.billing.settlement}")
    public void consume(String eventId) {
        outboxService.process(Long.valueOf(eventId));
    }
}
