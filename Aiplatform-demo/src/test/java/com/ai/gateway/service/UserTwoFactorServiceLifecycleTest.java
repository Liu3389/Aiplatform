package com.ai.gateway.service;

import com.ai.gateway.dto.UserLoginRequest;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.controller.AuthController;
import com.ai.gateway.entity.UserTwoFactor;
import com.ai.gateway.entity.UserTwoFactorBackupCode;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.exception.TwoFactorRequiredException;
import com.ai.gateway.mapper.UserTwoFactorBackupCodeMapper;
import com.ai.gateway.mapper.UserTwoFactorMapper;
import com.ai.gateway.service.SessionService;
import com.ai.gateway.service.CaptchaService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.common.Result;
import com.ai.gateway.vo.LoginResponseVO;
import com.ai.gateway.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class UserTwoFactorServiceLifecycleTest {

    @Autowired private UserService userService;
    @Autowired private UserTwoFactorService twoFactorService;
    @Autowired private UserTwoFactorMapper twoFactorMapper;
    @Autowired private UserTwoFactorBackupCodeMapper backupCodeMapper;
    @Autowired private TotpService totpService;
    @Autowired private AuthController authController;
    @Autowired private CaptchaService captchaService;
    @Autowired private TokenService tokenService;
    @Autowired private SessionService sessionService;
    @Autowired private StringRedisTemplate redisTemplate;

    @Test
    void loginRequiresTotpAndBackupCodesAreHashedOneTimeValues() {
        UserRegisterRequest register = new UserRegisterRequest();
        register.setUsername("two_factor_" + System.currentTimeMillis());
        register.setPassword("TwoFactorLocal123!");
        UserInfoVO created = userService.register(register);
        Long userId = created.getId();

        Map<String, String> enrollment = twoFactorService.beginEnrollment(userId);
        String secret = enrollment.get("secret");
        assertNotNull(secret);
        assertTrue(enrollment.get("qrUrl").contains("algorithm=SHA1&digits=6&period=30"));
        UserTwoFactor pending = twoFactorMapper.selectById(userId);
        assertFalse(Boolean.TRUE.equals(pending.getEnabled()));
        assertNull(pending.getSecretCiphertext());
        assertNotEquals(secret, pending.getPendingSecretCiphertext());

        long currentStep = System.currentTimeMillis() / 30_000L;
        Map<String, Object> confirmation = twoFactorService.confirmEnrollment(
                userId, totpService.codeForStep(secret, currentStep));
        @SuppressWarnings("unchecked")
        List<String> backupCodes = (List<String>) confirmation.get("backupCodes");
        assertEquals(10, backupCodes.size());

        UserTwoFactor active = twoFactorMapper.selectById(userId);
        assertTrue(Boolean.TRUE.equals(active.getEnabled()));
        assertNull(active.getPendingSecretCiphertext());
        assertNotEquals(secret, active.getSecretCiphertext());
        assertThrows(BusinessException.class, () -> twoFactorService.confirmEnrollment(
                userId, totpService.codeForStep(secret, System.currentTimeMillis() / 30_000L + 1)));

        Result<LoginResponseVO> missingFactor = authController.login(
                loginRequest(register.getUsername(), register.getPassword(), null), "two-factor-test-device");
        assertEquals(428, missingFactor.getCode());
        assertNull(missingFactor.getData());
        assertNull(sessionService.getCurrentDeviceId(userId));

        String loginTotp = totpService.codeForStep(secret, currentStep + 1);
        Result<LoginResponseVO> validTotp = authController.login(
                loginRequest(register.getUsername(), register.getPassword(), loginTotp), "two-factor-test-device");
        assertEquals(200, validTotp.getCode());
        assertNotNull(validTotp.getData().getToken());
        assertEquals(userId, tokenService.getUserIdFromToken(validTotp.getData().getToken()));
        assertThrows(BusinessException.class, () -> authController.login(
                loginRequest(register.getUsername(), register.getPassword(), loginTotp), "replay-device"));

        Result<LoginResponseVO> validBackup = authController.login(
                loginRequest(register.getUsername(), register.getPassword(), backupCodes.get(0)), "backup-device");
        assertEquals(200, validBackup.getCode());
        assertNotNull(validBackup.getData().getToken());
        assertThrows(BusinessException.class, () -> authController.login(
                loginRequest(register.getUsername(), register.getPassword(), backupCodes.get(0)), "backup-replay-device"));

        Map<String, Object> status = twoFactorService.status(userId);
        assertTrue(Boolean.TRUE.equals(status.get("enabled")));
        assertEquals(9, status.get("backupCodesRemaining"));
        assertFalse(status.containsKey("backupCodes"));
        List<UserTwoFactorBackupCode> storedCodes = backupCodeMapper.selectList(
                new LambdaQueryWrapper<UserTwoFactorBackupCode>().eq(UserTwoFactorBackupCode::getUserId, userId));
        assertEquals(10, storedCodes.size());
        assertTrue(storedCodes.stream().noneMatch(row -> backupCodes.contains(row.getCodeHash())));

        twoFactorService.disable(userId, backupCodes.get(1));
        assertFalse(Boolean.TRUE.equals(twoFactorService.status(userId).get("enabled")));
        assertEquals(0, backupCodeMapper.countUnused(userId));
    }

    private UserLoginRequest loginRequest(String username, String password, String twoFactorCode) {
        Map<String, Object> captcha = captchaService.generateLoginCaptcha();
        String captchaId = captcha.get("captchaId").toString();
        String captchaCode = redisTemplate.opsForValue().get("ai_gateway:captcha:login:" + captchaId);
        UserLoginRequest request = new UserLoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setCaptchaId(captchaId);
        request.setCaptchaCode(captchaCode);
        request.setTwoFactorCode(twoFactorCode);
        return request;
    }
}
