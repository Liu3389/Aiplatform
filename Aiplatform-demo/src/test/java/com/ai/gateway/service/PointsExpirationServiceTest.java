package com.ai.gateway.service;

import com.ai.gateway.mapper.PointsTemporaryLotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsExpirationServiceTest {

    @Mock private PointsTemporaryLotMapper lotMapper;
    @Mock private PointsService pointsService;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @InjectMocks private PointsExpirationService expirationService;

    @Test
    void retriesFailedUserOnNextScheduledScan() {
        when(lotMapper.selectExpiredUserIds(any(LocalDateTime.class), eq(0L), eq(500)))
                .thenReturn(List.of(5L));
        when(lotMapper.selectRecentlyExpiredIds(any(LocalDateTime.class), eq(1000)))
                .thenReturn(List.of());
        when(pointsService.expireTemporaryPoints(eq(5L), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("temporary database outage"))
                .thenReturn(BigDecimal.ZERO);

        expirationService.expireDueTemporaryPoints();
        expirationService.expireDueTemporaryPoints();

        verify(pointsService, times(2)).expireTemporaryPoints(eq(5L), any(LocalDateTime.class));
    }
}
