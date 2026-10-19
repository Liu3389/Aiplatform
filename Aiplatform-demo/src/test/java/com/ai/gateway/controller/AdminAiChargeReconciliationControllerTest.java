package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.dto.AdminResolveAiChargeRequest;
import com.ai.gateway.entity.User;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.AiChargeReconciliationService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.AiChargeReviewResolutionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiChargeReconciliationControllerTest {

    private AiChargeReconciliationService reconciliationService;
    private TokenService tokenService;
    private UserService userService;
    private AdminOperationLogService operationLogService;
    private AdminAiChargeReconciliationController controller;

    @BeforeEach
    void setUp() {
        reconciliationService = mock(AiChargeReconciliationService.class);
        tokenService = mock(TokenService.class);
        userService = mock(UserService.class);
        operationLogService = mock(AdminOperationLogService.class);
        controller = new AdminAiChargeReconciliationController(reconciliationService, tokenService,
                userService, operationLogService);
    }

    @Test
    void everyEndpointRequiresAdminAndResolutionRequiresSuperAdmin() throws Exception {
        var methods = Arrays.stream(AdminAiChargeReconciliationController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).toList();
        assertEquals(3, methods.size());
        methods.forEach(method -> assertNotNull(method.getAnnotation(RequireAdmin.class), method.getName()));

        when(tokenService.getUserIdFromToken("admin-token")).thenReturn(42L);
        User admin = new User();
        admin.setId(42L);
        admin.setRole("ADMIN");
        admin.setStatus(1);
        when(userService.getUserEntityById(42L)).thenReturn(admin);

        var response = controller.resolve("admin-token", "ambiguous-request", validRefund());
        assertEquals(403, response.getCode());
        verify(reconciliationService, never()).beginResolution(
                "ambiguous-request", 42L, "REFUND", null, "provider log 123 confirms no charge");
    }

    @Test
    void superAdminCanSubmitEvidenceAndControllerReturnsPersistedOutcome() {
        when(tokenService.getUserIdFromToken("root-token")).thenReturn(7L);
        User superAdmin = new User();
        superAdmin.setId(7L);
        superAdmin.setUsername("root-local");
        superAdmin.setRole("SUPER_ADMIN");
        superAdmin.setStatus(1);
        when(userService.getUserEntityById(7L)).thenReturn(superAdmin);

        AiChargeReviewResolutionVO resolution = new AiChargeReviewResolutionVO();
        resolution.setRequestId("ambiguous-request");
        resolution.setUserId(18L);
        resolution.setAction("REFUND");
        resolution.setStatus("RESOLVED");
        resolution.setIntentStatus("REFUNDED");
        resolution.setMessage("对账处理已完成");
        when(reconciliationService.processResolution("ambiguous-request")).thenReturn(resolution);

        var response = controller.resolve("root-token", "ambiguous-request", validRefund());

        assertEquals(200, response.getCode());
        assertEquals("REFUNDED", response.getData().getIntentStatus());
        verify(reconciliationService).beginResolution("ambiguous-request", 7L,
                "REFUND", null, "provider log 123 confirms no charge");
        verify(operationLogService).logSuccess(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("root-local"),
                org.mockito.ArgumentMatchers.eq("AI_CHARGE_RECONCILIATION"),
                org.mockito.ArgumentMatchers.eq("RESOLVE_REFUND"),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(18L)),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private AdminResolveAiChargeRequest validRefund() {
        AdminResolveAiChargeRequest request = new AdminResolveAiChargeRequest();
        request.setAction("REFUND");
        request.setActualPoints(null);
        request.setEvidence("provider log 123 confirms no charge");
        return request;
    }
}
