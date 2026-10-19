package com.ai.gateway.service;

import com.ai.gateway.entity.AiChargeIntent;
import com.ai.gateway.entity.AiChargeReviewAudit;
import com.ai.gateway.mapper.AiChargeIntentMapper;
import com.ai.gateway.mapper.AiChargeReviewAuditMapper;
import com.ai.gateway.vo.AdminPageVO;
import com.ai.gateway.vo.AiChargeReviewAuditVO;
import com.ai.gateway.vo.AiChargeReviewQueueItemVO;
import com.ai.gateway.vo.AiChargeReviewResolutionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** Evidence-backed, idempotent administrator workflow for ambiguous upstream billing results. */
@Service
@RequiredArgsConstructor
public class AiChargeReconciliationService {

    private static final String ACTION_REFUND = "REFUND";
    private static final String ACTION_SETTLE = "SETTLE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern SECRET_LIKE_VALUE = Pattern.compile(
            "(?i)(sk-[a-z0-9_-]{16,}|bearer\\s+[a-z0-9._~+/=-]{20,}|"
                    + "(?:api[_-]?key|client[_-]?secret|password|access[_-]?token)\\s*[:=]\\s*[^\\s,;]+)");

    private final AiChargeIntentMapper intentMapper;
    private final AiChargeReviewAuditMapper auditMapper;
    private final AiChargeIntentService intentService;
    private final BillingSettlementCoordinator settlementCoordinator;
    private final BillingSettlementOutboxService outboxService;

    @Transactional(readOnly = true)
    public AdminPageVO<AiChargeReviewQueueItemVO> listManualReview(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AiChargeIntent> query = new Page<>(safePage, safeSize);
        Page<AiChargeIntent> rows = intentMapper.selectPage(query,
                new LambdaQueryWrapper<AiChargeIntent>()
                        .eq(AiChargeIntent::getStatus, AiChargeIntent.MANUAL_REVIEW)
                        .orderByAsc(AiChargeIntent::getUpdateTime)
                        .orderByAsc(AiChargeIntent::getId));
        AdminPageVO<AiChargeReviewQueueItemVO> result = new AdminPageVO<>();
        result.setRecords(rows.getRecords().stream().map(this::toQueueItem).toList());
        result.setTotal(rows.getTotal());
        result.setPage(rows.getCurrent());
        result.setSize(rows.getSize());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminPageVO<AiChargeReviewAuditVO> listReviewHistory(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AiChargeReviewAudit> query = new Page<>(safePage, safeSize);
        Page<AiChargeReviewAudit> rows = auditMapper.selectPage(query,
                new LambdaQueryWrapper<AiChargeReviewAudit>()
                        .orderByDesc(AiChargeReviewAudit::getCreateTime)
                        .orderByDesc(AiChargeReviewAudit::getId));
        AdminPageVO<AiChargeReviewAuditVO> result = new AdminPageVO<>();
        result.setRecords(rows.getRecords().stream().map(this::syncAndMapAudit).toList());
        result.setTotal(rows.getTotal());
        result.setPage(rows.getCurrent());
        result.setSize(rows.getSize());
        return result;
    }

    /** Commit the one-time resolution choice before touching Redis or the settlement outbox. */
    @Transactional(rollbackFor = Exception.class)
    public AiChargeReviewAudit beginResolution(String requestId, Long adminId, String action,
                                               BigDecimal actualPoints, String evidence) {
        if (!ACTION_REFUND.equals(action) && !ACTION_SETTLE.equals(action)) {
            throw new IllegalArgumentException("处理方式必须是 REFUND 或 SETTLE");
        }
        if (evidence == null || evidence.isBlank() || evidence.trim().length() < 8
                || evidence.length() > 1000) {
            throw new IllegalArgumentException("请填写 8 到 1000 个字符的供应商日志引用和核验结论");
        }
        if (SECRET_LIKE_VALUE.matcher(evidence).find()) {
            throw new IllegalArgumentException("证据字段不能包含密钥、Bearer Token 或密码；请只填写供应商请求编号和核验结论");
        }

        AiChargeIntent intent = intentMapper.selectByRequestId(requestId);
        if (intent == null) throw new IllegalArgumentException("找不到对应的 AI 预扣请求");
        validateAction(action, actualPoints, intent.getReservedPoints());

        AiChargeReviewAudit existing = auditMapper.selectByRequestId(requestId);
        if (existing != null) {
            if (!sameResolution(existing, adminId, action, actualPoints, evidence)) {
                throw new IllegalStateException("该请求已经登记了不同的对账处理，不能覆盖原决定");
            }
            return existing;
        }
        if (!AiChargeIntent.MANUAL_REVIEW.equals(intent.getStatus())) {
            throw new IllegalStateException("该请求已离开人工对账状态，不能再次处理");
        }

        LocalDateTime now = LocalDateTime.now();
        AiChargeReviewAudit audit = new AiChargeReviewAudit();
        audit.setRequestId(requestId);
        audit.setUserId(intent.getUserId());
        audit.setAdminId(adminId);
        audit.setAction(action);
        audit.setActualPoints(actualPoints);
        audit.setEvidence(evidence.trim());
        audit.setStatus(STATUS_PENDING);
        audit.setCreateTime(now);
        audit.setUpdateTime(now);
        auditMapper.insert(audit);

        String targetStatus = ACTION_REFUND.equals(action)
                ? AiChargeIntent.REFUND_PENDING : AiChargeIntent.SETTLEMENT_PENDING;
        String stateReason = ACTION_REFUND.equals(action)
                ? "管理员依据供应商证据发起退款；处理记录见对账审计表"
                : "管理员依据供应商证据发起结算；处理记录见对账审计表";
        if (intentMapper.claimManualReviewResolution(requestId, targetStatus, actualPoints, stateReason, now) != 1) {
            throw new IllegalStateException("请求状态已被其他处理更新，请刷新队列后重试");
        }
        return audit;
    }

    /** Retry an accepted resolution safely; the refund marker, outbox, and ledger are idempotent. */
    public AiChargeReviewResolutionVO processResolution(String requestId) {
        AiChargeReviewAudit audit = auditMapper.selectByRequestId(requestId);
        if (audit == null) throw new IllegalArgumentException("找不到对账审计记录");

        AiChargeIntent intent = intentService.getByRequestId(requestId);
        if (intent == null) throw new IllegalStateException("对账意图不存在，需检查数据完整性");
        if (STATUS_RESOLVED.equals(audit.getStatus())) return resolutionVO(audit, intent);

        String lastError = null;
        try {
            if (ACTION_REFUND.equals(audit.getAction())) {
                outboxService.refundIntent(requestId, "管理员依据上游证据确认未计费并发起退款");
            } else {
                settlementCoordinator.settle(intent.getUserId(), requestId,
                        audit.getActualPoints(), intent.getModelName());
            }
        } catch (RuntimeException e) {
            lastError = "处理暂未完成，将由恢复扫描器重试";
        }

        intent = intentService.getByRequestId(requestId);
        boolean resolved = ACTION_REFUND.equals(audit.getAction())
                ? AiChargeIntent.REFUNDED.equals(intent.getStatus())
                : AiChargeIntent.SETTLED.equals(intent.getStatus());
        boolean recoverable = ACTION_REFUND.equals(audit.getAction())
                ? AiChargeIntent.REFUND_PENDING.equals(intent.getStatus())
                : AiChargeIntent.SETTLEMENT_PENDING.equals(intent.getStatus());

        String status = resolved ? STATUS_RESOLVED : recoverable ? STATUS_PENDING : STATUS_FAILED;
        if (!resolved && lastError == null) {
            lastError = recoverable ? "处理已持久化，等待后台恢复扫描器完成"
                    : "请求状态与登记的处理方向不一致，需管理员检查账本";
        }
        LocalDateTime now = LocalDateTime.now();
        auditMapper.updateOutcome(requestId, status, lastError, now, resolved ? now : null);
        audit.setStatus(status);
        audit.setLastError(lastError);
        audit.setUpdateTime(now);
        audit.setCompletedAt(resolved ? now : null);
        return resolutionVO(audit, intent);
    }

    private void validateAction(String action, BigDecimal actualPoints, BigDecimal reservedPoints) {
        if (ACTION_REFUND.equals(action)) {
            if (actualPoints != null) throw new IllegalArgumentException("退款处理不应提交实际点数");
            return;
        }
        if (actualPoints == null || actualPoints.signum() <= 0) {
            throw new IllegalArgumentException("结算处理必须提交大于 0 的实际上游消费点数");
        }
        if (reservedPoints == null || actualPoints.compareTo(reservedPoints) > 0) {
            throw new IllegalArgumentException("实际点数不能超过原预扣点数");
        }
        if (actualPoints.scale() > 3) throw new IllegalArgumentException("实际点数最多保留 3 位小数");
    }

    private boolean sameResolution(AiChargeReviewAudit audit, Long adminId, String action,
                                   BigDecimal actualPoints, String evidence) {
        boolean pointsMatch = audit.getActualPoints() == null ? actualPoints == null
                : actualPoints != null && audit.getActualPoints().compareTo(actualPoints) == 0;
        return adminId != null && adminId.equals(audit.getAdminId()) && action.equals(audit.getAction())
                && pointsMatch && audit.getEvidence().equals(evidence.trim());
    }

    private AiChargeReviewQueueItemVO toQueueItem(AiChargeIntent intent) {
        AiChargeReviewQueueItemVO item = new AiChargeReviewQueueItemVO();
        item.setRequestId(intent.getRequestId());
        item.setUserId(intent.getUserId());
        item.setReservedPoints(intent.getReservedPoints());
        item.setModelName(intent.getModelName());
        item.setUpstreamStartedAt(intent.getUpstreamStartedAt());
        item.setCreateTime(intent.getCreateTime());
        item.setUpdateTime(intent.getUpdateTime());
        item.setReason(intent.getLastError());
        return item;
    }

    private AiChargeReviewAuditVO toAuditVO(AiChargeReviewAudit audit) {
        AiChargeReviewAuditVO item = new AiChargeReviewAuditVO();
        item.setRequestId(audit.getRequestId());
        item.setUserId(audit.getUserId());
        item.setAdminId(audit.getAdminId());
        item.setAction(audit.getAction());
        item.setActualPoints(audit.getActualPoints());
        item.setEvidence(audit.getEvidence());
        item.setStatus(audit.getStatus());
        item.setLastError(audit.getLastError());
        item.setCreateTime(audit.getCreateTime());
        item.setUpdateTime(audit.getUpdateTime());
        item.setCompletedAt(audit.getCompletedAt());
        return item;
    }

    private AiChargeReviewAuditVO syncAndMapAudit(AiChargeReviewAudit audit) {
        if (STATUS_PENDING.equals(audit.getStatus())) {
            AiChargeIntent intent = intentMapper.selectByRequestId(audit.getRequestId());
            boolean resolved = intent != null && (ACTION_REFUND.equals(audit.getAction())
                    ? AiChargeIntent.REFUNDED.equals(intent.getStatus())
                    : AiChargeIntent.SETTLED.equals(intent.getStatus()));
            if (resolved) {
                LocalDateTime now = LocalDateTime.now();
                auditMapper.updateOutcome(audit.getRequestId(), STATUS_RESOLVED, null, now, now);
                audit.setStatus(STATUS_RESOLVED);
                audit.setLastError(null);
                audit.setUpdateTime(now);
                audit.setCompletedAt(now);
            }
        }
        return toAuditVO(audit);
    }

    private AiChargeReviewResolutionVO resolutionVO(AiChargeReviewAudit audit, AiChargeIntent intent) {
        AiChargeReviewResolutionVO result = new AiChargeReviewResolutionVO();
        result.setRequestId(audit.getRequestId());
        result.setUserId(audit.getUserId());
        result.setAction(audit.getAction());
        result.setStatus(audit.getStatus());
        result.setIntentStatus(intent.getStatus());
        result.setActualPoints(audit.getActualPoints());
        result.setMessage(STATUS_RESOLVED.equals(audit.getStatus())
                ? "对账处理已完成" : "处理已记录；如未完成，后台恢复扫描器会继续重试");
        return result;
    }
}
