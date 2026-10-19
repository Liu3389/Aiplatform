package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.UserLoginRequest;
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

    @Operation(summary = "获取注册图形验证码", description = "验证码有效期5分钟，校验后立即失效")
    @GetMapping("/register/captcha")
    public Result<Map<String, Object>> registerCaptcha() {
        return Result.success(captchaService.generateRegisterCaptcha());
    }

    @Operation(summary = "获取登录图形验证码", description = "验证码有效期5分钟，校验后立即失效")
    @GetMapping("/login/captcha")
    public Result<Map<String, Object>> loginCaptcha() {
        return Result.success(captchaService.generateLoginCaptcha());
    }

    @Operation(summary = "用户注册", description = "用户名+密码+邮箱注册，注册成功返回用户信息")
    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody UserRegisterRequest request) {
        if (!captchaService.validateAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            return Result.error(400, "图形验证码错误或已过期，请刷新后重试");
        }
        UserInfoVO userInfo = userService.register(request);
        return Result.success("注册成功", userInfo);
    }

    @Operation(summary = "用户登录", description = "校验用户名密码，返回 X-User-Token 令牌（后续接口需携带）与用户信息")
    @PostMapping("/login")
    public Result<LoginResponseVO> login(@Valid @RequestBody UserLoginRequest request,
                                         @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        if (!captchaService.validateLoginAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            return Result.error(400, "图形验证码错误或已过期，请刷新后重试");
        }

        if (tokenService.isAccountLocked(request.getUsername())) {
            long count = tokenService.getLoginFailCount(request.getUsername());
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
            
            tokenService.clearLoginFailures(request.getUsername());
            log.info("登录成功: username={}, deviceId={}", request.getUsername(), deviceId);
            
            LoginResponseVO response = new LoginResponseVO(token, userInfo);
            response.setDeviceId(deviceId); // 返回设备ID给前端
            
            return Result.success("登录成功", response);
        } catch (TwoFactorRequiredException e) {
            // The password was correct, but no session or token is issued until the next factor passes.
            return Result.error(428, e.getMessage());
        } catch (Exception e) {
            tokenService.recordLoginFailure(request.getUsername());
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

    @Operation(summary = "获取第三方 OAuth 授权链接", description = "当前仅启用已配置的 Google OIDC")
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

        if (provider == null || code == null || state == null) {
            return Result.error(400, "缺少参数");
        }

        try {
            OAuthService.OAuthLoginResult login = oauthService.complete(provider, code, state, browserBinding);
            OAuthCallbackVO response = new OAuthCallbackVO();
            response.setMode(login.mode());
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
        String deviceId = UUID.randomUUID().toString();
        String token = tokenService.generateToken(userInfo.getId());
        userService.createSession(userInfo.getId(), deviceId, token);
        LoginResponseVO response = new LoginResponseVO(token, userInfo);
        response.setDeviceId(deviceId);
        return response;
    }
}
