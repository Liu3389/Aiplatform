package com.ai.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {

    private StringRedisTemplate redisTemplate;
    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        captchaService = new CaptchaService(redisTemplate);
    }

    @Test
    void loginCaptchaUsesSeparateNamespaceAndIsConsumedOnce() {
        String loginKey = "ai_gateway:captcha:login:captcha-id";
        when(redisTemplate.execute(any(RedisScript.class), eq(java.util.List.of(loginKey))))
                .thenReturn("ABCDE", null);

        assertTrue(captchaService.validateLoginAndConsume("captcha-id", "abcde"));
        assertFalse(captchaService.validateLoginAndConsume("captcha-id", "ABCDE"));

        verify(redisTemplate, times(2)).execute(any(RedisScript.class), eq(java.util.List.of(loginKey)));
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    void registrationCaptchaCannotBeUsedForLogin() {
        assertFalse(captchaService.validateLoginAndConsume("captcha-id", "ABCDE"));

        verify(redisTemplate).execute(any(RedisScript.class), eq(java.util.List.of("ai_gateway:captcha:login:captcha-id")));
        verifyNoMoreInteractions(redisTemplate);
    }
}
