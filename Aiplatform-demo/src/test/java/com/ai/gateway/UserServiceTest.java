package com.ai.gateway;

import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.service.SessionService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务测试类
 * 
 * @author AI Gateway Platform
 */
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SessionService sessionService;

    @Test
    void testRegister() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("testuser_" + System.currentTimeMillis());
        request.setPassword("123456");
        request.setEmail("test@example.com");

        UserInfoVO userInfo = userService.register(request);
        
        assertNotNull(userInfo);
        assertEquals(request.getUsername(), userInfo.getUsername());
        assertNull(userInfo.getEmail(), "signup must not persist an unverified email");
        assertNotNull(userInfo.getId());
    }

    @Test
    void testGetBalance() {
        // 先创建一个测试用户
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("balance_test_user_" + System.currentTimeMillis());
        request.setPassword("123456");
        
        UserInfoVO userInfo = userService.register(request);
        
        // 查询余额
        var balance = userService.getBalance(userInfo.getId());
        
        assertNotNull(balance);
        assertEquals(0, balance.compareTo(java.math.BigDecimal.ZERO));
    }

    @Test
    void testDeactivateAccountPreservesIdentityAndHistoryWhileRevokingSessions() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "deactivate_" + suffix;
        String password = "Passw0rd!";
        Long userId = null;
        try {
            UserRegisterRequest request = new UserRegisterRequest();
            request.setUsername(username);
            request.setPassword(password);
            UserInfoVO created = userService.register(request);
            Long createdUserId = created.getId();
            userId = createdUserId;

            User before = userMapper.selectById(createdUserId);
            String email = "deactivate_" + suffix + "@example.test";
            String phone = "test-" + suffix.substring(0, 12);
            before.setEmail(email);
            before.setPhone(phone);
            userMapper.updateById(before);

            String deviceId = "deactivation-test-device";
            String token = "deactivation-test-token-" + suffix;
            assertTrue(sessionService.createSession(createdUserId, deviceId, token));
            assertTrue(sessionService.hasActiveSession(createdUserId));

            assertThrows(BusinessException.class, () -> userService.deactivateAccount(createdUserId, "wrong-password"));
            assertEquals(1, userMapper.selectById(createdUserId).getStatus(), "wrong password must not deactivate the account");

            userService.deactivateAccount(createdUserId, password);

            User after = userMapper.selectById(createdUserId);
            assertEquals(0, after.getStatus());
            assertEquals(username, after.getUsername());
            assertEquals(email, after.getEmail());
            assertEquals(phone, after.getPhone());
            assertFalse(sessionService.hasActiveSession(createdUserId));
            assertFalse(sessionService.validateSession(createdUserId, deviceId, token));
        } finally {
            if (userId != null) {
                sessionService.revokeAllUserSessions(userId, false);
                userMapper.deleteById(userId);
            }
        }
    }
}
