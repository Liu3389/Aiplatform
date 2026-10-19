package com.ai.gateway.service;

import com.ai.gateway.mapper.FileParseTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FileParseDispatchModeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DispatchModes.class);

    @Test
    void rabbitOffRegistersLocalRecoveryScanner() {
        contextRunner.withPropertyValues("app.messaging.rabbit-enabled=false").run(context -> {
            assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
            assertTrue(context.getBeanNamesForType(LocalFileParseTaskRecoveryService.class).length > 0);
            assertFalse(context.getBeanNamesForType(FileParseTaskDispatchService.class).length > 0);
        });
    }

    @Test
    void rabbitOnRegistersRabbitOutboxDispatcher() {
        contextRunner.withPropertyValues("app.messaging.rabbit-enabled=true").run(context -> {
            assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
            assertTrue(context.getBeanNamesForType(FileParseTaskDispatchService.class).length > 0);
            assertFalse(context.getBeanNamesForType(LocalFileParseTaskRecoveryService.class).length > 0);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({FileParseTaskDispatchService.class, LocalFileParseTaskRecoveryService.class})
    static class DispatchModes {
        @Bean FileParseTaskMapper fileParseTaskMapper() { return mock(FileParseTaskMapper.class); }
        @Bean RabbitEventPublisher rabbitEventPublisher() { return mock(RabbitEventPublisher.class); }
        @Bean @Qualifier("fileParseExecutor") java.util.concurrent.Executor fileParseExecutor() {
            return Runnable::run;
        }
    }
}
