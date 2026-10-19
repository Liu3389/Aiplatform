package com.ai.gateway.service;

import com.ai.gateway.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 统一负责持久化消息投递，并等待 Broker confirm 与 mandatory return 结果。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "rabbit-enabled", havingValue = "true")
public class RabbitEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public boolean publishFileParseTask(Long taskId) {
        return publish(RabbitMqConfig.FILE_PARSE_ROUTE, taskId.toString());
    }

    public boolean publishSettlementEvent(Long eventId) {
        return publish(RabbitMqConfig.SETTLEMENT_ROUTE, eventId.toString());
    }

    private boolean publish(String route, String body) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, route, body, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            boolean routed = correlation.getReturned() == null;
            if (!confirm.isAck() || !routed) {
                log.warn("RabbitMQ 未确认消息: route={}, ack={}, returned={}", route,
                        confirm.isAck(), !routed);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("RabbitMQ 消息投递失败: route={}", route, e);
            return false;
        }
    }
}
