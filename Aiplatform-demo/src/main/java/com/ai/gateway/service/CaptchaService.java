package com.ai.gateway.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 注册和登录图形验证码服务。
 * 验证码图片只返回给当前请求，答案放入 Redis 并在校验时一次性消费。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String REGISTER_PREFIX = "ai_gateway:captcha:register:";
    private static final String LOGIN_PREFIX = "ai_gateway:captcha:login:";
    private static final long TTL_SECONDS = 5 * 60;
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); " +
                    "if value then redis.call('DEL', KEYS[1]); end; " +
                    "return value",
            String.class
    );

    private final StringRedisTemplate redisTemplate;

    public Map<String, Object> generateRegisterCaptcha() {
        return generateCaptcha(REGISTER_PREFIX);
    }

    public Map<String, Object> generateLoginCaptcha() {
        return generateCaptcha(LOGIN_PREFIX);
    }

    private Map<String, Object> generateCaptcha(String prefix) {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(144, 44, 5, 20);
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String answer = normalize(captcha.getCode());
        redisTemplate.opsForValue().set(key(prefix, captchaId), answer, TTL_SECONDS, TimeUnit.SECONDS);
        return Map.of(
                "captchaId", captchaId,
                "image", captcha.getImageBase64Data(),
                "expiresIn", TTL_SECONDS
        );
    }

    /**
     * 无论校验成功还是失败都删除答案，避免同一张验证码被重复尝试。
     */
    public boolean validateAndConsume(String captchaId, String captchaCode) {
        return validateAndConsume(REGISTER_PREFIX, captchaId, captchaCode);
    }

    public boolean validateLoginAndConsume(String captchaId, String captchaCode) {
        return validateAndConsume(LOGIN_PREFIX, captchaId, captchaCode);
    }

    private boolean validateAndConsume(String prefix, String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            return false;
        }
        String redisKey = key(prefix, captchaId.trim());
        String expected = redisTemplate.execute(CONSUME_SCRIPT, List.of(redisKey));
        return expected != null && expected.equals(normalize(captchaCode));
    }

    private String key(String prefix, String captchaId) {
        return prefix + captchaId;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
