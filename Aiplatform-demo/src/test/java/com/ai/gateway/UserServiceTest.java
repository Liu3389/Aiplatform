package com.ai.gateway;

import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

    @Test
    void testRegister() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("testuser_" + System.currentTimeMillis());
        request.setPassword("123456");
        request.setEmail("test@example.com");

        UserInfoVO userInfo = userService.register(request);
        
        assertNotNull(userInfo);
        assertEquals(request.getUsername(), userInfo.getUsername());
        assertEquals(request.getEmail(), userInfo.getEmail());
        assertNotNull(userInfo.getId());
    }

    @Test
    void testGetBalance() {
        // 先创建一个测试用户
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("balance_test_user");
        request.setPassword("123456");
        
        UserInfoVO userInfo = userService.register(request);
        
        // 查询余额
        var balance = userService.getBalance(userInfo.getId());
        
        assertNotNull(balance);
        assertEquals(0, balance.compareTo(java.math.BigDecimal.ZERO));
    }
}
