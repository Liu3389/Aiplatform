package com.ai.gateway.service;

import com.ai.gateway.entity.BillingRecord;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.mapper.BillingRecordMapper;
import com.ai.gateway.mapper.UserSubscriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 财务管理服务。
 *
 * 复用现有表，不新建订单表：
 * - 充值记录：billing_record where type=2（充值）
 * - 消费明细：billing_record where type=1（扣费）
 * - 订单管理：user_subscription（套餐订阅订单）
 *
 * 营收概览由 AdminService.getPlatformRevenue() 提供，本服务专注明细列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final BillingRecordMapper billingRecordMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;

    /** billing_record.type: 1-扣费, 2-充值 */
    private static final int TYPE_DEDUCT = 1;
    private static final int TYPE_RECHARGE = 2;

    /**
     * 充值记录分页（billing_record type=2）。
     */
    public Map<String, Object> rechargeList(int page, int size, String startDate, String endDate) {
        return billingPage(page, size, startDate, endDate, TYPE_RECHARGE);
    }

    /**
     * 消费明细分页（billing_record type=1）。
     */
    public Map<String, Object> consumptionList(int page, int size, String startDate, String endDate) {
        return billingPage(page, size, startDate, endDate, TYPE_DEDUCT);
    }

    private Map<String, Object> billingPage(int page, int size, String startDate, String endDate, int type) {
        LambdaQueryWrapper<BillingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRecord::getType, type);
        applyDateRange(wrapper, startDate, endDate);
        wrapper.orderByDesc(BillingRecord::getCreateTime);

        Page<BillingRecord> result = billingRecordMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", result.getRecords());
        resp.put("total", result.getTotal());
        resp.put("page", page);
        resp.put("size", size);
        return resp;
    }

    /**
     * 订单管理分页（user_subscription）。
     *
     * @param status 订阅状态筛选（ACTIVE/EXPIRED/CANCELLED），null 表示全部
     */
    public Map<String, Object> ordersList(int page, int size, String status, String startDate, String endDate) {
        LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(UserSubscription::getStatus, status);
        }
        if (startDate != null) wrapper.ge(UserSubscription::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
        if (endDate != null) wrapper.le(UserSubscription::getCreateTime, LocalDate.parse(endDate).atTime(23, 59, 59));
        wrapper.orderByDesc(UserSubscription::getCreateTime);

        Page<UserSubscription> result = userSubscriptionMapper.selectPage(new Page<>(page, size), wrapper);

        // 汇总当前筛选条件下的实收金额
        LambdaQueryWrapper<UserSubscription> sumWrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) sumWrapper.eq(UserSubscription::getStatus, status);
        if (startDate != null) sumWrapper.ge(UserSubscription::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
        if (endDate != null) sumWrapper.le(UserSubscription::getCreateTime, LocalDate.parse(endDate).atTime(23, 59, 59));
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (UserSubscription s : userSubscriptionMapper.selectList(sumWrapper)) {
            if (s.getPricePaid() != null) totalPaid = totalPaid.add(s.getPricePaid());
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", result.getRecords());
        resp.put("total", result.getTotal());
        resp.put("page", page);
        resp.put("size", size);
        resp.put("totalPaid", totalPaid);
        return resp;
    }

    private void applyDateRange(LambdaQueryWrapper<BillingRecord> wrapper, String startDate, String endDate) {
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(BillingRecord::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(BillingRecord::getCreateTime, LocalDate.parse(endDate).atTime(23, 59, 59));
        }
    }
}
