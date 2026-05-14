package com.ai.gateway.service;

import com.ai.gateway.entity.Coupon;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.CouponMapper;
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
    private final UserMapper userMapper;

    public List<Coupon> listByUser(Long userId) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getUserId, userId)
               .orderByDesc(Coupon::getCreateTime);
        return couponMapper.selectList(wrapper);
    }

    @Transactional
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

    @Transactional
    public int sendToAllUsers(BigDecimal amount, BigDecimal thresholdAmount,
                               String applicablePlan, LocalDateTime expireTime) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<>());
        for (User u : users) {
            createCoupon(u.getId(), amount, thresholdAmount, applicablePlan, expireTime);
        }
        log.info("群发代金卷完成: count={}, amount={}", users.size(), amount);
        return users.size();
    }

    @Transactional
    public int sendToMembership(String membership, BigDecimal amount, BigDecimal thresholdAmount,
                                 String applicablePlan, LocalDateTime expireTime) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getMembership, membership);
        List<User> users = userMapper.selectList(wrapper);
        for (User u : users) {
            createCoupon(u.getId(), amount, thresholdAmount, applicablePlan, expireTime);
        }
        log.info("按会员发代金卷完成: membership={}, count={}, amount={}", membership, users.size(), amount);
        return users.size();
    }

    @Transactional
    public int sendToRecentUsers(int days, BigDecimal amount, BigDecimal thresholdAmount,
                                  String applicablePlan, LocalDateTime expireTime) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, since);
        List<User> users = userMapper.selectList(wrapper);
        for (User u : users) {
            createCoupon(u.getId(), amount, thresholdAmount, applicablePlan, expireTime);
        }
        log.info("按新用户发代金卷完成: days={}, count={}, amount={}", days, users.size(), amount);
        return users.size();
    }

    @Transactional
    public BigDecimal useCoupon(Long userId, Long couponId, BigDecimal orderAmount, String plan) {
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
        c.setUsed(true);
        c.setUsedTime(LocalDateTime.now());
        couponMapper.updateById(c);
        log.info("代金卷已使用: userId={}, couponId={}, discount={}", userId, couponId, discount);
        return discount;
    }
}
