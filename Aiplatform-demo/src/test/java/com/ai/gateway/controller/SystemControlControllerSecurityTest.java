package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.service.DockerControlService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SystemControlControllerSecurityTest {

    @Test
    void missingSecondaryPasswordFailsClosedInsteadOfUsingWeakDefault() {
        DockerControlService dockerControlService = mock(DockerControlService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SystemControlController controller = new SystemControlController(dockerControlService, redisTemplate);

        Result<Map<String, Object>> response = controller.verifySecondaryPassword(
                "admin-token", Map.of("password", "5215"));

        assertEquals(503, response.getCode());
        verifyNoInteractions(dockerControlService, redisTemplate);
    }
}
