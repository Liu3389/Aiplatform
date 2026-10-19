package com.ai.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Backwards-compatible admin entry point, delegated to the idempotent distribution service. */
@Service
@RequiredArgsConstructor
public class DailyPointsService {

    private final PointsDistributionService pointsDistributionService;

    public void distributeDailyPoints() {
        pointsDistributionService.distributeDailyPoints();
    }

    public void manualDistributePoints(Long userId) {
        pointsDistributionService.manualDistributePoints(userId);
    }
}
