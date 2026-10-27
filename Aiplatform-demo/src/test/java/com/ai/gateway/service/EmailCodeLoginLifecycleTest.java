package com.ai.gateway.service;

import com.ai.gateway.common.Result;
import com.ai.gateway.controller.AuthController;
import com.ai.gateway.dto.EmailCodeLoginRequest;
import com.ai.gateway.dto.LoginEmailCodeRequest;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserContactVerification;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.UserContactVerificationMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.vo.LoginResponseVO;
import com.ai.gateway.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.verification.delivery-mode=local",
        "app.verification.allow-local-capture=true"
})
class EmailCodeLoginLifecycleTest {

    @Autowired private UserService userService;
    @Autowired private AuthController authController;
    @Autowired private CaptchaService captchaService;
    @Autowired private VerificationCodeDeliveryService deliveryService;
    @Autowired private UserMapper userMapper;
    @Autowired private UserContactVerificationMapper contactMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private TokenService tokenService;
    @Autowired private UserTwoFactorService twoFactorService;
    @Autowired private TotpService totpService;

    @Test
    void emailCodeIsCaptchaGatedSingleUseAndMarksEmailVerified() {
        String email = "otp_login_" + System.nanoTime() + "@example.test";
        UserInfoVO created = registerWithProfileEmail(email);

        String otp = requestLoginCode(email);
        assertTrue(otp.matches("\\d{6}"));

        EmailCodeLoginRequest wrong = loginRequest(email, "000000".equals(otp) ? "111111" : "000000", null);
        assertThrows(BusinessException.class, () -> authController.loginWithEmailCode(wrong, "otp-wrong-device"));

        EmailCodeLoginRequest login = loginRequest(email, otp, null);
        Result<LoginResponseVO> result = authController.loginWithEmailCode(login, "otp-device");
        assertEquals(200, result.getCode());
        assertNotNull(result.getData().getToken());
        assertEquals("otp-device", result.getData().getDeviceId());
        assertEquals(created.getId(), result.getData().getUserInfo().getId());
        assertTrue(result.getData().getUserInfo().isEmailVerified(), "successful email-code login proves mailbox ownership");
        assertEquals(email, contactMapper.selectById(created.getId()).getEmail());
        assertEquals(email, userMapper.selectById(created.getId()).getEmail());

        assertThrows(BusinessException.class, () -> authController.loginWithEmailCode(login, "otp-replay-device"),
                "a login code is single use");
        tokenService.revokeToken(result.getData().getToken());
    }

    @Test
    void unknownEmailCannotBeUsedAsEmailCodeSender() {
        String email = "otp_unknown_" + System.nanoTime() + "@example.test";
        Map<String, Object> captcha = captchaService.generateLoginCaptcha();
        LoginEmailCodeRequest request = new LoginEmailCodeRequest();
        request.setEmail(email);
        request.setCaptchaId(captcha.get("captchaId").toString());
        request.setCaptchaCode(redisTemplate.opsForValue()
                .get("ai_gateway:captcha:login:" + captcha.get("captchaId")));
        BusinessException error = assertThrows(BusinessException.class,
                () -> authController.sendLoginEmailCode(request));
        assertEquals(404, error.getCode());
        assertNull(deliveryService.loginCodeForTesting(email));
    }

    @Test
    void twoFactorAccountCanFinishEmailCodeLoginWithoutRequestingAnotherCode() {
        String email = "otp_2fa_" + System.nanoTime() + "@example.test";
        UserInfoVO created = registerWithProfileEmail(email);
        Map<String, String> enrollment = twoFactorService.beginEnrollment(created.getId());
        long step = System.currentTimeMillis() / 30_000L;
        twoFactorService.confirmEnrollment(created.getId(), totpService.codeForStep(enrollment.get("secret"), step));

        String otp = requestLoginCode(email);
        Result<LoginResponseVO> challenge = authController.loginWithEmailCode(loginRequest(email, otp, null), "otp-2fa-device");
        assertEquals(428, challenge.getCode());

        String totp = totpService.codeForStep(enrollment.get("secret"), step + 1);
        Result<LoginResponseVO> result = authController.loginWithEmailCode(loginRequest(email, otp, totp), "otp-2fa-device");
        assertEquals(200, result.getCode());
        assertNotNull(result.getData().getToken());
        tokenService.revokeToken(result.getData().getToken());
    }

    private UserInfoVO registerWithProfileEmail(String email) {
        UserRegisterRequest register = new UserRegisterRequest();
        register.setUsername("otp_user_" + System.nanoTime());
        register.setPassword("OtpLogin123!");
        UserInfoVO created = userService.register(register);
        User user = userMapper.selectById(created.getId());
        user.setEmail(email);
        userMapper.updateById(user);
        return created;
    }

    private String requestLoginCode(String email) {
        Map<String, Object> captcha = captchaService.generateLoginCaptcha();
        String captchaId = captcha.get("captchaId").toString();
        LoginEmailCodeRequest request = new LoginEmailCodeRequest();
        request.setEmail(email);
        request.setCaptchaId(captchaId);
        request.setCaptchaCode(redisTemplate.opsForValue().get("ai_gateway:captcha:login:" + captchaId));
        assertEquals(200, authController.sendLoginEmailCode(request).getCode());
        return deliveryService.loginCodeForTesting(email);
    }

    private EmailCodeLoginRequest loginRequest(String email, String code, String twoFactorCode) {
        EmailCodeLoginRequest request = new EmailCodeLoginRequest();
        request.setEmail(email);
        request.setCode(code);
        request.setTwoFactorCode(twoFactorCode);
        return request;
    }
}
