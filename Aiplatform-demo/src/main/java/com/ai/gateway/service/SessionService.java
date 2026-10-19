package com.ai.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Redis-backed single-device sessions tied to the exact access token. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {
    private static final String USER_SESSION_PREFIX = "session:user:";
    private static final String DEVICE_SESSION_PREFIX = "session:device:";
    private static final String DEVICE_TOKEN_PREFIX = "session:device-token:";
    private static final String TOKEN_DEVICE_PREFIX = "ai_gateway:token_device:";
    private static final String USER_TOKENS_PREFIX = "ai_gateway:user_tokens:";
    private static final long SESSION_TIMEOUT_HOURS = 24;
    private static final long SESSION_TIMEOUT_SECONDS = TimeUnit.HOURS.toSeconds(SESSION_TIMEOUT_HOURS);

    private static final DefaultRedisScript<Long> CREATE_SESSION = new DefaultRedisScript<>("""
            local oldDevice = redis.call('GET', KEYS[1])
            if oldDevice then
              local oldDeviceKey = ARGV[7] .. ARGV[1] .. ':' .. oldDevice
              local oldDeviceTokenKey = ARGV[8] .. ARGV[1] .. ':' .. oldDevice
              local oldToken = redis.call('GET', oldDeviceTokenKey)
              if oldToken then
                redis.call('DEL', ARGV[5] .. oldToken)
                redis.call('DEL', ARGV[6] .. oldToken)
                redis.call('SREM', KEYS[4], oldToken)
              end
              redis.call('DEL', oldDeviceKey, oldDeviceTokenKey)
              redis.call('DEL', ARGV[7] .. oldDevice)
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[4])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[4])
            redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[4])
            redis.call('SET', ARGV[6] .. ARGV[3], ARGV[2], 'EX', ARGV[4])
            redis.call('SADD', KEYS[4], ARGV[3])
            redis.call('EXPIRE', KEYS[4], ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> DESTROY_SESSION = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[2])
            local activeToken = redis.call('GET', KEYS[3])
            if owner ~= ARGV[1] then return 0 end
            if ARGV[3] ~= '' and activeToken ~= ARGV[3] then return 0 end
            if redis.call('GET', KEYS[1]) == ARGV[2] then redis.call('DEL', KEYS[1]) end
            redis.call('DEL', KEYS[2], KEYS[3])
            if activeToken then
              redis.call('DEL', ARGV[4] .. activeToken)
              redis.call('DEL', ARGV[5] .. activeToken)
              redis.call('SREM', KEYS[4], activeToken)
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** Atomically replaces any prior device session and binds the new token to this device. */
    public boolean createSession(Long userId, String deviceId, String token) {
        if (userId == null || blank(deviceId) || blank(token)) return false;
        Long created = redisTemplate.execute(CREATE_SESSION,
                List.of(userSessionKey(userId), deviceSessionKey(userId, deviceId), deviceTokenKey(userId, deviceId), userTokensKey(userId)),
                userId.toString(), deviceId, token, Long.toString(SESSION_TIMEOUT_SECONDS),
                TokenService.TOKEN_PREFIX, TOKEN_DEVICE_PREFIX, DEVICE_SESSION_PREFIX, DEVICE_TOKEN_PREFIX);
        log.info("会话创建成功: userId={}, deviceId={}", userId, deviceId);
        return Long.valueOf(1L).equals(created);
    }

    public boolean validateSession(Long userId, String deviceId, String token) {
        if (userId == null || blank(deviceId) || blank(token)) return false;
        return deviceId.equals(redisTemplate.opsForValue().get(userSessionKey(userId)))
                && userId.toString().equals(redisTemplate.opsForValue().get(deviceSessionKey(userId, deviceId)))
                && token.equals(redisTemplate.opsForValue().get(deviceTokenKey(userId, deviceId)))
                && deviceId.equals(redisTemplate.opsForValue().get(TOKEN_DEVICE_PREFIX + token));
    }

    public boolean validateSession(Long userId, String deviceId) {
        return userId != null && !blank(deviceId)
                && deviceId.equals(redisTemplate.opsForValue().get(userSessionKey(userId)))
                && userId.toString().equals(redisTemplate.opsForValue().get(deviceSessionKey(userId, deviceId)));
    }

    public void refreshSession(Long userId, String deviceId, String token) {
        if (!validateSession(userId, deviceId, token)) return;
        redisTemplate.expire(userSessionKey(userId), SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(deviceSessionKey(userId, deviceId), SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(deviceTokenKey(userId, deviceId), SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(TOKEN_DEVICE_PREFIX + token, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(userTokensKey(userId), SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
    }

    /** Revokes the active token for a device only if it is still the token bound to that session. */
    public boolean destroySession(Long userId, String deviceId, String expectedToken) {
        if (userId == null || blank(deviceId)) return false;
        Long destroyed = redisTemplate.execute(DESTROY_SESSION,
                List.of(userSessionKey(userId), deviceSessionKey(userId, deviceId), deviceTokenKey(userId, deviceId), userTokensKey(userId)),
                userId.toString(), deviceId, expectedToken == null ? "" : expectedToken,
                TokenService.TOKEN_PREFIX, TOKEN_DEVICE_PREFIX);
        if (Long.valueOf(1L).equals(destroyed)) {
            log.info("会话已撤销: userId={}, deviceId={}", userId, deviceId);
            return true;
        }
        return false;
    }

    public boolean revokeDevice(Long userId, String deviceId) {
        return destroySession(userId, deviceId, null);
    }

    public void revokeAllUserSessions(Long userId, boolean exceptCurrent) {
        if (userId == null) return;
        String currentDevice = getCurrentDeviceId(userId);
        String currentToken = currentDevice == null ? null : redisTemplate.opsForValue().get(deviceTokenKey(userId, currentDevice));
        boolean preserveCurrent = exceptCurrent && currentDevice != null && currentToken != null
                && userId.toString().equals(redisTemplate.opsForValue().get(deviceSessionKey(userId, currentDevice)))
                && currentDevice.equals(redisTemplate.opsForValue().get(TOKEN_DEVICE_PREFIX + currentToken))
                && userId.toString().equals(redisTemplate.opsForValue().get(TokenService.TOKEN_PREFIX + currentToken));
        Set<String> tokens = redisTemplate.opsForSet().members(userTokensKey(userId));
        if (tokens != null) {
            for (String token : tokens) {
                if (preserveCurrent && token.equals(currentToken)) continue;
                redisTemplate.delete(TokenService.TOKEN_PREFIX + token);
                redisTemplate.delete(TOKEN_DEVICE_PREFIX + token);
                redisTemplate.opsForSet().remove(userTokensKey(userId), token);
            }
        }
        if (!preserveCurrent && currentDevice != null) destroySession(userId, currentDevice, null);
        if (!preserveCurrent) redisTemplate.delete(Set.of(userSessionKey(userId), userTokensKey(userId)));
        log.info("用户会话已批量撤销: userId={}, exceptCurrent={}", userId, exceptCurrent);
    }

    public List<java.util.Map<String, Object>> listActiveDevices(Long userId) {
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        String deviceId = getCurrentDeviceId(userId);
        String token = deviceId == null ? null : redisTemplate.opsForValue().get(deviceTokenKey(userId, deviceId));
        if (deviceId != null && token != null && validateSession(userId, deviceId, token)) {
            java.util.Map<String, Object> device = new java.util.LinkedHashMap<>();
            device.put("deviceId", deviceId);
            device.put("info", "当前有效登录设备");
            device.put("lastActive", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            device.put("isCurrent", true);
            result.add(device);
        }
        return result;
    }

    public String getCurrentDeviceId(Long userId) {
        return userId == null ? null : redisTemplate.opsForValue().get(userSessionKey(userId));
    }

    public boolean hasActiveSession(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(userSessionKey(userId)));
    }

    private static String userSessionKey(Long userId) {
        return USER_SESSION_PREFIX + userId;
    }

    private static String deviceSessionKey(Long userId, String deviceId) {
        return DEVICE_SESSION_PREFIX + userId + ":" + deviceId;
    }

    private static String deviceTokenKey(Long userId, String deviceId) {
        return DEVICE_TOKEN_PREFIX + userId + ":" + deviceId;
    }

    private static String userTokensKey(Long userId) {
        return USER_TOKENS_PREFIX + userId;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
