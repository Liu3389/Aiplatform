package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserOAuthIdentity;
import com.ai.gateway.exception.OAuthException;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserOAuthIdentityMapper;
import com.ai.gateway.vo.OAuthBindingVO;
import com.ai.gateway.vo.UserInfoVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {
    private static final String STATE_PREFIX = "oauth:state:";
    private static final String TWO_FACTOR_PREFIX = "oauth:2fa:";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value",
            String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GoogleOidcClient googleOidcClient;
    private final UserOAuthIdentityMapper identityMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final BillingService billingService;
    private final UserTwoFactorService twoFactorService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Map<String, String> begin(String provider, String purpose, Long authenticatedUserId, String browserBinding) {
        String normalizedProvider = normalizeProvider(provider);
        if (!"google".equals(normalizedProvider)) throw new OAuthException(400, "当前仅支持 Google OIDC");
        if (!googleOidcClient.isConfigured()) throw new OAuthException(503, "Google OAuth 尚未配置");
        if (browserBinding == null || browserBinding.length() < 32 || browserBinding.length() > 256) {
            throw new OAuthException(400, "浏览器授权状态无效，请重新发起");
        }

        String normalizedPurpose = purpose == null || purpose.isBlank() ? "login" : purpose.toLowerCase(Locale.ROOT);
        if (!"login".equals(normalizedPurpose) && !"bind".equals(normalizedPurpose)) {
            throw new OAuthException(400, "OAuth 用途无效");
        }
        if ("bind".equals(normalizedPurpose) && authenticatedUserId == null) {
            throw new OAuthException(401, "绑定第三方账号需要先登录");
        }

        String state = randomToken(32);
        String nonce = randomToken(32);
        String codeVerifier = randomToken(48);
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(codeVerifier));
        OAuthState record = new OAuthState(normalizedProvider, normalizedPurpose, authenticatedUserId,
                nonce, codeVerifier, sha256Hex(browserBinding), LocalDateTime.now());
        try {
            redisTemplate.opsForValue().set(STATE_PREFIX + sha256Hex(state), objectMapper.writeValueAsString(record), STATE_TTL);
        } catch (Exception e) {
            throw new OAuthException(503, "OAuth 暂不可用，请稍后重试");
        }

        return Map.of("url", googleOidcClient.authorizationUrl(state, nonce, codeChallenge),
                "state", state, "provider", normalizedProvider, "purpose", normalizedPurpose);
    }

    @Transactional(rollbackFor = Exception.class)
    public OAuthLoginResult complete(String provider, String code, String state, String browserBinding) {
        String normalizedProvider = normalizeProvider(provider);
        if (!"google".equals(normalizedProvider)) throw new OAuthException(400, "当前仅支持 Google OIDC");
        if (state == null || state.length() < 32 || state.length() > 256
                || browserBinding == null || browserBinding.length() < 32 || browserBinding.length() > 256) {
            throw new OAuthException(400, "OAuth state 无效或已过期");
        }

        String stateKey = STATE_PREFIX + sha256Hex(state);
        String pendingState = redisTemplate.opsForValue().get(stateKey);
        if (pendingState == null) throw new OAuthException(400, "OAuth state 无效或已过期");
        OAuthState flow;
        try {
            flow = objectMapper.readValue(pendingState, OAuthState.class);
        } catch (Exception e) {
            throw new OAuthException(400, "OAuth state 无效或已过期");
        }
        if (!normalizedProvider.equals(flow.provider()) || flow.nonce() == null || flow.codeVerifier() == null
                || !constantTimeEquals(flow.browserBindingHash(), sha256Hex(browserBinding))) {
            throw new OAuthException(400, "OAuth state 无效或已过期");
        }
        String storedState = redisTemplate.execute(GET_AND_DELETE, List.of(stateKey));
        if (storedState == null) throw new OAuthException(400, "OAuth state 无效或已过期");

        GoogleOidcClient.GoogleOidcIdentity identity = googleOidcClient.exchangeAndVerify(code, flow.codeVerifier(), flow.nonce());
        if ("bind".equals(flow.purpose())) {
            bindIdentity(flow.userId(), identity);
            return new OAuthLoginResult("bind", flow.userId(), userService.getUserInfo(flow.userId()), false, null);
        }

        UserOAuthIdentity link = identityMapper.selectByProviderSubject(normalizedProvider, identity.subject());
        User user;
        if (link == null) {
            user = createOAuthUser(identity);
            link = new UserOAuthIdentity();
            link.setUserId(user.getId());
            link.setProvider(normalizedProvider);
            link.setProviderSubject(identity.subject());
            link.setDisplayName(identity.displayName());
            link.setEmail(identity.verifiedEmail());
            link.setCreateTime(LocalDateTime.now());
            identityMapper.insert(link);
            billingService.syncUserBalanceToRedis(user.getId(), user.getBalance());
        } else {
            user = userMapper.selectById(link.getUserId());
            if (user == null) throw new OAuthException(401, "账号身份已失效，请联系管理员");
        }

        // Only OAuth-created accounts derive their profile email from the provider.
        // A Google identity linked to an existing password account must not replace
        // that account's independently verified contact information.
        if (user.getUsername() != null && user.getUsername().startsWith("oauth_google_")) {
            userService.bindOAuthVerifiedEmail(user.getId(), identity.verifiedEmail());
        }

        assertAccountEnabled(user);
        boolean requiresTwoFactor = twoFactorService.isEnabled(user.getId());
        String challenge = requiresTwoFactor ? createTwoFactorChallenge(user.getId()) : null;
        return new OAuthLoginResult("login", user.getId(), userService.getUserInfo(user.getId()), requiresTwoFactor, challenge);
    }

    public UserInfoVO completeTwoFactor(String challengeToken, String code) {
        if (challengeToken == null || challengeToken.length() < 32 || challengeToken.length() > 256
                || code == null || code.isBlank()) {
            throw new OAuthException(400, "二次验证信息无效或已过期");
        }
        String challengeKey = TWO_FACTOR_PREFIX + sha256Hex(challengeToken);
        String userIdValue = redisTemplate.opsForValue().get(challengeKey);
        if (userIdValue == null) throw new OAuthException(400, "二次验证信息无效或已过期");

        String attemptKey = challengeKey + ":attempts";
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1L) redisTemplate.expire(attemptKey, STATE_TTL);
        if (attempts == null || attempts > 5) {
            redisTemplate.delete(List.of(challengeKey, attemptKey));
            throw new OAuthException(429, "二次验证码尝试次数过多，请重新登录");
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdValue);
        } catch (NumberFormatException e) {
            redisTemplate.delete(List.of(challengeKey, attemptKey));
            throw new OAuthException(400, "二次验证信息无效或已过期");
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw new OAuthException(401, "账号不可用");
        assertAccountEnabled(user);
        if (!twoFactorService.verifyAndConsumeLoginCode(userId, code)) {
            throw new OAuthException(401, "二次验证码错误");
        }

        String consumed = redisTemplate.execute(GET_AND_DELETE, List.of(challengeKey));
        if (consumed == null) throw new OAuthException(400, "二次验证信息已使用");
        redisTemplate.delete(attemptKey);
        return userService.getUserInfo(userId);
    }

    @Transactional(readOnly = true)
    public List<OAuthBindingVO> bindings(Long userId) {
        return identityMapper.selectByUserId(userId).stream()
                .map(link -> new OAuthBindingVO(link.getProvider(), link.getDisplayName(), link.getEmail()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId, String provider) {
        String normalizedProvider = normalizeProvider(provider);
        UserOAuthIdentity link = identityMapper.selectByUserAndProvider(userId, normalizedProvider);
        if (link == null) return;
        User user = userMapper.selectById(userId);
        List<UserOAuthIdentity> all = identityMapper.selectByUserId(userId);
        if (user != null && user.getUsername() != null && user.getUsername().startsWith("oauth_google_") && all.size() <= 1) {
            throw new OAuthException(409, "请先设置密码或绑定其他登录方式，再解除最后一个登录账号");
        }
        identityMapper.deleteById(link.getId());
    }

    private void bindIdentity(Long userId, GoogleOidcClient.GoogleOidcIdentity identity) {
        if (userId == null) throw new OAuthException(400, "OAuth 绑定状态无效");
        User user = userMapper.selectById(userId);
        assertAccountEnabled(user);
        UserOAuthIdentity existingProviderIdentity = identityMapper.selectByProviderSubject("google", identity.subject());
        if (existingProviderIdentity != null && !userId.equals(existingProviderIdentity.getUserId())) {
            throw new OAuthException(409, "该 Google 账号已绑定到其他平台账号");
        }
        UserOAuthIdentity existingUserIdentity = identityMapper.selectByUserAndProvider(userId, "google");
        if (existingUserIdentity != null && !identity.subject().equals(existingUserIdentity.getProviderSubject())) {
            throw new OAuthException(409, "当前平台账号已绑定其他 Google 账号，请先解绑");
        }
        if (existingUserIdentity == null) {
            UserOAuthIdentity link = new UserOAuthIdentity();
            link.setUserId(userId);
            link.setProvider("google");
            link.setProviderSubject(identity.subject());
            link.setDisplayName(identity.displayName());
            link.setEmail(identity.verifiedEmail());
            link.setCreateTime(LocalDateTime.now());
            identityMapper.insert(link);
        }
    }

    private User createOAuthUser(GoogleOidcClient.GoogleOidcIdentity identity) {
        String username = "oauth_google_" + sha256Hex(identity.subject()).substring(0, 32);
        User usernameCollision = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (usernameCollision != null) throw new OAuthException(409, "第三方账号映射冲突，请联系管理员");

        byte[] passwordBytes = new byte[48];
        RANDOM.nextBytes(passwordBytes);
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(passwordBytes)));
        user.setEmail(identity.verifiedEmail());
        user.setBalance(BigDecimal.ZERO);
        user.setPoints(BigDecimal.ZERO);
        user.setStatus(1);
        user.setRole("USER");
        user.setFreeApiStrategy("NONE");
        user.setFreeQuota(0);
        user.setDailyCallLimit(0);
        user.setMonthlyCallLimit(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private String createTwoFactorChallenge(Long userId) {
        String token = randomToken(32);
        String key = TWO_FACTOR_PREFIX + sha256Hex(token);
        redisTemplate.opsForValue().set(key, userId.toString(), STATE_TTL);
        return token;
    }

    private void assertAccountEnabled(User user) {
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new OAuthException(401, "账号不可用");
        }
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank() || provider.length() > 32) {
            throw new OAuthException(400, "OAuth 提供方无效");
        }
        return provider.toLowerCase(Locale.ROOT);
    }

    private static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newBrowserBinding() {
        return randomToken(32);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    private record OAuthState(String provider, String purpose, Long userId, String nonce,
                              String codeVerifier, String browserBindingHash, LocalDateTime createdAt) { }

    public record OAuthLoginResult(String mode, Long userId, UserInfoVO userInfo,
                                   boolean requiresTwoFactor, String challengeToken) { }
}
