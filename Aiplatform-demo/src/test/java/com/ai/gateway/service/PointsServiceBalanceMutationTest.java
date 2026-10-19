package com.ai.gateway.service;

import com.ai.gateway.entity.PointsBill;
import com.ai.gateway.entity.BillingRecord;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.BillingRecordMapper;
import com.ai.gateway.mapper.PointsBillMapper;
import com.ai.gateway.mapper.PointsTemporaryLotMapper;
import com.ai.gateway.mapper.AiChargeIntentMapper;
import com.ai.gateway.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointsServiceBalanceMutationTest {

    private static final Long USER_ID = 42L;
    private static final String POINTS_KEY = "points:user:42";

    private UserMapper userMapper;
    private PointsBillMapper pointsBillMapper;
    private BillingRecordMapper billingRecordMapper;
    private PointsTemporaryLotMapper temporaryLotMapper;
    private AiChargeIntentMapper aiChargeIntentMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private HashOperations<String, Object, Object> hashOperations;
    private PointsService pointsService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        pointsBillMapper = mock(PointsBillMapper.class);
        billingRecordMapper = mock(BillingRecordMapper.class);
        temporaryLotMapper = mock(PointsTemporaryLotMapper.class);
        aiChargeIntentMapper = mock(AiChargeIntentMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), any())).thenReturn(null);
        when(valueOperations.get(POINTS_KEY)).thenReturn("100.000");
        when(temporaryLotMapper.selectActiveForUpdate(anyLong())).thenReturn(List.of());
        User databaseUser = new User();
        databaseUser.setId(USER_ID);
        databaseUser.setPoints(new BigDecimal("100.000"));
        databaseUser.setPointsVersion(0L);
        when(userMapper.selectOne(any())).thenReturn(databaseUser);
        when(userMapper.updatePointsAndIncrementVersion(anyLong(), any(BigDecimal.class))).thenReturn(1);
        when(billingRecordMapper.selectOne(any())).thenReturn(null);
        pointsService = new PointsService(
                pointsBillMapper,
                redisTemplate,
                userMapper,
                mock(PlatformModelService.class),
                billingRecordMapper,
                temporaryLotMapper,
                aiChargeIntentMapper);
        pointsService.init();
    }

    @Test
    void grantUsesRedisBalanceWithAnUnsettledReservationInsteadOfOverwritingItFromDatabase() {
        when(valueOperations.get(POINTS_KEY)).thenReturn("90.000");

        BigDecimal result = pointsService.addPoints(
                USER_ID, new BigDecimal("20.000"), "GRANT", "test-grant", "test grant", null, 0);

        assertEquals(new BigDecimal("110.000"), result);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString());
        verify(valueOperations).set(POINTS_KEY, "110.000");
        verify(userMapper, never()).selectById(USER_ID);

        org.mockito.ArgumentCaptor<PointsBill> billCaptor = org.mockito.ArgumentCaptor.forClass(PointsBill.class);
        verify(pointsBillMapper).insert(billCaptor.capture());
        assertEquals(new BigDecimal("90.000"), billCaptor.getValue().getBalanceBefore());
        assertEquals(new BigDecimal("110.000"), billCaptor.getValue().getBalanceAfter());

        verify(userMapper).updatePointsAndIncrementVersion(USER_ID, new BigDecimal("110.000"));
    }

    @Test
    void cacheMissInitializesFromLockedDatabaseRowOnlyOnceBeforeGrant() {
        when(valueOperations.get(POINTS_KEY)).thenReturn(null, "100.000");
        when(valueOperations.setIfAbsent(POINTS_KEY, "100.000")).thenReturn(true);

        BigDecimal result = pointsService.addPoints(
                USER_ID, new BigDecimal("2.500"), "GRANT", "test-grant-cache-miss", "test grant", null, 0);

        assertEquals(new BigDecimal("102.500"), result);
        verify(valueOperations).setIfAbsent(POINTS_KEY, "100.000");
        verify(valueOperations).set(POINTS_KEY, "102.500");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replayedSettlementDoesNotOverwriteBalanceWhenDatabaseVersionAdvanced() {
        User databaseUser = new User();
        databaseUser.setId(USER_ID);
        databaseUser.setPoints(new BigDecimal("105.000"));
        databaseUser.setPointsVersion(7L);
        when(userMapper.selectOne(any())).thenReturn(databaseUser);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(Arrays.asList(2L, "95.000", "10.000", "4", "2026-10-18T10:15:30"));

        boolean settled = pointsService.settlePoints(USER_ID, "request-replay", new BigDecimal("5.000"), "model");

        assertTrue(settled);
        verify(userMapper, never()).updatePointsAndIncrementVersion(anyLong(), any(BigDecimal.class));
        verify(billingRecordMapper).insert(any(BillingRecord.class));
        org.mockito.ArgumentCaptor<PointsBill> billCaptor = org.mockito.ArgumentCaptor.forClass(PointsBill.class);
        verify(pointsBillMapper).insert(billCaptor.capture());
        assertEquals(new BigDecimal("100.000"), billCaptor.getValue().getBalanceBefore());
        assertEquals(new BigDecimal("95.000"), billCaptor.getValue().getBalanceAfter());
        assertEquals(LocalDateTime.parse("2026-10-18T10:15:30"), billCaptor.getValue().getCreateTime());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replayedSettlementRepairsDatabaseWhenNoNewerBalanceMutationExists() {
        User databaseUser = new User();
        databaseUser.setId(USER_ID);
        databaseUser.setPoints(new BigDecimal("100.000"));
        databaseUser.setPointsVersion(4L);
        when(userMapper.selectOne(any())).thenReturn(databaseUser);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(Arrays.asList(2L, "95.000", "10.000", "4", "2026-10-18T10:15:30"));

        boolean settled = pointsService.settlePoints(USER_ID, "request-replay-repair", new BigDecimal("5.000"), "model");

        assertTrue(settled);
        verify(userMapper).updatePointsAndIncrementVersion(USER_ID, new BigDecimal("95.000"));
        verify(billingRecordMapper).insert(any(BillingRecord.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void legacyReplayWithoutVersionMetadataFailsClosed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(Arrays.asList(-2L, "legacy_settlement_metadata_missing", "95.000", "10.000", ""));

        boolean settled = pointsService.settlePoints(USER_ID, "request-legacy-replay", new BigDecimal("5.000"), "model");

        assertFalse(settled);
        verify(billingRecordMapper, never()).insert(any(BillingRecord.class));
        verify(pointsBillMapper, never()).insert(any(PointsBill.class));
        verify(userMapper, never()).updatePointsAndIncrementVersion(anyLong(), any(BigDecimal.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void expiredRedisReservationUsesDurableFallbackAmountToRefundOnce() {
        when(valueOperations.get(POINTS_KEY)).thenReturn("95.000");
        when(valueOperations.get("points:pre_deduct:request-expired-reservation")).thenReturn(null);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(Arrays.asList(1L, "100.000", "0", "5.000"));

        boolean refunded = pointsService.rollbackPreDeduct(USER_ID, "request-expired-reservation",
                new BigDecimal("5.000"), true);

        assertTrue(refunded);
        verify(userMapper).updatePointsAndIncrementVersion(USER_ID, new BigDecimal("100.000"));
        org.mockito.ArgumentCaptor<PointsBill> billCaptor = org.mockito.ArgumentCaptor.forClass(PointsBill.class);
        verify(pointsBillMapper).insert(billCaptor.capture());
        assertEquals("ROLLBACK", billCaptor.getValue().getChangeType());
        assertEquals("request-expired-reservation", billCaptor.getValue().getBusinessId());
        assertEquals(new BigDecimal("5.000"), billCaptor.getValue().getPointsChange());
        assertEquals(new BigDecimal("95.000"), billCaptor.getValue().getBalanceBefore());
        assertEquals(new BigDecimal("100.000"), billCaptor.getValue().getBalanceAfter());
    }
}
