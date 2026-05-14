package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.UserLoginRequest;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.LoginResponseVO;
import com.ai.gateway.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;

    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody UserRegisterRequest request) {
        UserInfoVO userInfo = userService.register(request);
        return Result.success("注册成功", userInfo);
    }

    @PostMapping("/login")
    public Result<LoginResponseVO> login(@Valid @RequestBody UserLoginRequest request, 
                                         @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
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
        } catch (Exception e) {
            tokenService.recordLoginFailure(request.getUsername());
            throw e;
        }
    }

    @PostMapping("/refresh-token")
    public Result<Map<String, String>> refreshToken(@RequestHeader("X-User-Token") String oldToken) {
        String newToken = tokenService.refreshToken(oldToken);
        if (newToken == null) {
            return Result.error(401, "Token无效或已过期，请重新登录");
        }
        return Result.success(Map.of("token", newToken));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("X-User-Token") String token) {
        tokenService.revokeToken(token);
        return Result.success("已退出登录", null);
    }

    @GetMapping("/userinfo")
    public Result<UserInfoVO> getUserInfo(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        UserInfoVO userInfo = userService.getUserInfo(userId);
        return Result.success(userInfo);
    }

    @GetMapping("/balance")
    public Result<BigDecimal> getBalance(@RequestHeader("X-User-Token") String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error(401, "Token无效或已过期");
        }
        BigDecimal balance = userService.getBalance(userId);
        return Result.success(balance);
    }

    @GetMapping("/oauth/url")
    public Result<Map<String, String>> getOAuthUrl(@RequestParam String provider) {
        String state = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> result = new HashMap<>();
        String redirectUri = "http://localhost:3000/oauth/callback";

        switch (provider.toLowerCase()) {
            case "github":
                result.put("url", "https://github.com/login/oauth/authorize?client_id=github_client_id&redirect_uri="
                        + redirectUri + "&scope=user:email&state=" + state);
                break;
            case "google":
                result.put("url", "https://accounts.google.com/o/oauth2/v2/auth?client_id=google_client_id&redirect_uri="
                        + redirectUri + "&response_type=code&scope=openid%20email%20profile&state=" + state);
                break;
            case "wechat":
                result.put("url", "https://open.weixin.qq.com/connect/qrconnect?appid=wechat_app_id&redirect_uri="
                        + redirectUri + "&response_type=code&scope=snsapi_login&state=" + state);
                break;
            default:
                return Result.error(400, "不支持的第三方登录方式");
        }
        result.put("state", state);
        result.put("provider", provider);
        return Result.success(result);
    }

    @PostMapping("/oauth/callback")
    public Result<LoginResponseVO> oauthCallback(@RequestBody Map<String, String> body) {
        String provider = body.get("provider");
        String code = body.get("code");
        String state = body.get("state");

        if (provider == null || code == null) {
            return Result.error(400, "缺少参数");
        }

        try {
            UserInfoVO userInfo = userService.oauthLogin(provider, code, state);
            String token = tokenService.generateToken(userInfo.getId());
            return Result.success("第三方登录成功", new LoginResponseVO(token, userInfo));
        } catch (Exception e) {
            log.error("OAuth登录失败: provider={}", provider, e);
            return Result.error(500, "第三方登录失败: " + e.getMessage());
        }
    }

    @PostMapping("/oauth/bind")
    public Result<Void> bindOAuth(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        String provider = body.get("provider");
        String code = body.get("code");
        if (provider == null || code == null) return Result.error(400, "缺少参数");

        userService.bindOAuth(userId, provider, code);
        return Result.success("绑定成功", null);
    }

    @PostMapping("/oauth/unbind")
    public Result<Void> unbindOAuth(@RequestHeader("X-User-Token") String token, @RequestBody Map<String, String> body) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");

        String provider = body.get("provider");
        if (provider == null) return Result.error(400, "缺少参数");

        userService.unbindOAuth(userId, provider);
        return Result.success("解绑成功", null);
    }
}
