package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.UserLoginRequest;
import com.ai.gateway.dto.RegistrationEmailCodeRequest;
import com.ai.gateway.dto.LoginEmailCodeRequest;
import com.ai.gateway.dto.EmailCodeLoginRequest;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.service.CaptchaService;
import com.ai.gateway.service.OAuthService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.exception.OAuthException;
import com.ai.gateway.exception.TwoFactorRequiredException;
import com.ai.gateway.vo.LoginResponseVO;
import com.ai.gateway.vo.OAuthCallbackVO;
import com.ai.gateway.vo.OAuthBindingVO;
import com.ai.gateway.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "01-认证中心", description = "用户注册、登录、Token 管理、第三方 OAuth")
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final OAuthService oauthService;

    @Value("${app.verification.registration-email-required:false}")
    private boolean registrationEmailRequired;

    @Operation(summary = "查询注册方式配置")
    @GetMapping("/register/config")
    public Result<Map<String, Boolean>> registerConfig() {
        return Result.success(Map.of("emailVerificationRequired", registrationEmailRequired));
    }

    @Operation(summary = "获取注册图形验证码", description = "验证码有效期5分钟，校验后立即失效")
    @GetMapping("/register/captcha")
    public Result<Map<String, Object>> registerCaptcha() {
        return Result.success(captchaService.generateRegisterCaptcha());
    }

    @Operation(summary = "发送注册邮箱验证码", description = "图形验证码通过后发送一次性邮箱验证码，10分钟有效")
    @PostMapping("/register/email-code")
    public Result<Void> sendRegistrationEmailCode(@Valid @RequestBody RegistrationEmailCodeRequest request) {
        if (!captchaService.validateAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            return Result.error(400, "图形验证码错误或已过期，请刷新后重试");
        }
        userService.sendRegistrationEmailCode(request.getEmail());
        return Result.success("邮箱验证码已发送，请查收", null);
    }

    @Operation(summary = "获取登录图形验证码", description = "验证码有效期5分钟，校验后立即失效")
    @GetMapping("/login/captcha")
    public Result<Map<String, Object>> loginCaptcha() {
        return Result.success(captchaService.generateLoginCaptcha());
    }

    @Operation(summary = "用户注册", description = "用户名+密码+邮箱注册，注册成功返回用户信息")
    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody UserRegisterRequest request) {
        UserInfoVO userInfo;
        if (registrationEmailRequired) {
            if (request.getEmail() == null || request.getEmail().isBlank()
                    || request.getEmailCode() == null || !request.getEmailCode().matches("\\d{6}")) {
                return Result.error(400, "请填写邮箱并输入6位邮箱验证码");
            }
            userInfo = userService.registerWithVerifiedEmail(request);
        } else {
            if (!captchaService.validateAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
                return Result.error(400, "图形验证码错误或已过期，请刷新后重试");
            }
            userInfo = userService.register(request);
        }
        return Result.success("注册成功", userInfo);
    }

    @Operation(summary = "用户登录", description = "校验用户名或已验证邮箱及密码，返回 X-User-Token 令牌（后续接口需携带）与用户信息")
    @PostMapping("/login")
    public Result<LoginResponseVO> login(@Valid @RequestBody UserLoginRequest request,
                                         @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        if (!captchaService.validateLoginAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            return Result.error(400, "图形验证码错误或已过期，请刷新后重试");
        }

        String loginIdentifier = request.getUsername().trim();
        if (loginIdentifier.indexOf('@') >= 0) {
            loginIdentifier = loginIdentifier.toLowerCase(java.util.Locale.ROOT);
        }

        if (tokenService.isAccountLocked(loginIdentifier)) {
            long count = tokenService.getLoginFailCount(loginIdentifier);
            return Result.error(423, "账户已被临时锁定（登录失败" + count + "次），请15分钟后再试");
        }

        try {
            UserInfoVO userInfo = userService.login(request);
            
            // 如果没有提供设备ID，生成一个
            if (deviceId == null || deviceId.isEmpty()) {
                deviceId = java.util.UUID.randomUUID().toString();
            }
            
            // 创建会话（会检查并踢掉旧设备）
            String token = tokenService.generateToken(userInfo.getId());
            userService.createSession(userInfo.getId(), deviceId, token);
            
            tokenService.clearLoginFailures(loginIdentifier);
            log.info("登录成功: username={}, deviceId={}", userInfo.getUsername(), deviceId);
            
            LoginResponseVO response = new LoginResponseVO(token, userInfo);
            response.setDeviceId(deviceId); // 返回设备ID给前端
            
            return Result.success("登录成功", response);
        } catch (TwoFactorRequiredException e) {
            // The password was correct, but no session or token is issued until the next factor passes.
            return Result.error(428, e.getMessage());
        } catch (Exception e) {
            tokenService.recordLoginFailure(loginIdentifier);
            throw e;
        }
    }

    @Operation(summary = "发送邮箱登录验证码", description = "校验图形验证码后向已注册邮箱发送一次性登录验证码，10分钟有效")
    @PostMapping("/login/email-code")
    public Result<Void> sendLoginEmailCode(@Valid @RequestBody LoginEmailCodeRequest request) {
        if (!captchaService.validateLoginAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            return Result.error(400, "图形验证码错误或已过期，请刷新后重试");
        }
        userService.sendLoginEmailCode(request.getEmail());
        return Result.success("邮箱验证码已发送，请查收", null);
    }

    @Operation(summary = "邮箱验证码登录", description = "校验邮箱验证码并签发 X-User-Token 令牌；启用两步验证的账号还需二次验证码")
    @PostMapping("/login/email")
    public Result<LoginResponseVO> loginWithEmailCode(@Valid @RequestBody EmailCodeLoginRequest request,
                                                      @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (tokenService.isAccountLocked(email)) {
            long count = tokenService.getLoginFailCount(email);
            return Result.error(423, "账户已被临时锁定（登录失败" + count + "次），请15分钟后再试");
        }

        try {
            UserInfoVO userInfo = userService.loginWithEmailCode(request);
            tokenService.clearLoginFailures(email);
            LoginResponseVO response = issueLoginSession(userInfo, deviceId);
            log.info("邮箱验证码登录成功: userId={}, deviceId={}", userInfo.getId(), response.getDeviceId());
            return Result.success("登录成功", response);
        } catch (TwoFactorRequiredException e) {
            return Result.error(428, "邮箱验证码已验证，请输入动态验证码或一次性备用码");
        } catch (Exception e) {
            tokenService.recordLoginFailure(email);
            throw e;
        }
    }

    @Operation(summary = "刷新 Token", description = "用旧 Token 换取新 Token")
    @PostMapping("/refresh-token")
    public Result<Map<String, String>> refreshToken(@RequestHeader("X-User-Token") String oldToken) {
        String newToken = tokenService.refreshToken(oldToken);
        if (newToken == null) {
            return Result.error(401, "Token无效或已过期，请重新登录");
        }
        return Result.success(Map.of("token", newToken));
    }

    @Operation(summary = "退出登录", description = "吊销当前 Token")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("X-User-Token") String token) {
        tokenService.revokeToken(token);
        return Result.success("已退出登录", null);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/userinfo")
    public Result<UserInfoVO> getUserInfo(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        UserInfoVO userInfo = userService.getUserInfo(userId);
        return Result.success(userInfo);
    }

    @Operation(summary = "查询账户余额")
    @GetMapping("/balance")
    public Result<BigDecimal> getBalance(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        BigDecimal balance = userService.getBalance(userId);
        return Result.success(balance);
    }

    @Operation(summary = "查看第三方登录方式状态", description = "仅返回已配置状态，不返回 OAuth 凭据")
    @GetMapping("/oauth/providers")
    public Result<Map<String, Object>> getOAuthProviders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(oauthService.providerAvailability());
    }

    @Operation(summary = "获取第三方 OAuth 授权链接", description = "支持已配置的 Google OIDC 与 QQ Connect")
    @GetMapping("/oauth/url")
    public Result<Map<String, String>> getOAuthUrl(@RequestParam String provider,
                                                    @RequestParam(required = false) String purpose,
                                                    @RequestHeader(value = "X-User-Token", required = false) String token,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        try {
            Long userId = null;
            if ("bind".equalsIgnoreCase(purpose)) {
                userId = tokenService.getUserIdFromToken(token);
                if (userId == null) return Result.error(401, "绑定第三方账号需要先登录");
            }
            String browserBinding = Arrays.stream(request.getCookies() == null ? new Cookie[0] : request.getCookies())
                    .filter(cookie -> "aiplatform_oauth_browser".equals(cookie.getName()))
                    .map(Cookie::getValue).findFirst().orElse(null);
            if (browserBinding == null || browserBinding.length() < 32 || browserBinding.length() > 256) {
                browserBinding = oauthService.newBrowserBinding();
            }
            boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            ResponseCookie cookie = ResponseCookie.from("aiplatform_oauth_browser", browserBinding)
                    .httpOnly(true).secure(secure).sameSite("Lax").path("/api/auth/oauth")
                    .maxAge(Duration.ofMinutes(10)).build();
            response.addHeader("Set-Cookie", cookie.toString());
            response.setHeader("Cache-Control", "no-store");
            return Result.success(oauthService.begin(provider, purpose, userId, browserBinding));
        } catch (OAuthException e) {
            return Result.error(e.getStatus(), e.getMessage());
        }
    }

    @Operation(summary = "第三方 OAuth 回调登录", description = "用授权 code 换取用户信息并登录，返回 Token")
    @PostMapping("/oauth/callback")
    public Result<OAuthCallbackVO> oauthCallback(@RequestBody Map<String, String> body,
                                                 @CookieValue(value = "aiplatform_oauth_browser", required = false) String browserBinding) {
        String provider = body.get("provider");
        String code = body.get("code");
        String state = body.get("state");

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return Result.error(400, "缺少参数");
        }

        try {
            OAuthService.OAuthLoginResult login = oauthService.complete(provider, code, state, browserBinding);
            OAuthCallbackVO response = new OAuthCallbackVO();
            response.setMode(login.mode());
            response.setProvider(login.provider());
            if ("bind".equals(login.mode())) {
                return Result.success("第三方账号绑定成功", response);
            }
            if (login.requiresTwoFactor()) {
                response.setRequiresTwoFactor(true);
                response.setChallengeToken(login.challengeToken());
                return Result.success("请输入二次验证码", response);
            }
            LoginResponseVO session = issueLoginSession(login.userInfo());
            response.setToken(session.getToken());
            response.setDeviceId(session.getDeviceId());
            response.setUserInfo(login.userInfo());
            response.setFirstLogin(login.firstLogin());
            return Result.success("第三方登录成功", response);
        } catch (OAuthException e) {
            log.warn("OAuth callback rejected: provider={}, reason={}", provider, e.getClass().getSimpleName());
            return Result.error(e.getStatus(), e.getMessage());
        } catch (Exception e) {
            log.warn("OAuth callback failed: provider={}, reason={}", provider, e.getClass().getSimpleName());
            return Result.error(502, "第三方登录暂不可用");
        }
    }

    @Operation(summary = "完成 OAuth 登录二次验证")
    @PostMapping("/oauth/complete")
    public Result<LoginResponseVO> completeOAuthTwoFactor(@RequestBody Map<String, String> body) {
        try {
            UserInfoVO userInfo = oauthService.completeTwoFactor(body.get("challengeToken"), body.get("twoFactorCode"));
            return Result.success("第三方登录成功", issueLoginSession(userInfo));
        } catch (OAuthException e) {
            return Result.error(e.getStatus(), e.getMessage());
        }
    }

    @Operation(summary = "查询已绑定的第三方账号")
    @GetMapping("/oauth/bindings")
    public Result<List<OAuthBindingVO>> oauthBindings(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        return Result.success(oauthService.bindings(userId));
    }

    @Operation(summary = "绑定第三方 OAuth 账号")
    @PostMapping("/oauth/bind")
    public Result<Void> bindOAuth(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        String provider = body.get("provider");
        String code = body.get("code");
        if (provider == null || code == null) return Result.error(400, "缺少参数");

        return Result.error(400, "请通过 OAuth 授权链接完成绑定");
    }

    @Operation(summary = "解绑第三方 OAuth 账号")
    @PostMapping("/oauth/unbind")
    public Result<Void> unbindOAuth(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        String provider = body.get("provider");
        if (provider == null) return Result.error(400, "缺少参数");

        try {
            oauthService.unbind(userId, provider);
            return Result.success("解绑成功", null);
        } catch (OAuthException e) {
            return Result.error(e.getStatus(), e.getMessage());
        }
    }

    private LoginResponseVO issueLoginSession(UserInfoVO userInfo) {
        return issueLoginSession(userInfo, null);
    }

    private LoginResponseVO issueLoginSession(UserInfoVO userInfo, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString();
        }
        String token = tokenService.generateToken(userInfo.getId());
        userService.createSession(userInfo.getId(), deviceId, token);
        LoginResponseVO response = new LoginResponseVO(token, userInfo);
        response.setDeviceId(deviceId);
        return response;
    }
}
