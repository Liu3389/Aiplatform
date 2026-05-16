package com.ai.gateway.service;

import com.ai.gateway.entity.Coupon;
import com.ai.gateway.entity.CouponUsageRecord;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.CouponMapper;
import com.ai.gateway.mapper.CouponUsageRecordMapper;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final CouponUsageRecordMapper couponUsageRecordMapper;
    private final UserMapper userMapper;

    public List<Coupon> listByUser(Long userId) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getUserId, userId)
               .orderByDesc(Coupon::getCreateTime);
        return couponMapper.selectList(wrapper);
    }

    /**
     * 获取用户优惠券使用记录
     */
    public List<CouponUsageRecord> listUsageRecords(Long userId) {
        LambdaQueryWrapper<CouponUsageRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CouponUsageRecord::getUserId, userId)
               .orderByDesc(CouponUsageRecord::getUsageTime);
        return couponUsageRecordMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Coupon createCoupon(Long userId, BigDecimal amount, BigDecimal thresholdAmount,
                                String applicablePlan, LocalDateTime expireTime) {
        Coupon c = new Coupon();
        c.setUserId(userId);
        c.setCouponCode("COUPON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setAmount(amount);
        c.setThresholdAmount(thresholdAmount != null ? thresholdAmount : BigDecimal.ZERO);
        c.setApplicablePlan(applicablePlan);
        c.setExpireTime(expireTime);
        c.setUsed(false);
        c.setCreateTime(LocalDateTime.now());
        couponMapper.insert(c);
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public int sendToAllUsers(BigDecimal amount, BigDecimal thresholdAmount,
                               String applicablePlan, LocalDateTime expireTime) {
        Long totalCount = userMapper.selectCount(new LambdaQueryWrapper<>());
        if (totalCount > 10000) {
            log.warn("目标用户数量过大({}), 建议分批发送", totalCount);
            throw new RuntimeException("目标用户数量超过10000，请分批发送");
        }
        
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<>());
        int sentCount = 0;
        int skipCount = 0;
        for (User u : users) {
            if (hasUnusedCoupon(u.getId(), amount, applicablePlan)) {
                skipCount++;
                continue;
            }
            createCoupon(u.getId(), amount, thresholdAmount, applicablePlan, expireTime);
            sentCount++;
        }
        log.info("群发代金卷完成: sent={}, skipped={}, amount={}", sentCount, skipCount, amount);
        return sentCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public int sendToMembership(String membership, BigDecimal amount, BigDecimal thresholdAmount,
                                 String applicablePlan, LocalDateTime expireTime) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getMembership, membership);
        Long totalCount = userMapper.selectCount(wrapper);
        if (totalCount > 10000) {
            log.warn("目标用户数量过大({}), 建议分批发送", totalCount);
            throw new RuntimeException("目标用户数量超过10000，请分批发送");
        }
        
        List<User> users = userMapper.selectList(wrapper);
        int sentCount = 0;
        int skipCount = 0;
        for (User u : users) {
            if (hasUnusedCoupon(u.getId(), amount, applicablePlan)) {
                skipCount++;
                continue;
            }
            createCoupon(u.getId(), amount, thresholdAmount, applicablePlan, expireTime);
            sentCount++;
        }
        log.info("按会员发代金卷完成: membership={}, sent={}, skipped={}, amount={}", membership, sentCount, skipCount, amount);
        return sentCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public int sendToRecentUsers(int days, BigDecimal amount, BigDecimal thresholdAmount,
                                  String applicablePlan, LocalDateTime expireTime) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, since);
        Long totalCount = userMapper.selectCount(wrapper);
        if (totalCount > 10000) {
            log.warn("目标用户数量过大({}), 建议分批发送", totalCount);
            throw new RuntimeException("目标用户数量超过10000，请分批发送");
        }
        
        List<User> users = userMapper.selectList(wrapper);
        int sentCount = 0;
        int skipCount = 0;
        for (User u : users) {
            if (hasUnusedCoupon(u.getId(), amount, applicablePlan)) {
                skipCount++;
                continue;
            }
            createCoupon(u.getId(), amount, thresholdAmount, applicablePlan, expireTime);
            sentCount++;
        }
        log.info("按新用户发代金卷完成: days={}, sent={}, skipped={}, amount={}", days, sentCount, skipCount, amount);
        return sentCount;
    }

    /**
     * 检查用户是否已有相同面额和套餐的未使用优惠券（防重领）
     */
    private boolean hasUnusedCoupon(Long userId, BigDecimal amount, String applicablePlan) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getUserId, userId)
               .eq(Coupon::getAmount, amount)
               .eq(Coupon::getUsed, false);
        if (applicablePlan == null || applicablePlan.isEmpty()) {
            wrapper.and(w -> w.isNull(Coupon::getApplicablePlan).or().eq(Coupon::getApplicablePlan, ""));
        } else {
            wrapper.eq(Coupon::getApplicablePlan, applicablePlan);
        }
        return couponMapper.selectCount(wrapper) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public BigDecimal useCoupon(Long userId, Long couponId, BigDecimal orderAmount, String plan, 
                                String orderType, String orderId) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || !c.getUserId().equals(userId)) throw new RuntimeException("代金卷不存在");
        if (Boolean.TRUE.equals(c.getUsed())) throw new RuntimeException("代金卷已使用");
        if (c.getExpireTime() != null && c.getExpireTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException("代金卷已过期");
        if (c.getThresholdAmount().compareTo(BigDecimal.ZERO) > 0 && orderAmount.compareTo(c.getThresholdAmount()) < 0)
            throw new RuntimeException("未达到使用门槛 ¥" + c.getThresholdAmount());
        if (c.getApplicablePlan() != null && !c.getApplicablePlan().isEmpty() && !c.getApplicablePlan().equals(plan))
            throw new RuntimeException("此代金卷仅适用于 " + c.getApplicablePlan() + " 套餐");

        BigDecimal discount = c.getAmount().min(orderAmount);
        BigDecimal actualAmount = orderAmount.subtract(discount);
        
        // 标记优惠券为已使用
        c.setUsed(true);
        c.setUsedTime(LocalDateTime.now());
        couponMapper.updateById(c);
        
        // 记录优惠券使用历史
        CouponUsageRecord usageRecord = new CouponUsageRecord();
        usageRecord.setUserId(userId);
        usageRecord.setCouponId(couponId);
        usageRecord.setCouponCode(c.getCouponCode());
        usageRecord.setCouponAmount(c.getAmount());
        usageRecord.setOrderType(orderType != null ? orderType : "RECHARGE");
        usageRecord.setOrderId(orderId);
        usageRecord.setOrderAmount(orderAmount);
        usageRecord.setDiscountAmount(discount);
        usageRecord.setActualAmount(actualAmount);
        usageRecord.setUsageTime(LocalDateTime.now());
        usageRecord.setRemark("优惠券使用 - " + (plan != null ? plan : "通用"));
        couponUsageRecordMapper.insert(usageRecord);
        
        log.info("代金卷已使用: userId={}, couponId={}, discount={}, orderType={}, orderId={}", 
                userId, couponId, discount, orderType, orderId);
        return discount;
    }
}
