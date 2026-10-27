package com.ai.gateway.service;

import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.controller.AuthController;
import com.ai.gateway.common.Result;
import com.ai.gateway.entity.UserOAuthIdentity;
import com.ai.gateway.exception.OAuthException;
import com.ai.gateway.mapper.UserContactVerificationMapper;
import com.ai.gateway.mapper.UserOAuthIdentityMapper;
import com.ai.gateway.mapper.UserTwoFactorMapper;
import com.ai.gateway.vo.UserInfoVO;
import com.ai.gateway.vo.OAuthCallbackVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
class OAuthServiceLifecycleTest {
    @Autowired private OAuthService oauthService;
    @Autowired private UserService userService;
    @Autowired private UserOAuthIdentityMapper identityMapper;
    @Autowired private UserContactVerificationMapper contactVerificationMapper;
    @Autowired private UserTwoFactorService twoFactorService;
    @Autowired private UserTwoFactorMapper twoFactorMapper;
    @Autowired private TotpService totpService;
    @Autowired private AuthController authController;

    @MockBean private GoogleOidcClient googleOidcClient;
    @MockBean private QQConnectClient qqConnectClient;

    @BeforeEach
    void configureLocalGoogleProvider() {
        reset(googleOidcClient);
        when(googleOidcClient.isConfigured()).thenReturn(true);
        when(googleOidcClient.authorizationUrl(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> "https://accounts.google.com/oauth?state=" + invocation.getArgument(0));
        reset(qqConnectClient);
        when(qqConnectClient.isConfigured()).thenReturn(true);
        when(qqConnectClient.authorizationUrl(anyString()))
                .thenAnswer(invocation -> "https://graph.qq.com/oauth2.0/authorize?state=" + invocation.getArgument(0));
    }

    @Test
    void qqLoginCreatesProviderScopedAccountAndKeepsTheIdentityBound() {
        String subject = "local-qq-openid-" + System.nanoTime();
        when(qqConnectClient.exchange(eq("qq-one-time-code")))
                .thenReturn(new QQConnectClient.QQIdentity(subject, "本地 QQ 用户", "https://q.qlogo.cn/avatar.png"));

        String browser = oauthService.newBrowserBinding();
        Map<String, String> authorization = oauthService.begin("qq", "login", null, browser);
        assertTrue(authorization.get("url").contains("state="));
        OAuthService.OAuthLoginResult result = oauthService.complete("qq", "qq-one-time-code", authorization.get("state"), browser);

        assertEquals("login", result.mode());
        assertTrue(result.firstLogin());
        assertFalse(result.requiresTwoFactor());
        assertEquals("oauth_qq_" + subjectHashPrefix(subject), result.userInfo().getUsername());
        assertNull(result.userInfo().getEmail());
        assertEquals("本地 QQ 用户", result.userInfo().getDisplayName());
        assertEquals("https://q.qlogo.cn/avatar.png", result.userInfo().getAvatarUrl());
        UserOAuthIdentity stored = identityMapper.selectByProviderSubject("qq", subject);
        assertEquals(result.userId(), stored.getUserId());
        assertEquals("本地 QQ 用户", stored.getDisplayName());
        assertEquals("https://q.qlogo.cn/avatar.png", stored.getAvatarUrl());
        assertThrows(OAuthException.class, () -> oauthService.unbind(result.userId(), "qq"));
    }

    @Test
    void loginStateIsBoundToProviderAndConsumedOnce() {
        String subject = "local-login-sub-" + System.nanoTime();
        when(googleOidcClient.exchangeAndVerify(eq("one-time-code"), anyString(), anyString()))
                .thenReturn(identity(subject));

        String browser = oauthService.newBrowserBinding();
        Map<String, String> authorization = oauthService.begin("google", "login", null, browser);
        assertTrue(authorization.get("url").contains("state="));
        assertThrows(OAuthException.class, () -> oauthService.complete("google", "one-time-code",
                authorization.get("state"), oauthService.newBrowserBinding()));
        OAuthService.OAuthLoginResult result = oauthService.complete("google", "one-time-code", authorization.get("state"), browser);

        assertEquals("login", result.mode());
        assertFalse(result.requiresTwoFactor());
        assertNotNull(result.userId());
        assertEquals("oauth_google_" + subjectHashPrefix(subject), result.userInfo().getUsername());
        assertEquals(emailForSubject(subject), result.userInfo().getEmail());
        assertTrue(result.userInfo().isEmailVerified());
        assertEquals(emailForSubject(subject), contactVerificationMapper.selectById(result.userId()).getEmail());
        UserOAuthIdentity stored = identityMapper.selectByProviderSubject("google", subject);
        assertEquals(result.userId(), stored.getUserId());

        Map<String, String> repeatAuthorization = oauthService.begin("google", "login", null, browser);
        OAuthService.OAuthLoginResult repeatResult = oauthService.complete("google", "one-time-code",
                repeatAuthorization.get("state"), browser);
        assertEquals(result.userId(), repeatResult.userId());
        assertFalse(repeatResult.firstLogin());
        assertTrue(repeatResult.userInfo().isEmailVerified());

        assertThrows(OAuthException.class,
                () -> oauthService.complete("google", "one-time-code", authorization.get("state"), browser));
    }

    @Test
    void controllerSetsHttpOnlyBrowserBindingCookieAndIssuesNoReusableState() {
        String subject = "local-controller-sub-" + System.nanoTime();
        when(googleOidcClient.exchangeAndVerify(eq("controller-code"), anyString(), anyString()))
                .thenReturn(identity(subject));
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();

        Result<Map<String, String>> authorization = authController.getOAuthUrl("google", "login", null, request, response);
        assertEquals(200, authorization.getCode());
        String cookieHeader = response.getHeader("Set-Cookie");
        assertNotNull(cookieHeader);
        assertTrue(cookieHeader.contains("HttpOnly"));
        assertTrue(cookieHeader.contains("SameSite=Lax"));
        assertTrue(cookieHeader.contains("Path=/api/auth/oauth"));
        String browserBinding = cookieHeader.substring(0, cookieHeader.indexOf(';')).split("=", 2)[1];

        Result<OAuthCallbackVO> callback = authController.oauthCallback(
                Map.of("provider", "google", "code", "controller-code", "state", authorization.getData().get("state")),
                browserBinding);
        assertEquals(200, callback.getCode());
        assertEquals("login", callback.getData().getMode());
        assertTrue(callback.getData().isFirstLogin());
        assertNotNull(callback.getData().getToken());

        Result<OAuthCallbackVO> replay = authController.oauthCallback(
                Map.of("provider", "google", "code", "controller-code", "state", authorization.getData().get("state")),
                browserBinding);
        assertEquals(400, replay.getCode());
    }

    @Test
    void mobileQqCallbackWorksWhenBrowserDoesNotRestoreLocalStorageOrBindingCookie() {
        String subject = "local-mobile-qq-sub-" + System.nanoTime();
        when(qqConnectClient.exchange(eq("mobile-qq-code")))
                .thenReturn(new QQConnectClient.QQIdentity(subject, "移动端 QQ 用户", "https://q.qlogo.cn/avatar.png"));
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();

        Result<Map<String, String>> authorization = authController.getOAuthUrl("qq", "login", null, request, response);
        assertEquals(200, authorization.getCode());
        String cookieHeader = response.getHeader("Set-Cookie");
        String browserBinding = cookieHeader.substring(0, cookieHeader.indexOf(';')).split("=", 2)[1];

        Result<OAuthCallbackVO> callback = authController.oauthCallback(
                Map.of("code", "mobile-qq-code", "state", authorization.getData().get("state")), null);
        assertEquals(200, callback.getCode());
        assertEquals("qq", callback.getData().getProvider());
        assertEquals("login", callback.getData().getMode());
        assertNotNull(callback.getData().getToken());
    }

    @Test
    void providerStatusExposesOnlyConfigurationFlagsForTheLoginPage() {
        when(qqConnectClient.isConfigured()).thenReturn(false);
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();

        when(qqConnectClient.reviewStatus()).thenReturn("unconfigured");
        Result<Map<String, Object>> result = authController.getOAuthProviders(response);

        assertEquals(200, result.getCode());
        assertEquals(Map.of("google", true, "qq", false, "qqReviewStatus", "unconfigured"), result.getData());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void bindingRequiresAuthenticatedOwnerAndRejectsIdentityAlreadyOwnedByAnotherUser() {
        UserInfoVO firstUser = register("oauth_bind_first");
        UserInfoVO secondUser = register("oauth_bind_second");
        String subject = "local-bind-sub-" + System.nanoTime();
        when(googleOidcClient.exchangeAndVerify(eq("bind-code"), anyString(), anyString()))
                .thenReturn(identity(subject));

        String browser = oauthService.newBrowserBinding();
        Map<String, String> firstFlow = oauthService.begin("google", "bind", firstUser.getId(), browser);
        OAuthService.OAuthLoginResult bound = oauthService.complete("google", "bind-code", firstFlow.get("state"), browser);
        assertEquals("bind", bound.mode());
        assertNull(bound.userInfo().getEmail());
        assertFalse(bound.userInfo().isEmailVerified());
        assertEquals(firstUser.getId(), identityMapper.selectByProviderSubject("google", subject).getUserId());
        assertEquals(1, oauthService.bindings(firstUser.getId()).size());

        Map<String, String> secondFlow = oauthService.begin("google", "bind", secondUser.getId(), browser);
        OAuthException conflict = assertThrows(OAuthException.class,
                () -> oauthService.complete("google", "bind-code", secondFlow.get("state"), browser));
        assertEquals(409, conflict.getStatus());
        assertThrows(OAuthException.class, () -> oauthService.begin("google", "bind", null, browser));
    }

    @Test
    void oauthLoginCannotSkipEnabledTwoFactorAndChallengeCanOnlyCompleteOnce() {
        UserInfoVO user = register("oauth_2fa");
        Map<String, String> enrollment = twoFactorService.beginEnrollment(user.getId());
        String secret = enrollment.get("secret");
        twoFactorService.confirmEnrollment(user.getId(), totpService.codeForStep(secret, System.currentTimeMillis() / 30_000L));
        assertTrue(Boolean.TRUE.equals(twoFactorMapper.selectById(user.getId()).getEnabled()));

        String subject = "local-2fa-sub-" + System.nanoTime();
        when(googleOidcClient.exchangeAndVerify(anyString(), anyString(), anyString())).thenReturn(identity(subject));
        String browser = oauthService.newBrowserBinding();
        Map<String, String> binding = oauthService.begin("google", "bind", user.getId(), browser);
        oauthService.complete("google", "bind-code", binding.get("state"), browser);
        Map<String, String> authorization = oauthService.begin("google", "login", null, browser);
        OAuthService.OAuthLoginResult login = oauthService.complete("google", "oauth-code", authorization.get("state"), browser);
        assertTrue(login.requiresTwoFactor());
        assertNotNull(login.challengeToken());

        String code = totpService.codeForStep(secret, System.currentTimeMillis() / 30_000L + 1);
        UserInfoVO completed = oauthService.completeTwoFactor(login.challengeToken(), code);
        assertEquals(user.getId(), completed.getId());
        assertThrows(OAuthException.class, () -> oauthService.completeTwoFactor(login.challengeToken(), code));
    }

    private UserInfoVO register(String prefix) {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername(prefix + "_" + System.nanoTime());
        request.setPassword("OAuthLifecycle123!");
        return userService.register(request);
    }

    private GoogleOidcClient.GoogleOidcIdentity identity(String subject) {
        return new GoogleOidcClient.GoogleOidcIdentity(subject, "Local Google User", emailForSubject(subject));
    }

    private String emailForSubject(String subject) {
        return subjectHashPrefix(subject).substring(0, 16) + "@oauth.test";
    }

    private String subjectHashPrefix(String subject) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
