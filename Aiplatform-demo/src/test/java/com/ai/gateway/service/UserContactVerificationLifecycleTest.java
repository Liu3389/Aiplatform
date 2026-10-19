package com.ai.gateway.service;

import com.ai.gateway.common.Result;
import com.ai.gateway.controller.UserController;
import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.entity.User;
import com.ai.gateway.entity.UserContactVerification;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.mapper.UserContactVerificationMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.verification.delivery-mode=local",
        "app.verification.allow-local-capture=true"
})
class UserContactVerificationLifecycleTest {

    @Autowired private UserService userService;
    @Autowired private UserMapper userMapper;
    @Autowired private UserContactVerificationMapper contactMapper;
    @Autowired private UserController userController;
    @Autowired private TokenService tokenService;
    @Autowired private SessionService sessionService;
    @Autowired private VerificationCodeDeliveryService deliveryService;
    @Autowired private StringRedisTemplate redisTemplate;

    @Test
    void verifiedEmailAndPhoneArePersistedAndCodesAreSingleUse() {
        UserInfoVO created = registerUser();
        Long userId = created.getId();
        assertEquals(false, created.isEmailVerified());
        assertEquals(false, created.isPhoneVerified());
        assertNull(userMapper.selectById(userId).getEmail(), "unverified signup email must not be persisted");
        String token = issueToken(userId, "contact-device-" + userId);
        String email = "contact_" + userId + "@example.test";
        String phone = phoneFor(userId);

        assertEquals(200, userController.sendBindCode(token, Map.of("type", "email", "target", email)).getCode());
        String emailCode = deliveryService.localCodeForTesting(userId, "email", email);
        assertTrue(emailCode.matches("\\d{6}"));
        Result<Void> emailBound = userController.bindEmail(token, Map.of("email", email, "code", emailCode));
        assertEquals(200, emailBound.getCode());
        assertEquals(email, userMapper.selectById(userId).getEmail());
        assertEquals(email, contactMapper.selectById(userId).getEmail());
        assertEquals(true, userService.getUserInfo(userId).isEmailVerified());
        assertEquals(false, userService.getUserInfo(userId).isPhoneVerified());
        assertThrows(BusinessException.class, () -> userController.bindEmail(token, Map.of("email", email, "code", emailCode)));

        assertEquals(200, userController.sendBindCode(token, Map.of("type", "phone", "target", phone)).getCode());
        String phoneCode = deliveryService.localCodeForTesting(userId, "phone", phone);
        assertEquals(200, userController.bindPhone(token, Map.of("phone", phone, "code", phoneCode)).getCode());
        User updated = userMapper.selectById(userId);
        assertEquals(phone, updated.getPhone());
        assertEquals(phone, contactMapper.selectById(userId).getPhone());
        UserInfoVO verifiedInfo = userService.getUserInfo(userId);
        assertEquals(phone, verifiedInfo.getPhone());
        assertEquals(true, verifiedInfo.isPhoneVerified());
        tokenService.revokeToken(token);
    }

