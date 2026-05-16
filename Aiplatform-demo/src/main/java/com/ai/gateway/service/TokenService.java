package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
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

    private static final String TOKEN_PREFIX = "ai_gateway:token:";
    private static final String LOGIN_FAIL_PREFIX = "ai_gateway:login_fail:";
    private static final long TOKEN_TTL_HOURS = 24;
    private static final long REFRESH_THRESHOLD_HOURS = 1;
    private static final int MAX_LOGIN_FAIL = 5;
    private static final long LOGIN_LOCK_MINUTES = 15;

    // 修复 P1-13: 从配置文件读取 AES 密钥，避免硬编码
    @Value("${security.aes-key:}")
    private String aesKey;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final SessionService sessionService;

    public void validateAesKey() {
        if (aesKey == null || aesKey.isEmpty() || "AiGateway2026Key".equals(aesKey)) {
            throw new IllegalStateException("AES 密钥未配置，请设置 security.aes-key");
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
        log.info("Token生成: userId={}", userId);
        return token;
    }

    public Long getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String key = TOKEN_PREFIX + token;
        String userIdStr = redisTemplate.opsForValue().get(key);
        if (userIdStr == null) return null;
        refreshToken(token);
        return Long.parseLong(userIdStr);
    }

    public String refreshToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String key = TOKEN_PREFIX + token;
        String userIdStr = redisTemplate.opsForValue().get(key);
        if (userIdStr == null) return null;

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0 && ttl < REFRESH_THRESHOLD_HOURS * 3600) {
            redisTemplate.expire(key, TOKEN_TTL_HOURS, TimeUnit.HOURS);
            log.info("Token续期: ttl={}s", ttl);
        }
        return token;
    }

    public User getUserFromToken(String token) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) return null;
        return userService.getUserEntityById(userId);
    }

    public void revokeToken(String token) {
        if (token == null) return;
        redisTemplate.delete(TOKEN_PREFIX + token);
        log.info("Token已撤销");
    }

    public boolean validateToken(String token) {
        return getUserIdFromToken(token) != null;
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
