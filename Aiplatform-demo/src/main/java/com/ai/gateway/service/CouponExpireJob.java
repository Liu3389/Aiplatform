package com.ai.gateway.service;

import com.ai.gateway.entity.Coupon;
import com.ai.gateway.mapper.CouponMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 代金券过期定时任务。
 *
 * - 每天 03:00 执行
 * - 对 3 天内即将过期且未使用的券发送提醒通知
 * - 统计已过期未使用的券（不修改 used 字段语义，前端按 expire_time 过滤展示）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireJob {

    private final CouponMapper couponMapper;
    private final NotificationFacadeService notificationFacade;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 过期前提醒天数 */
    private static final int REMIND_BEFORE_DAYS = 3;

    /**
     * 每天 03:00 执行过期检查与提醒。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void checkExpiringCoupons() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime remindBefore = now.plusDays(REMIND_BEFORE_DAYS);
        log.info("代金券过期检查开始: now={}, remindBefore={}", now, remindBefore);

        // 1. 即将过期（now ~ now+3天）且未使用的券 → 发提醒通知
        LambdaQueryWrapper<Coupon> remindWrapper = new LambdaQueryWrapper<>();
        remindWrapper.eq(Coupon::getUsed, false)
                     .isNotNull(Coupon::getExpireTime)
                     .ge(Coupon::getExpireTime, now)
                     .le(Coupon::getExpireTime, remindBefore);
        List<Coupon> expiring = couponMapper.selectList(remindWrapper);
        int reminded = 0;
        for (Coupon c : expiring) {
            try {
                String content = "您有一张面值 ¥" + c.getAmount() + " 的代金券将于 "
                        + c.getExpireTime().format(FMT) + " 过期，请尽快使用。";
                notificationFacade.sendToUser(c.getUserId(), "代金券即将过期", content,
                        "coupon", c.getId(), "{\"couponId\":" + c.getId() + ",\"expireRemind\":true}");
                reminded++;
            } catch (Exception e) {
                log.warn("过期提醒通知失败: couponId={}, userId={}", c.getId(), c.getUserId(), e);
            }
        }

        // 2. 统计已过期未使用的券（仅记录日志，不修改 used 字段；前端按 expire_time 判断展示为"已过期"）
        LambdaQueryWrapper<Coupon> expiredWrapper = new LambdaQueryWrapper<>();
        expiredWrapper.eq(Coupon::getUsed, false)
                      .isNotNull(Coupon::getExpireTime)
                      .lt(Coupon::getExpireTime, now);
        Long expiredCount = couponMapper.selectCount(expiredWrapper);

        log.info("代金券过期检查完成: 即将过期提醒={}, 已过期未使用={}", reminded, expiredCount);
    }
}
