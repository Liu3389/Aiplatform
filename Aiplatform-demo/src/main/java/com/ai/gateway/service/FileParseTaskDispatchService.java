package com.ai.gateway.service;

import com.ai.gateway.entity.FileParseTask;
import com.ai.gateway.mapper.FileParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** file_parse_task 本身兼作文件解析 outbox，避免“任务入库、消息未发布”的双写丢失。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "rabbit-enabled", havingValue = "true")
public class FileParseTaskDispatchService {

    private static final int BATCH_SIZE = 100;
    private static final int DISPATCH_LEASE_SECONDS = 30;
    private static final int PARSE_LEASE_SECONDS = 300;
    private static final int MAX_PARSE_ATTEMPTS = 5;

    private final FileParseTaskMapper taskMapper;
    private final RabbitEventPublisher rabbitEventPublisher;

    public boolean dispatchNow(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dispatchStaleBefore = now.minusSeconds(DISPATCH_LEASE_SECONDS);
        LocalDateTime parseStaleBefore = now.minusSeconds(PARSE_LEASE_SECONDS);
        if (taskMapper.claimForDispatch(taskId, dispatchStaleBefore, parseStaleBefore, now,
                MAX_PARSE_ATTEMPTS) != 1) return false;
        if (rabbitEventPublisher.publishFileParseTask(taskId)) return true;
        taskMapper.releaseDispatchClaim(taskId);
        return false;
    }

    /** 重发未确认的 PENDING 任务，也重新投递租约过期的 PARSING 任务。 */
    @Scheduled(fixedDelayString = "${app.messaging.file-parse-poll-ms:10000}")
    public void dispatchRecoverable() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dispatchStaleBefore = now.minusSeconds(DISPATCH_LEASE_SECONDS);
        LocalDateTime parseStaleBefore = now.minusSeconds(PARSE_LEASE_SECONDS);
        taskMapper.failExhaustedExpiredParsing(now, parseStaleBefore, MAX_PARSE_ATTEMPTS);
        List<FileParseTask> recoverable = taskMapper.selectRecoverableForDispatch(now, dispatchStaleBefore,
                parseStaleBefore, MAX_PARSE_ATTEMPTS, BATCH_SIZE);
        for (FileParseTask task : recoverable) dispatchNow(task.getId());
    }
}
