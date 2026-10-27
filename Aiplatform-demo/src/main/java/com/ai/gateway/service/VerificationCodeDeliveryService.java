package com.ai.gateway.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeDeliveryService {
    private static final long CODE_TTL_MINUTES = 10;
    private static final String LOCAL_INBOX_PREFIX = "verification:local:contact:";
    private static final String LOCAL_REGISTRATION_INBOX_PREFIX = "verification:local:registration:email:";
    private static final String LOCAL_LOGIN_INBOX_PREFIX = "verification:local:login:email:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.verification.delivery-mode:disabled}")
    private String deliveryMode;

    @Value("${app.verification.allow-local-capture:false}")
    private boolean allowLocalCapture;

    @Value("${app.verification.email.from:}")
    private String emailFrom;

    @Value("${app.verification.sms.url:}")
    private String smsUrl;

    @Value("${app.verification.sms.token:}")
    private String smsToken;

    @Value("${app.verification.sms.template-id:}")
    private String smsTemplateId;

    public void deliver(Long userId, String type, String target, String code) {
        if ("local".equalsIgnoreCase(deliveryMode) && allowLocalCapture) {
            redisTemplate.opsForValue().set(localInboxKey(userId, type, target), code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("本地验证通道已接收验证码: userId={}, type={}", userId, type);
            return;
        }
        if (!"external".equalsIgnoreCase(deliveryMode)) {
            throw unavailable();
        }
        try {
            if ("email".equals(type)) sendEmail(target, code, "绑定");
            else if ("phone".equals(type)) sendSms(target, code);
            else throw new IllegalArgumentException("unsupported contact type");
        } catch (Exception e) {
            log.warn("绑定验证码投递失败: type={}, errorType={}", type, e.getClass().getSimpleName());
            throw new com.ai.gateway.exception.BusinessException(503, "验证码发送失败，请稍后重试");
        }
    }

    public void deliverRegistrationEmail(String target, String code) {
        if ("local".equalsIgnoreCase(deliveryMode) && allowLocalCapture) {
            redisTemplate.opsForValue().set(registrationLocalInboxKey(target), code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("本地验证通道已接收注册邮箱验证码");
            return;
        }
        if (!"external".equalsIgnoreCase(deliveryMode)) throw unavailable();
        try {
            sendEmail(target, code, "注册");
        } catch (Exception e) {
            log.warn("注册验证码投递失败: errorType={}", e.getClass().getSimpleName());
            throw new com.ai.gateway.exception.BusinessException(503, "验证码发送失败，请稍后重试");
        }
    }

    public void deliverLoginEmail(String target, String code) {
        if ("local".equalsIgnoreCase(deliveryMode) && allowLocalCapture) {
            redisTemplate.opsForValue().set(loginLocalInboxKey(target), code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("本地验证通道已接收登录邮箱验证码");
            return;
        }
        if (!"external".equalsIgnoreCase(deliveryMode)) throw unavailable();
        try {
            sendEmail(target, code, "登录");
        } catch (Exception e) {
            log.warn("登录验证码投递失败: errorType={}", e.getClass().getSimpleName());
            throw new com.ai.gateway.exception.BusinessException(503, "验证码发送失败，请稍后重试");
        }
    }

    String localCodeForTesting(Long userId, String type, String target) {
        if (!"local".equalsIgnoreCase(deliveryMode) || !allowLocalCapture) {
            throw new IllegalStateException("local verification inbox is disabled");
        }
        return redisTemplate.opsForValue().get(localInboxKey(userId, type, target));
    }

    void clearLocalCode(Long userId, String type, String target) {
        if ("local".equalsIgnoreCase(deliveryMode) && allowLocalCapture) {
            redisTemplate.delete(localInboxKey(userId, type, target));
        }
    }

    String registrationCodeForTesting(String target) {
        if (!"local".equalsIgnoreCase(deliveryMode) || !allowLocalCapture) {
            throw new IllegalStateException("local verification inbox is disabled");
        }
        return redisTemplate.opsForValue().get(registrationLocalInboxKey(target));
    }

    void clearRegistrationLocalCode(String target) {
        if ("local".equalsIgnoreCase(deliveryMode) && allowLocalCapture) {
            redisTemplate.delete(registrationLocalInboxKey(target));
        }
    }

    String loginCodeForTesting(String target) {
        if (!"local".equalsIgnoreCase(deliveryMode) || !allowLocalCapture) {
            throw new IllegalStateException("local verification inbox is disabled");
        }
        return redisTemplate.opsForValue().get(loginLocalInboxKey(target));
    }

    void clearLoginLocalCode(String target) {
        if ("local".equalsIgnoreCase(deliveryMode) && allowLocalCapture) {
            redisTemplate.delete(loginLocalInboxKey(target));
        }
    }

    private void sendEmail(String target, String code, String purpose) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String from = emailFrom == null || emailFrom.isBlank() ? null : emailFrom.trim();
        if (sender == null || from == null) throw unavailable();

        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(target);
            helper.setSubject("凌点 AI 邮箱" + purpose + "验证码");
            helper.setText("你的凌点 AI " + purpose + "验证码是 " + code + "，10 分钟内有效。若非本人操作，请忽略此邮件。");
            sender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("email delivery failed", e);
        }
    }

    private void sendSms(String target, String code) {
        if (smsUrl == null || smsUrl.isBlank() || smsToken == null || smsToken.isBlank()
                || smsTemplateId == null || smsTemplateId.isBlank()) throw unavailable();
        URI uri = URI.create(smsUrl.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw unavailable();

        WebClient.builder().build().post().uri(uri)
                .headers(headers -> headers.setBearerAuth(smsToken))
                .bodyValue(Map.of("phone", target, "templateId", smsTemplateId,
                        "variables", Map.of("code", code, "expiresInMinutes", "10")))
                .retrieve().toBodilessEntity().block(Duration.ofSeconds(10));
    }

    static String localInboxKey(Long userId, String type, String target) {
        return LOCAL_INBOX_PREFIX + userId + ":" + type + ":" + sha256(target);
    }

    static String registrationLocalInboxKey(String target) {
        return LOCAL_REGISTRATION_INBOX_PREFIX + sha256(target);
    }

    static String loginLocalInboxKey(String target) {
        return LOCAL_LOGIN_INBOX_PREFIX + sha256(target);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private com.ai.gateway.exception.BusinessException unavailable() {
        return new com.ai.gateway.exception.BusinessException(503, "验证码发送通道未配置或暂不可用");
    }
}
