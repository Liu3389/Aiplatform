package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.Coupon;
import com.ai.gateway.service.CouponService;
import com.ai.gateway.service.PackageTemplateService;
import com.ai.gateway.service.PointsService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.service.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRechargeSecurityTest {

    private UserService userService;
    private TokenService tokenService;
    private CouponService couponService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        tokenService = mock(TokenService.class);
        couponService = mock(CouponService.class);
        controller = new UserController(
                userService,
                tokenService,
                mock(PointsService.class),
                mock(UserSubscriptionService.class),
                mock(PackageTemplateService.class),
                couponService);
        when(tokenService.getUserIdFromToken("valid-user-token")).thenReturn(42L);
    }

    @Test
    void directRechargeNeverCreditsBalance() {
        Result<Void> response = controller.recharge("valid-user-token", new BigDecimal("1000000"));

        assertEquals(503, response.getCode());
        verifyNoInteractions(userService);
    }

    @Test
    void couponRechargeNeverConsumesCouponOrCreditsBalance() {
        Result<Map<String, Object>> response = controller.rechargeWithCoupon(
                "valid-user-token", Map.of("amount", "1000000", "couponId", 1));

        assertEquals(503, response.getCode());
        verifyNoInteractions(userService);
    }

    @Test
    void standaloneCouponUseDoesNotClaimSuccessOrConsumeCoupon() {
        Result<Map<String, Object>> response = controller.useCoupon(
                "valid-user-token", Map.of("code", "COUPON-EXAMPLE"));

        assertEquals(503, response.getCode());
        verifyNoInteractions(userService, couponService);
    }

    @Test
    void unimplementedCouponRedemptionDoesNotClaimSuccessOrCreateCoupon() {
        Result<Void> response = controller.redeemCoupon(
                "valid-user-token", Map.of("redeemCode", "REDEEM-EXAMPLE"));

        assertEquals(503, response.getCode());
        verifyNoInteractions(userService, couponService);
    }

    @Test
    void couponListUsesPersistedUserCouponsAndRealStatus() {
        Coupon persisted = new Coupon();
        persisted.setId(19L);
        persisted.setUserId(42L);
        persisted.setCouponCode("COUPON-REAL");
        persisted.setAmount(new BigDecimal("15.00"));
        persisted.setThresholdAmount(new BigDecimal("50.00"));
        persisted.setExpireTime(LocalDateTime.now().plusDays(1));
        persisted.setUsed(false);
        when(couponService.listByUser(42L)).thenReturn(List.of(persisted));

        Result<List<Map<String, Object>>> response = controller.getCoupons("valid-user-token");

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("COUPON-REAL", response.getData().get(0).get("code"));
        assertEquals(new BigDecimal("15.00"), response.getData().get(0).get("value"));
        assertEquals("AVAILABLE", response.getData().get(0).get("status"));
        verify(couponService).listByUser(42L);
        verifyNoInteractions(userService);
    }
}
