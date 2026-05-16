package com.ai.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class BusinessMetricsCollector {

    private final MeterRegistry meterRegistry;

    private final Counter chatSuccessCounter;
    private final Counter chatFailureCounter;
    private final Counter pointsDeductCounter;
    private final Counter pointsDeductFailureCounter;

    private final Timer chatResponseTimer;
    private final Timer pointsDeductTimer;

    public BusinessMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.chatSuccessCounter = Counter.builder("ai.chat.success")
                .tag("result", "success")
                .description("AI对话成功次数")
                .register(meterRegistry);

        this.chatFailureCounter = Counter.builder("ai.chat.success")
                .tag("result", "failure")
                .description("AI对话失败次数")
                .register(meterRegistry);

        this.pointsDeductCounter = Counter.builder("points.deduct")
                .tag("result", "success")
                .description("扣费成功次数")
                .register(meterRegistry);

        this.pointsDeductFailureCounter = Counter.builder("points.deduct")
                .tag("result", "failure")
                .description("扣费失败次数")
                .register(meterRegistry);

        this.chatResponseTimer = Timer.builder("model.response.time")
                .description("模型响应时间")
                .register(meterRegistry);

        this.pointsDeductTimer = Timer.builder("points.deduct.latency")
                .description("扣费耗时")
                .register(meterRegistry);
    }

    public void recordChatSuccess() {
        chatSuccessCounter.increment();
    }

    public void recordChatFailure() {
        chatFailureCounter.increment();
    }

    public void recordPointsDeductSuccess() {
        pointsDeductCounter.increment();
    }

    public void recordPointsDeductFailure() {
        pointsDeductFailureCounter.increment();
    }

    public void recordChatResponseTime(long durationMs) {
        chatResponseTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordPointsDeductLatency(long durationMs) {
        pointsDeductTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
