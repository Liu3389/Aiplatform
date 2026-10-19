package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.dto.AdminResolveAiChargeRequest;
import com.ai.gateway.entity.User;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.AiChargeReconciliationService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.AdminPageVO;
import com.ai.gateway.vo.AiChargeReviewAuditVO;
import com.ai.gateway.vo.AiChargeReviewQueueItemVO;
import com.ai.gateway.vo.AiChargeReviewResolutionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "AI 点数人工对账", description = "核验上游计费记录后，由超级管理员幂等退款或结算歧义预扣")
@RestController
@RequestMapping("/admin/ai-charge-intents")
@RequiredArgsConstructor
public class AdminAiChargeReconciliationController {

    private final AiChargeReconciliationService reconciliationService;
    private final TokenService tokenService;
    private final UserService userService;
    private final AdminOperationLogService operationLogService;

    @RequireAdmin
    @Operation(summary = "查询待人工对账的 AI 点数预扣")
    @GetMapping("/manual-review")
    public Result<AdminPageVO<AiChargeReviewQueueItemVO>> listManualReview(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(reconciliationService.listManualReview(page, size));
    }

    @RequireAdmin
    @Operation(summary = "查询 AI 点数人工对账记录")
    @GetMapping("/reconciliations")
    public Result<AdminPageVO<AiChargeReviewAuditVO>> listHistory(
            @RequestHeader("X-User-Token") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(reconciliationService.listReviewHistory(page, size));
    }

    @RequireAdmin
    @Operation(summary = "依据供应商证据处理歧义 AI 预扣",
            description = "仅超级管理员可操作；确认上游未计费后退款，确认已计费后按实际点数结算")
    @PostMapping("/{requestId}/resolve")
    public Result<AiChargeReviewResolutionVO> resolve(
            @RequestHeader("X-User-Token") String token,
            @PathVariable String requestId,
            @Valid @RequestBody AdminResolveAiChargeRequest request) {
        Long adminId = tokenService.getUserIdFromToken(token);
        User admin = adminId == null ? null : userService.getUserEntityById(adminId);
        if (admin == null || !"SUPER_ADMIN".equals(admin.getRole())) {
            return Result.error(403, "AI 点数人工对账只允许超级管理员处理");
        }

        try {
            reconciliationService.beginResolution(requestId, adminId, request.getAction(),
                    request.getActualPoints(), request.getEvidence());
            AiChargeReviewResolutionVO result = reconciliationService.processResolution(requestId);
            Map<String, Object> detail = new HashMap<>();
            detail.put("requestId", requestId);
            detail.put("action", result.getAction());
            detail.put("status", result.getStatus());
            detail.put("intentStatus", result.getIntentStatus());
            if (result.getActualPoints() != null) detail.put("actualPoints", result.getActualPoints());
            operationLogService.logSuccess(adminId, admin.getUsername(), "AI_CHARGE_RECONCILIATION",
                    "RESOLVE_" + result.getAction(), List.of(result.getUserId()), detail);
            return Result.success(result.getMessage(), result);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        } catch (Exception e) {
            log.error("AI 点数人工对账处理失败: requestId={}", requestId, e);
            Map<String, Object> detail = Map.of("requestId", requestId, "action", request.getAction());
            operationLogService.logFailure(adminId, admin.getUsername(), "AI_CHARGE_RECONCILIATION",
                    "RESOLVE_" + request.getAction(), List.of(), detail, "人工对账处理失败");
            return Result.error(500, "人工对账处理失败，请检查审计记录和点数账本");
        }
    }
}
