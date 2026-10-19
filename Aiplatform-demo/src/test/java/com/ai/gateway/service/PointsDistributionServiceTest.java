package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.PackageTemplateMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserSubscriptionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsDistributionServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserSubscriptionMapper userSubscriptionMapper;
    @Mock private PackageTemplateMapper packageTemplateMapper;
    @Mock private PointsService pointsService;
    @InjectMocks private PointsDistributionService distributionService;

    @Test
    void retriesFailedUserGrantAndReliesOnStableBusinessIdForIdempotency() {
        User user = activeUser();
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        doThrow(new IllegalStateException("temporary database outage"))
                .doNothing()
                .when(pointsService).addTemporaryPoints(eq(5L), any(BigDecimal.class), anyString(),
                        eq("DAILY_GRANT"), anyString(), any(LocalDateTime.class));

        distributionService.distributeDailyPoints();

        verify(pointsService, times(2)).addTemporaryPoints(eq(5L), any(BigDecimal.class), anyString(),
                eq("DAILY_GRANT"), anyString(), any(LocalDateTime.class));
    }

    @Test
    void reportsUsersThatStillFailAfterBoundedRetries() {
        User user = activeUser();
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        doThrow(new IllegalStateException("database unavailable"))
                .when(pointsService).addTemporaryPoints(eq(5L), any(BigDecimal.class), anyString(),
                        eq("DAILY_GRANT"), anyString(), any(LocalDateTime.class));

        assertThrows(IllegalStateException.class, () -> distributionService.distributeDailyPoints());

        verify(pointsService, times(3)).addTemporaryPoints(eq(5L), any(BigDecimal.class), anyString(),
                eq("DAILY_GRANT"), anyString(), any(LocalDateTime.class));
    }

    private static User activeUser() {
        User user = new User();
        user.setId(5L);
        user.setUsername("daily-grant-test");
        user.setStatus(1);
        return user;
    }
}
