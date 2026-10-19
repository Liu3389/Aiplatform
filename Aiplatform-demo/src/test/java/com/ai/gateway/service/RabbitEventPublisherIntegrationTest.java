package com.ai.gateway.service;

import com.ai.gateway.config.RabbitMqConfig;
import com.ai.gateway.mq.FileParseMessageListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Opt-in test against a real, isolated RabbitMQ broker. Run with -Drabbit.it=true
 * and rabbit.host/port/username/password system properties. Credentials are required
 * at runtime and are deliberately not stored in the repository.
 */
@EnabledIfSystemProperty(named = "rabbit.it", matches = "true")
class RabbitEventPublisherIntegrationTest {

    @Test
    void confirmsPersistentRoutedMessageAndRejectsMandatoryReturn() {
        try (AnnotationConfigApplicationContext context = newContext(false)) {
            RabbitEventPublisher publisher = context.getBean(RabbitEventPublisher.class);
            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            RabbitAdmin admin = context.getBean(RabbitAdmin.class);

            long taskId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE - 1);
            assertTrue(publisher.publishFileParseTask(taskId), "bound durable queue should be confirmed");

            Message received = template.receive("ai.file.parse", 5_000);
            assertNotNull(received, "confirmed message should be routable and consumable");
            assertEquals(Long.toString(taskId), new String(received.getBody(), StandardCharsets.UTF_8));
            assertEquals(MessageDeliveryMode.PERSISTENT,
                    received.getMessageProperties().getReceivedDeliveryMode());

            Binding binding = context.getBean("fileParseBinding", Binding.class);
            admin.removeBinding(binding);
            try {
                assertFalse(publisher.publishFileParseTask(taskId + 1),
                        "mandatory return must make the publisher reject an unroutable message");
            } finally {
                admin.declareBinding(binding);
            }
        }
    }

    @Test
    void fileParseListenerAcknowledgesAfterSuccessfulDispatch() {
        try (AnnotationConfigApplicationContext context = newContext(true)) {
            long taskId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE - 1);
            assertTrue(context.getBean(RabbitEventPublisher.class).publishFileParseTask(taskId));
            verify(context.getBean(FileParseService.class), timeout(5_000)).processFileTask(taskId);
            assertEquals(0, context.getBean(RabbitAdmin.class)
                    .getQueueInfo("ai.file.parse").getMessageCount());
        }
    }

    private AnnotationConfigApplicationContext newContext(boolean enableListener) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = Map.of(
                "app.messaging.rabbit-enabled", "true",
                "rabbit.host", System.getProperty("rabbit.host", "127.0.0.1"),
                "rabbit.port", System.getProperty("rabbit.port", "15672"),
                "rabbit.username", requiredProperty("rabbit.username"),
                "rabbit.password", requiredProperty("rabbit.password"));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rabbit-it", properties));
        context.register(TestRabbitConfiguration.class);
        if (enableListener) context.register(FileParseMessageListener.class);
        context.refresh();
        context.getBean(RabbitAdmin.class).initialize();
        if (enableListener) context.getBean(RabbitListenerEndpointRegistry.class).start();
        return context;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Opt-in RabbitMQ integration test requires -D" + name);
        }
        return value;
    }

    @Configuration(proxyBeanMethods = false)
    @Import({RabbitMqConfig.class, RabbitEventPublisher.class})
    static class TestRabbitConfiguration {
        @Bean
        CachingConnectionFactory rabbitConnectionFactory(
                @Value("${rabbit.host}") String host,
                @Value("${rabbit.port}") int port,
                @Value("${rabbit.username}") String username,
                @Value("${rabbit.password}") String password) {
            CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setPublisherConfirmType(ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
            factory.setConnectionTimeout(5_000);
            return factory;
        }

        @Bean
        RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
            RabbitTemplate template = new RabbitTemplate(connectionFactory);
            template.setMandatory(true);
            return template;
        }

        @Bean
        RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        @Bean
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
            factory.setDefaultRequeueRejected(false);
            factory.setAutoStartup(false);
            return factory;
        }

        @Bean
        FileParseService fileParseService() {
            return mock(FileParseService.class);
        }

        @Bean
        @Qualifier("fileParseExecutor")
        Executor fileParseExecutor() {
            return Runnable::run;
        }
    }
}