    @Test
    void expiredWrongOwnerAndReplayedCodesCannotBindContacts() {
        Long ownerId = registerUser().getId();
        Long otherUserId = registerUser().getId();
        String ownerToken = issueToken(ownerId, "contact-owner-" + ownerId);
        String otherToken = issueToken(otherUserId, "contact-other-" + otherUserId);
        String email = "owned_" + ownerId + "@example.test";

        userController.sendBindCode(ownerToken, Map.of("type", "email", "target", email));
        String code = deliveryService.localCodeForTesting(ownerId, "email", email);
        String wrongCode = "000000".equals(code) ? "111111" : "000000";
        assertThrows(BusinessException.class,
                () -> userController.bindEmail(ownerToken, Map.of("email", email, "code", wrongCode)));
        BusinessException wrongOwner = assertThrows(BusinessException.class,
                () -> userController.bindEmail(otherToken, Map.of("email", email, "code", code)));
        assertEquals(400, wrongOwner.getCode());
        assertNull(userMapper.selectById(otherUserId).getEmail());
        assertEquals(200, userController.bindEmail(ownerToken, Map.of("email", email, "code", code)).getCode());
        assertThrows(BusinessException.class,
                () -> userController.bindEmail(ownerToken, Map.of("email", email, "code", code)));

        String expiredEmail = "expired_" + ownerId + "@example.test";
        redisTemplate.delete(UserService.bindUserCooldownKey(ownerId, "email"));
        userController.sendBindCode(ownerToken, Map.of("type", "email", "target", expiredEmail));
        redisTemplate.expire(UserService.bindCodeKey(ownerId, "email", expiredEmail), 0, java.util.concurrent.TimeUnit.SECONDS);
        assertThrows(BusinessException.class, () -> userController.bindEmail(ownerToken,
                Map.of("email", expiredEmail, "code", deliveryService.localCodeForTesting(ownerId, "email", expiredEmail))));
        assertNotEquals(expiredEmail, userMapper.selectById(ownerId).getEmail());
        tokenService.revokeToken(ownerToken);
        tokenService.revokeToken(otherToken);
    }

    @Test
    void contactsCannotBeReusedAndConcurrentClaimsHaveOneWinner() throws Exception {
        Long firstId = registerUser().getId();
        Long secondId = registerUser().getId();
        String firstToken = issueToken(firstId, "contact-first-" + firstId);
        String secondToken = issueToken(secondId, "contact-second-" + secondId);

        String alreadyUsed = "unique_" + firstId + "@example.test";
        userController.sendBindCode(firstToken, Map.of("type", "email", "target", alreadyUsed));
        String firstCode = deliveryService.localCodeForTesting(firstId, "email", alreadyUsed);
        userController.bindEmail(firstToken, Map.of("email", alreadyUsed, "code", firstCode));
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> userController.sendBindCode(secondToken, Map.of("type", "email", "target", alreadyUsed)));
        assertEquals(409, duplicate.getCode());

        String contestedEmail = "race_" + firstId + "_" + secondId + "@example.test";
        redisTemplate.delete(UserService.bindUserCooldownKey(firstId, "email"));
        userController.sendBindCode(firstToken, Map.of("type", "email", "target", contestedEmail));
        String firstRaceCode = deliveryService.localCodeForTesting(firstId, "email", contestedEmail);
        redisTemplate.delete(UserService.bindTargetCooldownKey("email", contestedEmail));
        userController.sendBindCode(secondToken, Map.of("type", "email", "target", contestedEmail));
        String secondRaceCode = deliveryService.localCodeForTesting(secondId, "email", contestedEmail);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> bindAfter(start, firstId, contestedEmail, firstRaceCode));
            Future<Boolean> second = executor.submit(() -> bindAfter(start, secondId, contestedEmail, secondRaceCode));
            start.countDown();
            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successes);
        }
        assertEquals(1L, contactMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserContactVerification>()
                        .eq(UserContactVerification::getEmail, contestedEmail)));
        assertEquals(1L, userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getEmail, contestedEmail)));
        tokenService.revokeToken(firstToken);
        tokenService.revokeToken(secondToken);
    }

    private boolean bindAfter(CountDownLatch start, Long userId, String email, String code) throws Exception {
        start.await();
        try {
            userService.bindEmail(userId, email, code);
            return true;
        } catch (BusinessException expectedConflict) {
            return false;
        }
    }

    private String issueToken(Long userId, String deviceId) {
        String token = tokenService.generateToken(userId);
        assertTrue(sessionService.createSession(userId, deviceId, token));
        return token;
    }

    private UserInfoVO registerUser() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("contact_verify_" + System.nanoTime());
        request.setPassword("ContactVerify123!");
        return userService.register(request);
    }

    private String phoneFor(Long userId) {
        return "139" + String.format("%08d", userId % 100_000_000L);
    }
}
