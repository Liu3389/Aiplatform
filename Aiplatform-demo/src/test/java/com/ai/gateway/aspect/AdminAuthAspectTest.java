package com.ai.gateway.aspect;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.controller.TestDataController;
import com.ai.gateway.entity.User;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthAspectTest {

    private TokenService tokenService;
    private UserService userService;
    private AdminAuthAspect aspect;
    private MockHttpServletRequest request;
    private RequireAdmin requireAdmin;

    @BeforeEach
    void setUp() throws Exception {
        tokenService = mock(TokenService.class);
        userService = mock(UserService.class);
        aspect = new AdminAuthAspect(tokenService, userService);
        request = new MockHttpServletRequest("GET", "/api/admin/test-data/check-conversation-cache");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method endpoint = TestDataController.class.getDeclaredMethod("checkConversationCache", String.class, Long.class);
        requireAdmin = endpoint.getAnnotation(RequireAdmin.class);
        assertNotNull(requireAdmin);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void everyTestDataEndpointIsProtectedByRequireAdmin() {
        List<Method> endpoints = Arrays.stream(TestDataController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        assertEquals(4, endpoints.size());
        endpoints.forEach(method -> assertNotNull(method.getAnnotation(RequireAdmin.class), method.getName()));
    }

    @Test
    void missingTokenIsUnauthorizedAndDoesNotInvokeEndpoint() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aspect.checkAdminPermission(joinPoint, requireAdmin));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), exception.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void ordinaryUserIsForbiddenAndDoesNotInvokeEndpoint() throws Throwable {
        request.addHeader("X-User-Token", "normal-user-token");
        when(tokenService.getUserIdFromToken("normal-user-token")).thenReturn(42L);
        User user = new User();
        user.setId(42L);
        user.setStatus(1);
        user.setRole("USER");
        when(userService.getUserEntityById(42L)).thenReturn(user);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aspect.checkAdminPermission(joinPoint, requireAdmin));

        assertEquals(ResultCode.FORBIDDEN.getCode(), exception.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void enabledAdminCanProceedAndDisabledAdminCannot() throws Throwable {
        request.addHeader("X-User-Token", "admin-token");
        when(tokenService.getUserIdFromToken("admin-token")).thenReturn(7L);
        User admin = new User();
        admin.setId(7L);
        admin.setStatus(1);
        admin.setRole("ADMIN");
        when(userService.getUserEntityById(7L)).thenReturn(admin);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("allowed");

        assertEquals("allowed", aspect.checkAdminPermission(joinPoint, requireAdmin));
        verify(joinPoint).proceed();

        User disabledAdmin = new User();
        disabledAdmin.setId(7L);
        disabledAdmin.setStatus(0);
        disabledAdmin.setRole("SUPER_ADMIN");
        when(userService.getUserEntityById(7L)).thenReturn(disabledAdmin);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aspect.checkAdminPermission(joinPoint, requireAdmin));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("禁用"));
    }
}
