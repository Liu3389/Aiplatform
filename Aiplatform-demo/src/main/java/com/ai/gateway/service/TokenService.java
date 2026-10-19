package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    static final String TOKEN_PREFIX = "ai_gateway:token:";
    static final String TOKEN_DEVICE_PREFIX = "ai_gateway:token_device:";
    static final String USER_TOKENS_PREFIX = "ai_gateway:user_tokens:";
    private static final String LOGIN_FAIL_PREFIX = "ai_gateway:login_fail:";
    private static final long TOKEN_TTL_HOURS = 24;
    private static final long REFRESH_THRESHOLD_HOURS = 1;
    private static final int MAX_LOGIN_FAIL = 5;
    private static final long LOGIN_LOCK_MINUTES = 15;

    private final com.ai.gateway.util.RedisScanUtil redisScanUtil;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final SessionService sessionService;
    // 修复 P1-13: 从配置文件读取 AES 密钥，避免硬编码
    @Value("${api.encryption.key:}")
    private String aesKey;

    public void validateAesKey() {
        if (aesKey == null || aesKey.isEmpty() || "AiGateway2026Key".equals(aesKey)) {
            throw new IllegalStateException("AES 密钥未配置，请设置 api.encryption.key 或环境变量 API_ENCRYPTION_KEY");
        }
    }

    @PostConstruct
    public void init() {
        validateAesKey();
    }

    // === Token Management ===

    public String generateToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), TOKEN_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForSet().add(USER_TOKENS_PREFIX + userId, token);
        redisTemplate.expire(USER_TOKENS_PREFIX + userId, TOKEN_TTL_HOURS, TimeUnit.HOURS);
        log.info("Token生成: userId={}", userId);
        return token;
    }

    public Long getUserIdFromToken(String token) {
        return resolveUserId(token, true);
    }

    public String refreshToken(String token) {
        return resolveUserId(token, true) == null ? null : token;
    }

    public User getUserFromToken(String token) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) return null;
        return userService.getUserEntityById(userId);
    }

    public void revokeToken(String token) {
        if (token == null || token.isBlank()) return;
        String tokenKey = TOKEN_PREFIX + token;
        String userIdValue = redisTemplate.opsForValue().get(tokenKey);
        String deviceId = redisTemplate.opsForValue().get(TOKEN_DEVICE_PREFIX + token);
        Long userId = parseUserId(userIdValue);
        boolean activeSessionRevoked = userId != null && deviceId != null
                && sessionService.destroySession(userId, deviceId, token);
        redisTemplate.delete(tokenKey);
        redisTemplate.delete(TOKEN_DEVICE_PREFIX + token);
        if (userId != null) redisTemplate.opsForSet().remove(USER_TOKENS_PREFIX + userId, token);
        if (activeSessionRevoked && userId != null) clearUserSessionCaches(userId);
        log.info("Token已撤销: userId={}", userId);
    }

    public boolean validateToken(String token) {
        return getUserIdFromToken(token) != null;
    }

    private Long resolveUserId(String token, boolean refresh) {
        if (token == null || token.isBlank()) return null;
        String tokenKey = TOKEN_PREFIX + token;
        String userIdValue = redisTemplate.opsForValue().get(tokenKey);
        if (userIdValue == null) return null;
        Long userId = parseUserId(userIdValue);
        String deviceId = redisTemplate.opsForValue().get(TOKEN_DEVICE_PREFIX + token);
        if (userId == null || deviceId == null || !sessionService.validateSession(userId, deviceId, token)) {
            revokeToken(token);
            return null;
        }
        User user = userService.getUserEntityById(userId);
        if (user == null || user.getStatus() == com.ai.gateway.common.Constants.USER_STATUS_DISABLED) {
            revokeToken(token);
            return null;
        }
        if (refresh) {
            Long ttl = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0 && ttl < REFRESH_THRESHOLD_HOURS * 3600) {
                redisTemplate.expire(tokenKey, TOKEN_TTL_HOURS, TimeUnit.HOURS);
                log.info("Token续期: ttl={}s", ttl);
            }
            sessionService.refreshSession(userId, deviceId, token);
        }
        return userId;
    }

    private Long parseUserId(String userIdValue) {
        if (userIdValue == null) return null;
        try {
            return Long.parseLong(userIdValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void clearUserSessionCaches(Long userId) {
        try {
            redisTemplate.delete("conversations:" + userId);
            redisTemplate.delete("conversations:version:" + userId);
            redisScanUtil.deleteByPattern("chat:messages:v2:" + userId + ":*");
            redisScanUtil.deleteByPattern("free:limit:*:" + userId + ":*");
            redisTemplate.delete("membership:" + userId);
        } catch (Exception e) {
            log.error("清除用户会话缓存失败: userId={}", userId, e);
        }
    }

    // === Login Failure Lockout ===

    public void recordLoginFailure(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_LOGIN_FAIL) {
            log.warn("登录失败次数过多，账户锁定: username={}, count={}", username, count);
        }
    }

    public boolean isAccountLocked(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) return false;
        long count = Long.parseLong(val);
        return count >= MAX_LOGIN_FAIL;
    }

    public void clearLoginFailures(String username) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
    }

    public long getLoginFailCount(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        String val = redisTemplate.opsForValue().get(key);
        return val == null ? 0 : Long.parseLong(val);
    }

    // === API Key AES Encryption ===

    public String encryptApiKey(String plainKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("API Key加密失败", e);
            return plainKey;
        }
    }

    public String decryptApiKey(String encryptedKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(encryptedKey);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("API Key解密失败", e);
            return encryptedKey;
        }
    }
}
