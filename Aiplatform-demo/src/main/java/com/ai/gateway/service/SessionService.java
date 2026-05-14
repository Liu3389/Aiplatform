package com.ai.gateway.service;

import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 会话管理服务 - 实现单设备登录和异地登录踢下线
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final StringRedisTemplate redisTemplate;
    
    private static final String USER_SESSION_PREFIX = "session:user:";
    private static final String DEVICE_SESSION_PREFIX = "session:device:";
    private static final long SESSION_TIMEOUT_HOURS = 24;

    /**
     * 创建新会话（登录时调用）
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @param token 用户Token
     * @return 是否成功创建会话
     */
    public boolean createSession(Long userId, String deviceId, String token) {
        String userSessionKey = USER_SESSION_PREFIX + userId;
        String deviceSessionKey = DEVICE_SESSION_PREFIX + deviceId;
        
        // 检查该用户是否已有活跃会话
        String existingDeviceId = redisTemplate.opsForValue().get(userSessionKey);
        if (existingDeviceId != null && !existingDeviceId.equals(deviceId)) {
            // 异地登录，踢掉旧设备
            log.info("检测到异地登录: userId={}, oldDevice={}, newDevice={}", 
                    userId, existingDeviceId, deviceId);
            invalidateSession(userId, existingDeviceId);
        }
        
        // 创建新会话
        redisTemplate.opsForValue().set(userSessionKey, deviceId, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(deviceSessionKey, userId.toString(), SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        
        log.info("会话创建成功: userId={}, deviceId={}", userId, deviceId);
        return true;
    }

    /**
     * 验证会话有效性
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 会话是否有效
     */
    public boolean validateSession(Long userId, String deviceId) {
        String userSessionKey = USER_SESSION_PREFIX + userId;
        String currentDeviceId = redisTemplate.opsForValue().get(userSessionKey);
        
        if (currentDeviceId == null) {
            return false;
        }
        
        return currentDeviceId.equals(deviceId);
    }

    /**
     * 刷新会话超时时间
     * @param userId 用户ID
     * @param deviceId 设备ID
     */
    public void refreshSession(Long userId, String deviceId) {
        if (validateSession(userId, deviceId)) {
            String userSessionKey = USER_SESSION_PREFIX + userId;
            String deviceSessionKey = DEVICE_SESSION_PREFIX + deviceId;
            redisTemplate.expire(userSessionKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
            redisTemplate.expire(deviceSessionKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        }
    }

    /**
     * 销毁会话（退出登录时调用）
     * @param userId 用户ID
     * @param deviceId 设备ID
     */
    public void destroySession(Long userId, String deviceId) {
        String userSessionKey = USER_SESSION_PREFIX + userId;
        String deviceSessionKey = DEVICE_SESSION_PREFIX + deviceId;
        
        redisTemplate.delete(userSessionKey);
        redisTemplate.delete(deviceSessionKey);
        
        log.info("会话已销毁: userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * 强制踢下线指定设备
     * @param userId 用户ID
     * @param deviceId 设备ID
     */
    private void invalidateSession(Long userId, String deviceId) {
        String userSessionKey = USER_SESSION_PREFIX + userId;
        String deviceSessionKey = DEVICE_SESSION_PREFIX + deviceId;
        
        redisTemplate.delete(userSessionKey);
        redisTemplate.delete(deviceSessionKey);
        
        log.warn("设备已被踢下线: userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * 获取用户当前活跃设备ID
     * @param userId 用户ID
     * @return 设备ID，如果没有活跃会话则返回null
     */
    public String getCurrentDeviceId(Long userId) {
        String userSessionKey = USER_SESSION_PREFIX + userId;
        return redisTemplate.opsForValue().get(userSessionKey);
    }

    /**
     * 检查用户是否有活跃会话
     * @param userId 用户ID
     * @return 是否有活跃会话
     */
    public boolean hasActiveSession(Long userId) {
        String userSessionKey = USER_SESSION_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(userSessionKey));
    }
}
