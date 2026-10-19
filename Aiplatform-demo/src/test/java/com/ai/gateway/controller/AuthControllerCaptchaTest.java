package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.UserLoginRequest;
import com.ai.gateway.service.CaptchaService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.exception.TwoFactorRequiredException;
import com.ai.gateway.vo.LoginResponseVO;
import com.ai.gateway.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerCaptchaTest {

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private AuthController authController;

    @Test
    void invalidCaptchaRejectsLoginBeforeCheckingCredentialsOrAccountLock() {
        UserLoginRequest request = loginRequest();
        when(captchaService.validateLoginAndConsume("captcha-id", "ABCDE")).thenReturn(false);

        Result<LoginResponseVO> result = authController.login(request, null);

        assertEquals(400, result.getCode());
        assertEquals("图形验证码错误或已过期，请刷新后重试", result.getMessage());
        verifyNoInteractions(userService, tokenService);
    }

    @Test
    void validCaptchaAllowsNormalLoginAndCreatesSession() {
        UserLoginRequest request = loginRequest();
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(42L);
        when(captchaService.validateLoginAndConsume("captcha-id", "ABCDE")).thenReturn(true);
        when(tokenService.isAccountLocked("alice")).thenReturn(false);
        when(userService.login(request)).thenReturn(userInfo);
        when(tokenService.generateToken(42L)).thenReturn("session-token");

        Result<LoginResponseVO> result = authController.login(request, "device-1");

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("session-token", result.getData().getToken());
        assertEquals("device-1", result.getData().getDeviceId());
        verify(userService).createSession(42L, "device-1", "session-token");
        verify(tokenService).clearLoginFailures("alice");
        verify(tokenService, never()).recordLoginFailure(anyString());
    }

    @Test
    void missingSecondFactorReturnsChallengeWithoutCreatingSessionOrToken() {
        UserLoginRequest request = loginRequest();
        when(captchaService.validateLoginAndConsume("captcha-id", "ABCDE")).thenReturn(true);
        when(tokenService.isAccountLocked("alice")).thenReturn(false);
        when(userService.login(request)).thenThrow(new TwoFactorRequiredException());

        Result<LoginResponseVO> result = authController.login(request, "device-1");

        assertEquals(428, result.getCode());
        assertEquals("账户已启用两步验证，请输入动态验证码或备用码", result.getMessage());
        verify(tokenService, never()).generateToken(42L);
        verify(tokenService, never()).clearLoginFailures(anyString());
        verify(tokenService, never()).recordLoginFailure(anyString());
        verify(userService, never()).createSession(org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString());
    }

    private UserLoginRequest loginRequest() {
        UserLoginRequest request = new UserLoginRequest();
        request.setUsername("alice");
        request.setPassword("ValidPass123");
        request.setCaptchaId("captcha-id");
        request.setCaptchaCode("ABCDE");
        return request;
    }
}
