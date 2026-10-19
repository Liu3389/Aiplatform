package com.ai.gateway.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/** RabbitMQ 只在部署显式启用时创建监听器和队列，便于无 Broker 的本地开发。 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "app.messaging", name = "rabbit-enabled", havingValue = "true")
public class RabbitMqConfig {

    public static final String EXCHANGE = "ai.platform.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "ai.platform.dlx";
    public static final String FILE_PARSE_ROUTE = "ai.file.parse";
    public static final String SETTLEMENT_ROUTE = "ai.billing.settlement";

    @Bean
    public DirectExchange aiPlatformExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange aiDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue fileParseQueue(@Value("${app.messaging.file-parse-queue:ai.file.parse}") String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(FILE_PARSE_ROUTE + ".dead")
                .build();
    }

    @Bean
    public Queue fileParseDeadLetterQueue() {
        return QueueBuilder.durable("ai.file.parse.dlq").build();
    }

    @Bean
    public Queue billingSettlementQueue(@Value("${app.messaging.settlement-queue:ai.billing.settlement}") String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(SETTLEMENT_ROUTE + ".dead")
                .build();
    }

    @Bean
    public Queue billingSettlementDeadLetterQueue() {
        return QueueBuilder.durable("ai.billing.settlement.dlq").build();
    }

    @Bean
    public Binding fileParseBinding(@Qualifier("fileParseQueue") Queue fileParseQueue,
                                    @Qualifier("aiPlatformExchange") DirectExchange aiPlatformExchange) {
        return BindingBuilder.bind(fileParseQueue).to(aiPlatformExchange).with(FILE_PARSE_ROUTE);
    }

    @Bean
    public Binding settlementBinding(@Qualifier("billingSettlementQueue") Queue billingSettlementQueue,
                                     @Qualifier("aiPlatformExchange") DirectExchange aiPlatformExchange) {
        return BindingBuilder.bind(billingSettlementQueue).to(aiPlatformExchange).with(SETTLEMENT_ROUTE);
    }

    @Bean
    public Binding fileParseDeadLetterBinding(@Qualifier("fileParseDeadLetterQueue") Queue fileParseDeadLetterQueue,
                                              @Qualifier("aiDeadLetterExchange") DirectExchange aiDeadLetterExchange) {
        return BindingBuilder.bind(fileParseDeadLetterQueue).to(aiDeadLetterExchange).with(FILE_PARSE_ROUTE + ".dead");
    }

    @Bean
    public Binding settlementDeadLetterBinding(@Qualifier("billingSettlementDeadLetterQueue") Queue billingSettlementDeadLetterQueue,
                                               @Qualifier("aiDeadLetterExchange") DirectExchange aiDeadLetterExchange) {
        return BindingBuilder.bind(billingSettlementDeadLetterQueue).to(aiDeadLetterExchange).with(SETTLEMENT_ROUTE + ".dead");
    }
}
