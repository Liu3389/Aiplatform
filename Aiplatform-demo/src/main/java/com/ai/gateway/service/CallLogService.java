package com.ai.gateway.service;

import com.ai.gateway.entity.BillingRecord;
import com.ai.gateway.entity.CallLog;
import com.ai.gateway.mapper.BillingRecordMapper;
import com.ai.gateway.mapper.CallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 日志和计费记录服务类
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallLogService {

    private final CallLogMapper callLogMapper;
    private final BillingRecordMapper billingRecordMapper;

    /**
     * 保存调用日志
     * 
     * @param callLog 调用日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveCallLog(CallLog callLog) {
        callLog.setCreateTime(LocalDateTime.now());
        callLogMapper.insert(callLog);
        log.debug("调用日志保存成功: callLogId={}", callLog.getId());
    }

    /**
     * 保存计费记录
     * 
     * @param billingRecord 计费记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveBillingRecord(BillingRecord billingRecord) {
        billingRecord.setCreateTime(LocalDateTime.now());
        billingRecordMapper.insert(billingRecord);
        log.debug("计费记录保存成功: billingRecordId={}", billingRecord.getId());
    }

    /**
     * 创建并保存计费记录
     * 
     * @param userId 用户ID
     * @param callLogId 调用日志ID
     * @param amount 金额
     * @param type 类型（1-扣费，2-充值）
     * @param balanceBefore 操作前余额
     * @param balanceAfter 操作后余额
     */
    @Transactional(rollbackFor = Exception.class)
    public void createBillingRecord(Long userId, Long callLogId, BigDecimal amount, 
                                     Integer type, BigDecimal balanceBefore, BigDecimal balanceAfter) {
        BillingRecord record = new BillingRecord();
        record.setUserId(userId);
        record.setCallLogId(callLogId);
        record.setAmount(amount);
        record.setType(type);
        record.setBalanceBefore(balanceBefore);
        record.setBalanceAfter(balanceAfter);
        
        saveBillingRecord(record);
    }
}
