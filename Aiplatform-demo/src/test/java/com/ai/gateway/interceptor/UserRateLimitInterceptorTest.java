package com.ai.gateway.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRateLimitInterceptorTest {

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOperations;
    private UserRateLimitInterceptor interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), eq(65L), eq(TimeUnit.SECONDS))).thenReturn(true);
        interceptor = new UserRateLimitInterceptor(redisTemplate);
    }

    @Test
    void rejectsUserTokenWhenSlidingWindowExceedsConfiguredLimit() throws Exception {
        when(zSetOperations.zCard("rate_limit:user:42")).thenReturn(61L);
        MockHttpServletRequest request = authenticatedRequest(42L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(429, response.getStatus());
        assertEquals(429, new ObjectMapper().readTree(response.getContentAsByteArray()).path("code").asInt());
    }

    @Test
    void keepsUsersInSeparateRateLimitWindows() throws Exception {
        when(zSetOperations.zCard("rate_limit:user:42")).thenReturn(61L);
        when(zSetOperations.zCard("rate_limit:user:84")).thenReturn(1L);

        assertFalse(interceptor.preHandle(authenticatedRequest(42L), new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(authenticatedRequest(84L), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void apiKeyUsesItsOwnLimiterAndDoesNotConsumeUserLimit() throws Exception {
        MockHttpServletRequest request = authenticatedRequest(42L);
        request.setAttribute("apiKey", "sk-local-api-key");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(redisTemplate, zSetOperations);
    }

    @Test
    void asyncSseRedispatchDoesNotConsumeAnotherUserRequest() throws Exception {
        MockHttpServletRequest request = authenticatedRequest(42L);
        request.setDispatcherType(DispatcherType.ASYNC);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verify(redisTemplate, never()).opsForZSet();
        verify(redisTemplate, never()).expire(anyString(), eq(65L), eq(TimeUnit.SECONDS));
    }

    @Test
    void eachRequestGetsAUniqueSlidingWindowMember() throws Exception {
        when(zSetOperations.zCard("rate_limit:user:42")).thenReturn(1L);
        interceptor.preHandle(authenticatedRequest(42L), new MockHttpServletResponse(), new Object());
        interceptor.preHandle(authenticatedRequest(42L), new MockHttpServletResponse(), new Object());

        var memberCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(zSetOperations, org.mockito.Mockito.times(2))
                .add(eq("rate_limit:user:42"), memberCaptor.capture(), anyDouble());
        assertNotEquals(memberCaptor.getAllValues().get(0), memberCaptor.getAllValues().get(1));
    }

    private MockHttpServletRequest authenticatedRequest(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/chat/completions");
        request.setAttribute("userId", userId);
        return request;
    }
}
