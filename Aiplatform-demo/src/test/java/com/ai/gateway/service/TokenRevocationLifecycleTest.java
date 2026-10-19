package com.ai.gateway.service;

import com.ai.gateway.common.Result;
import com.ai.gateway.controller.AuthController;
import com.ai.gateway.controller.UserController;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.interceptor.ApiKeyAuthenticationFilter;
import com.ai.gateway.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TokenRevocationLifecycleTest {

    @Autowired private UserService userService;
    @Autowired private AdminService adminService;
    @Autowired private TokenService tokenService;
    @Autowired private SessionService sessionService;
    @Autowired private AuthController authController;
    @Autowired private UserController userController;
    @Autowired private ApiKeyAuthenticationFilter authenticationFilter;

    @Test
    void deviceReplacementRemovalLogoutAndExceptCurrentRevokeOnlyIntendedTokens() {
        Long userId = registerUser().getId();
        String oldToken = issueToken(userId, "old-device");
        assertEquals(userId, tokenService.getUserIdFromToken(oldToken));

        String currentToken = issueToken(userId, "current-device");
        assertNull(tokenService.getUserIdFromToken(oldToken), "new login must invalidate the prior device token");
        assertEquals(userId, tokenService.getUserIdFromToken(currentToken));

        tokenService.revokeToken(oldToken);
        assertEquals(userId, tokenService.getUserIdFromToken(currentToken), "stale logout must not evict a newer session");

        userService.removeAllLoginDevices(userId, true);
        assertEquals(userId, tokenService.getUserIdFromToken(currentToken), "exceptCurrent must preserve the validated active token");
        assertEquals(1, userService.getLoginDevices(userId).size());

        userService.removeLoginDevice(userId, "current-device");
        assertNull(tokenService.getUserIdFromToken(currentToken), "removing the active device must revoke its token");
        assertTrue(userService.getLoginDevices(userId).isEmpty());

        String logoutToken = issueToken(userId, "logout-device");
        Result<Void> logout = authController.logout(logoutToken);
        assertEquals(200, logout.getCode());
        assertNull(tokenService.getUserIdFromToken(logoutToken), "logout must revoke its exact session token");

        String allDevicesToken = issueToken(userId, "all-devices-device");
        userService.removeAllLoginDevices(userId, false);
        assertNull(tokenService.getUserIdFromToken(allDevicesToken), "all-device logout must revoke every user token");
    }

    @Test
    void disabledAndAdminBannedUsersLoseActiveSessions() {
        Long selfDisabledUserId = registerUser().getId();
        String selfDisabledToken = issueToken(selfDisabledUserId, "self-disabled-device");
        userService.toggleUserStatus(selfDisabledUserId, "DISABLED");
        assertNull(tokenService.getUserIdFromToken(selfDisabledToken));

        Long adminBannedUserId = registerUser().getId();
        String adminBannedToken = issueToken(adminBannedUserId, "admin-banned-device");
        assertEquals(1, adminService.batchUpdateStatus(java.util.List.of(adminBannedUserId), 0));
        assertNull(tokenService.getUserIdFromToken(adminBannedToken));
    }

    @Test
    void revokedTokenFailsOrdinaryApiAndChatAuthentication() throws Exception {
        Long userId = registerUser().getId();
        String token = issueToken(userId, "filter-device");

        MockHttpServletRequest validRequest = chatRequest(token);
        MockHttpServletResponse validResponse = new MockHttpServletResponse();
        MockFilterChain validChain = new MockFilterChain();
        authenticationFilter.doFilter(validRequest, validResponse, validChain);
        assertNotNull(validChain.getRequest(), "a valid user token must reach the chat handler");

        tokenService.revokeToken(token);
        Result<?> ordinaryApiResponse = userController.getUserInfo(token);
        assertEquals(401, ordinaryApiResponse.getCode(), "ordinary API controllers must reject a revoked token");

        MockHttpServletRequest revokedRequest = chatRequest(token);
        MockHttpServletResponse revokedResponse = new MockHttpServletResponse();
        MockFilterChain revokedChain = new MockFilterChain();
        authenticationFilter.doFilter(revokedRequest, revokedResponse, revokedChain);
        assertNull(revokedChain.getRequest(), "revoked tokens must not reach chat or SSE handlers");
        assertTrue(revokedResponse.getContentAsString().contains("Token"));
        assertFalse(sessionService.hasActiveSession(userId));
    }

    private MockHttpServletRequest chatRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/completions");
        request.setContextPath("/api");
        request.setServletPath("/chat/completions");
        request.addHeader("X-User-Token", token);
        return request;
    }

    private String issueToken(Long userId, String deviceId) {
        String token = tokenService.generateToken(userId);
        assertTrue(sessionService.createSession(userId, deviceId, token));
        return token;
    }

    private UserInfoVO registerUser() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("token_revoke_" + System.nanoTime());
        request.setPassword("TokenRevokeLocal123!");
        return userService.register(request);
    }
}
