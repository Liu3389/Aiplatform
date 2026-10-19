package com.ai.gateway.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IpRateLimitInterceptorTest {

    private StringRedisTemplate redisTemplate;
    private IpRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        interceptor = new IpRateLimitInterceptor(redisTemplate);
    }

    @Test
    void rejectsCaptchaIssuanceAfterPerIpMinuteLimit() throws Exception {
        String key = "ai_gateway:captcha_issue_limit:198.51.100.24";
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"))).thenReturn(11L);
        MockHttpServletRequest request = captchaRequest("/auth/login/captcha");
        request.addHeader("X-Real-IP", "198.51.100.24");
        request.addHeader("X-Forwarded-For", "192.0.2.99, 198.51.100.24");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(429, response.getStatus());
        assertEquals(429, new ObjectMapper().readTree(response.getContentAsByteArray()).path("code").asInt());
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), eq("60"));
    }

    @Test
    void ignoresClientControlledForwardedForWhenNoTrustedRealIpIsPresent() throws Exception {
        String key = "ai_gateway:captcha_issue_limit:203.0.113.8";
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), eq("60"))).thenReturn(1L);
        MockHttpServletRequest request = captchaRequest("/auth/register/captcha");
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("X-Forwarded-For", "192.0.2.99");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), eq("60"));
    }

    @Test
    void otherAuthenticationRoutesRemainOutsideIpLimiter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(redisTemplate);
    }

    private MockHttpServletRequest captchaRequest(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
