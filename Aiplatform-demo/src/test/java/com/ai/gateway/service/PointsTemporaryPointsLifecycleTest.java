package com.ai.gateway.service;

import com.ai.gateway.dto.UserRegisterRequest;
import com.ai.gateway.entity.PointsBill;
import com.ai.gateway.entity.PointsTemporaryLot;
import com.ai.gateway.mapper.PointsBillMapper;
import com.ai.gateway.mapper.PointsTemporaryLotMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PointsTemporaryPointsLifecycleTest {

    @Autowired private UserService userService;
    @Autowired private UserMapper userMapper;
    @Autowired private PointsService pointsService;
    @Autowired private PointsDistributionService distributionService;
    @Autowired private PointsExpirationService expirationService;
    @Autowired private PointsBillMapper pointsBillMapper;
    @Autowired private PointsTemporaryLotMapper temporaryLotMapper;
    @Autowired private StringRedisTemplate redisTemplate;

    @Test
    void dailyGrantIsIdempotentAndExpiryRemovesOnlyUnspentTemporaryPoints() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("b5_points_" + System.currentTimeMillis());
        request.setPassword("LocalB5Test123");
        UserInfoVO created = userService.register(request);
        Long userId = created.getId();
        assertNotNull(userId);

        userMapper.updatePointsAndIncrementVersion(userId, new BigDecimal("100.000"));
        redisTemplate.opsForValue().set("points:user:" + userId, "100.000");

        LocalDate grantDate = LocalDate.now();
        String grantBusinessId = "daily_temporary_" + grantDate + "_" + userId;
        String permanentBusinessId = "daily_permanent_" + grantDate + "_" + userId;
        pointsService.addPermanentPoints(userId, new BigDecimal("10.000"), "DAILY_PERMANENT_GRANT",
                "test paid-member daily points", permanentBusinessId);
        pointsService.addPermanentPoints(userId, new BigDecimal("10.000"), "DAILY_PERMANENT_GRANT",
                "test paid-member daily points", permanentBusinessId);
        distributionService.distributePointsForUser(userId, grantDate);
        distributionService.distributePointsForUser(userId, grantDate);

        assertEquals(new BigDecimal("140.000"), userMapper.selectById(userId).getPoints());
        List<PointsBill> grants = pointsBillMapper.selectList(new LambdaQueryWrapper<PointsBill>()
                .eq(PointsBill::getUserId, userId)
                .eq(PointsBill::getBusinessId, grantBusinessId)
                .eq(PointsBill::getChangeType, "DAILY_GRANT"));
        assertEquals(1, grants.size());
        List<PointsBill> permanentGrants = pointsBillMapper.selectList(new LambdaQueryWrapper<PointsBill>()
                .eq(PointsBill::getUserId, userId)
                .eq(PointsBill::getBusinessId, permanentBusinessId)
                .eq(PointsBill::getChangeType, "DAILY_PERMANENT_GRANT"));
        assertEquals(1, permanentGrants.size());

        String spendId = "b5-spend-" + userId;
        assertTrue(pointsService.deductPoints(userId, new BigDecimal("20.000"), spendId, "B5_TEST"));
        PointsTemporaryLot lot = temporaryLotMapper.selectByUserAndBusinessId(userId, grantBusinessId);
        assertEquals(new BigDecimal("10.000"), lot.getRemainingPoints());
        lot.setExpireTime(LocalDateTime.now().minusSeconds(1));
        temporaryLotMapper.updateById(lot);

        expirationService.expireDueTemporaryPoints();
        BigDecimal expired = pointsBillMapper.selectList(new LambdaQueryWrapper<PointsBill>()
                        .eq(PointsBill::getUserId, userId)
                        .eq(PointsBill::getBusinessId, "expire_" + lot.getId())
                        .eq(PointsBill::getChangeType, "EXPIRE"))
                .stream()
                .map(PointsBill::getPointsChange)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("10.000"), expired);
        assertEquals(new BigDecimal("110.000"), userMapper.selectById(userId).getPoints());
        assertEquals("110.000", redisTemplate.opsForValue().get("points:user:" + userId));

        List<PointsBill> expirationBills = pointsBillMapper.selectList(new LambdaQueryWrapper<PointsBill>()
                .eq(PointsBill::getUserId, userId)
                .eq(PointsBill::getBusinessId, "expire_" + lot.getId())
                .eq(PointsBill::getChangeType, "EXPIRE"));
        assertEquals(1, expirationBills.size());
        assertEquals(new BigDecimal("-10.000"), expirationBills.get(0).getPointsChange());
        assertEquals(new BigDecimal("120.000"), expirationBills.get(0).getBalanceBefore());
        assertEquals(new BigDecimal("110.000"), expirationBills.get(0).getBalanceAfter());

        expirationService.expireDueTemporaryPoints();
        distributionService.distributePointsForUser(userId, grantDate);
        assertEquals(new BigDecimal("110.000"), userMapper.selectById(userId).getPoints());
    }
}
