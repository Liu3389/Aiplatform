package com.ai.gateway.service;

import com.ai.gateway.common.ResultCode;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserTwoFactor;
import com.ai.gateway.entity.UserTwoFactorBackupCode;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserTwoFactorBackupCodeMapper;
import com.ai.gateway.mapper.UserTwoFactorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class UserTwoFactorService {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final String BACKUP_CODE_HASH_CONTEXT = "user-two-factor-backup/v1:";

    private final UserMapper userMapper;
    private final UserTwoFactorMapper twoFactorMapper;
    private final UserTwoFactorBackupCodeMapper backupCodeMapper;
    private final TotpService totpService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> beginEnrollment(Long userId) {
        lockUser(userId);
        UserTwoFactor settings = twoFactorMapper.selectByUserForUpdate(userId);
        if (settings != null && Boolean.TRUE.equals(settings.getEnabled())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "两步验证已启用");
        }

        String secret;
        if (settings == null) {
            secret = totpService.newSecret();
            settings = new UserTwoFactor();
            settings.setUserId(userId);
            settings.setPendingSecretCiphertext(totpService.encryptSecret(userId, secret));
            settings.setEnabled(false);
            settings.setLastTotpStep(-1L);
            settings.setCreateTime(LocalDateTime.now());
            settings.setUpdateTime(settings.getCreateTime());
            twoFactorMapper.insert(settings);
        } else if (settings.getPendingSecretCiphertext() == null) {
            secret = totpService.newSecret();
            settings.setPendingSecretCiphertext(totpService.encryptSecret(userId, secret));
            settings.setUpdateTime(LocalDateTime.now());
            twoFactorMapper.updateById(settings);
        } else {
            secret = totpService.decryptSecret(userId, settings.getPendingSecretCiphertext());
        }

        String issuer = "LingdianAI";
        String label = URLEncoder.encode(issuer + ":" + userId, StandardCharsets.UTF_8);
        String qrUrl = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=6&period=30";
        Map<String, String> response = new HashMap<>();
        response.put("secret", secret);
        response.put("qrUrl", qrUrl);
        return response;
    }

    /** Activates 2FA only after a valid TOTP and returns the newly generated backup codes once. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmEnrollment(Long userId, String code) {
        UserTwoFactor settings = twoFactorMapper.selectByUserForUpdate(userId);
        if (settings == null || Boolean.TRUE.equals(settings.getEnabled())
                || settings.getPendingSecretCiphertext() == null) {
            throw invalidCode();
        }
        String pendingSecret = totpService.decryptSecret(userId, settings.getPendingSecretCiphertext());
        OptionalLong matchedStep = totpService.matchedStep(pendingSecret, code, System.currentTimeMillis());
        if (matchedStep.isEmpty()) throw invalidCode();

        LocalDateTime now = LocalDateTime.now();
        if (twoFactorMapper.activateEnrollment(userId, matchedStep.getAsLong(), now) != 1) {
            throw invalidCode();
        }

        backupCodeMapper.deleteByUserId(userId);
        List<String> backupCodes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String backupCode = newBackupCode();
            UserTwoFactorBackupCode saved = new UserTwoFactorBackupCode();
            saved.setUserId(userId);
            saved.setCodeHash(hashBackupCode(normalizeBackupCode(backupCode)));
            saved.setCreateTime(now);
            backupCodeMapper.insert(saved);
            backupCodes.add(backupCode);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("enabled", true);
        response.put("backupCodes", backupCodes);
        return response;
    }

    public boolean isEnabled(Long userId) {
        UserTwoFactor settings = twoFactorMapper.selectById(userId);
        return settings != null && Boolean.TRUE.equals(settings.getEnabled())
                && settings.getSecretCiphertext() != null;
    }

    /** Validates and atomically consumes either a new TOTP step or one backup code. */
    @Transactional(rollbackFor = Exception.class)
    public boolean verifyAndConsumeLoginCode(Long userId, String code) {
        UserTwoFactor settings = twoFactorMapper.selectByUserForUpdate(userId);
        return verifyAndConsume(settings, userId, code);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long userId, String code) {
        UserTwoFactor settings = twoFactorMapper.selectByUserForUpdate(userId);
        if (!verifyAndConsume(settings, userId, code)) throw invalidCode();
        backupCodeMapper.deleteByUserId(userId);
        twoFactorMapper.deleteById(userId);
    }

    public Map<String, Object> status(Long userId) {
        UserTwoFactor settings = twoFactorMapper.selectById(userId);
        boolean enabled = settings != null && Boolean.TRUE.equals(settings.getEnabled())
                && settings.getSecretCiphertext() != null;
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", enabled);
        response.put("backupCodesRemaining", enabled ? backupCodeMapper.countUnused(userId) : 0);
        return response;
    }

    private boolean verifyAndConsume(UserTwoFactor settings, Long userId, String code) {
        if (settings == null || !Boolean.TRUE.equals(settings.getEnabled())
                || settings.getSecretCiphertext() == null || code == null || code.isBlank()) {
            return false;
        }
        String secret = totpService.decryptSecret(userId, settings.getSecretCiphertext());
        OptionalLong matchedStep = totpService.matchedStep(secret, code.trim(), System.currentTimeMillis());
        if (matchedStep.isPresent()) {
            return twoFactorMapper.consumeTotpStep(userId, matchedStep.getAsLong(), LocalDateTime.now()) == 1;
        }

        String normalizedBackupCode = normalizeBackupCode(code);
        if (normalizedBackupCode == null) return false;
        return backupCodeMapper.consume(userId, hashBackupCode(normalizedBackupCode)) == 1;
    }

    private void lockUser(Long userId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
    }

    private String newBackupCode() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        String raw = HexFormat.of().withUpperCase().formatHex(bytes);
        return raw.replaceAll("(.{8})", "$1-").replaceFirst("-$", "");
    }

    private String normalizeBackupCode(String code) {
        String normalized = code.replaceAll("[-\\s]", "").toUpperCase(java.util.Locale.ROOT);
        return normalized.matches("[0-9A-F]{32}") ? normalized : null;
    }

    private String hashBackupCode(String code) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((BACKUP_CODE_HASH_CONTEXT + code).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("无法安全处理两步验证备用码", e);
        }
    }

    private BusinessException invalidCode() {
        return new BusinessException(ResultCode.PARAM_ERROR.getCode(), "两步验证码错误或已过期");
    }
}
