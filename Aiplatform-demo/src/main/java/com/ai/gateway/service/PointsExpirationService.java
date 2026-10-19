package com.ai.gateway.service;

import com.ai.gateway.mapper.PointsTemporaryLotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** Repeatedly scans due temporary grants so a failed user can be retried on the next pass. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsExpirationService {

    private static final int BATCH_SIZE = 500;

    private final PointsTemporaryLotMapper lotMapper;
    private final PointsService pointsService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelayString = "${app.points.expiration-poll-ms:60000}")
    public void expireDueTemporaryPoints() {
        LocalDateTime now = LocalDateTime.now();
        long afterUserId = 0L;
        while (true) {
            List<Long> userIds = lotMapper.selectExpiredUserIds(now, afterUserId, BATCH_SIZE);
            if (userIds.isEmpty()) break;
            for (Long userId : userIds) {
                try {
                    pointsService.expireTemporaryPoints(userId, now);
                } catch (Exception e) {
                    // The lot and its Redis replay snapshot remain recoverable for the next pass.
                    log.error("限时点数过期失败，将在后续扫描重试: userId={}", userId, e);
                }
            }
            afterUserId = userIds.get(userIds.size() - 1);
            if (userIds.size() < BATCH_SIZE) break;
        }
        cleanupCommittedSnapshots(now);
    }

    private void cleanupCommittedSnapshots(LocalDateTime now) {
        for (Long lotId : lotMapper.selectRecentlyExpiredIds(now.minusDays(1), 1000)) {
            redisTemplate.delete("points:expiration:" + lotId);
        }
    }
}
