package com.ai.gateway.controller;

import com.ai.gateway.entity.User;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.AiChargeReconciliationService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.vo.AdminPageVO;
import com.ai.gateway.vo.AiChargeReviewQueueItemVO;
import com.ai.gateway.vo.AiChargeReviewResolutionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.messaging.rabbit-enabled=false",
        "app.messaging.intent-recovery-ms=3600000"
})
@AutoConfigureMockMvc(addFilters = false)
class AdminAiChargeReconciliationHttpTest {

    private static final long LOCAL_USER_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private AiChargeReconciliationService reconciliationService;
    @MockBean private TokenService tokenService;
    @MockBean private AdminOperationLogService operationLogService;

    private String originalRole;

    @BeforeEach
    void setupSuperAdmin() {
        originalRole = jdbc.queryForObject("SELECT role FROM user WHERE id = ?", String.class, LOCAL_USER_ID);
        jdbc.update("UPDATE user SET role = 'SUPER_ADMIN' WHERE id = ?", LOCAL_USER_ID);
        when(tokenService.getUserIdFromToken("root-token")).thenReturn(LOCAL_USER_ID);
    }

    @AfterEach
    void restoreUserRole() {
        jdbc.update("UPDATE user SET role = ? WHERE id = ?", originalRole, LOCAL_USER_ID);
    }

    @Test
    void queueHttpRouteReturnsTheAdminPage() throws Exception {
        AdminPageVO<AiChargeReviewQueueItemVO> page = new AdminPageVO<>();
        page.setRecords(List.of());
        page.setTotal(0);
        page.setPage(1);
        page.setSize(20);
        when(reconciliationService.listManualReview(1, 20)).thenReturn(page);

        mockMvc.perform(get("/admin/ai-charge-intents/manual-review")
                        .header("X-User-Token", "root-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void resolveHttpRouteValidatesEvidenceAndReturnsTheSettledOutcome() throws Exception {
        AiChargeReviewResolutionVO result = new AiChargeReviewResolutionVO();
        result.setRequestId("manual-request-1");
        result.setUserId(18L);
        result.setAction("REFUND");
        result.setStatus("RESOLVED");
        result.setIntentStatus("REFUNDED");
        result.setMessage("对账处理已完成");
        when(reconciliationService.processResolution("manual-request-1")).thenReturn(result);

        mockMvc.perform(post("/admin/ai-charge-intents/manual-request-1/resolve")
                        .header("X-User-Token", "root-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"REFUND","evidence":"provider log 123 confirms no charge"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.intentStatus").value("REFUNDED"));

        verify(reconciliationService).beginResolution(eq("manual-request-1"), eq(LOCAL_USER_ID),
                eq("REFUND"), isNull(), eq("provider log 123 confirms no charge"));
    }

    @Test
    void invalidEvidenceIsRejectedBeforeTheServiceCanWriteAnAudit() throws Exception {
        mockMvc.perform(post("/admin/ai-charge-intents/manual-request-2/resolve")
                        .header("X-User-Token", "root-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"REFUND","evidence":"short"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(reconciliationService, never()).beginResolution(any(), any(), any(), any(), any());
    }
}
