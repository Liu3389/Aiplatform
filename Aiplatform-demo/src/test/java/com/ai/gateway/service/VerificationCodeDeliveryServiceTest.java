package com.ai.gateway.service;

import com.ai.gateway.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class VerificationCodeDeliveryServiceTest {

    @Test
    void deliveryIsClosedWhenNoChannelIsConfigured() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        VerificationCodeDeliveryService service = new VerificationCodeDeliveryService(redisTemplate, mailSenderProvider);
        ReflectionTestUtils.setField(service, "deliveryMode", "disabled");
        ReflectionTestUtils.setField(service, "allowLocalCapture", false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deliver(1L, "email", "person@example.test", "123456"));
        assertEquals(503, error.getCode());
        verifyNoInteractions(redisTemplate, mailSenderProvider);
    }

    @Test
    void localCaptureCannotRunWithoutTheExplicitLocalOptIn() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        VerificationCodeDeliveryService service = new VerificationCodeDeliveryService(redisTemplate, mailSenderProvider);
        ReflectionTestUtils.setField(service, "deliveryMode", "local");
        ReflectionTestUtils.setField(service, "allowLocalCapture", false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deliver(1L, "phone", "13900000000", "123456"));
        assertEquals(503, error.getCode());
        verifyNoInteractions(redisTemplate, mailSenderProvider);
    }
}
