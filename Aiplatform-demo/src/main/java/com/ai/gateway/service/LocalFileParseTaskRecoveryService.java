package com.ai.gateway.service;

import com.ai.gateway.entity.FileParseTask;
import com.ai.gateway.mapper.FileParseTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/** RabbitMQ 关闭时扫描持久化任务，并交由本地有界线程池执行或恢复。 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.messaging", name = "rabbit-enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileParseTaskRecoveryService {

    private static final int BATCH_SIZE = 100;
    private static final int DISPATCH_LEASE_SECONDS = 30;
    private static final int PARSE_LEASE_SECONDS = 300;
    private static final int MAX_PARSE_ATTEMPTS = 5;

    private final FileParseTaskMapper taskMapper;
    private final ObjectProvider<FileParseService> fileParseServiceProvider;
    private final Executor fileParseExecutor;

    public LocalFileParseTaskRecoveryService(FileParseTaskMapper taskMapper,
                                             ObjectProvider<FileParseService> fileParseServiceProvider,
                                             @Qualifier("fileParseExecutor") Executor fileParseExecutor) {
        this.taskMapper = taskMapper;
        this.fileParseServiceProvider = fileParseServiceProvider;
        this.fileParseExecutor = fileParseExecutor;
    }

    /** 上传后立即执行；失败或进程退出留下的任务由定时扫描重新领取。 */
    public boolean dispatchNow(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        if (!claimDispatch(taskId, now)) return false;
        try {
            fileParseExecutor.execute(() -> fileParseServiceProvider.getObject().processFileTask(taskId));
            return true;
        } catch (RuntimeException e) {
            taskMapper.releaseDispatchClaim(taskId);
            log.warn("本地文件解析任务未能提交到线程池: taskId={}", taskId, e);
            return false;
        }
    }

    /** 本地模式也有数据库 outbox 扫描器，保证应用重启后 PENDING/过期 PARSING 能恢复。 */
    @Scheduled(fixedDelayString = "${app.messaging.file-parse-poll-ms:10000}")
    public void recoverRecoverableTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dispatchStaleBefore = now.minusSeconds(DISPATCH_LEASE_SECONDS);
        LocalDateTime parseStaleBefore = now.minusSeconds(PARSE_LEASE_SECONDS);
        taskMapper.failExhaustedExpiredParsing(now, parseStaleBefore, MAX_PARSE_ATTEMPTS);
        List<FileParseTask> recoverable = taskMapper.selectRecoverableForDispatch(now, dispatchStaleBefore,
                parseStaleBefore, MAX_PARSE_ATTEMPTS, BATCH_SIZE);
        for (FileParseTask task : recoverable) dispatchNow(task.getId());
    }

    private boolean claimDispatch(Long taskId, LocalDateTime now) {
        return taskMapper.claimForDispatch(taskId, now.minusSeconds(DISPATCH_LEASE_SECONDS),
                now.minusSeconds(PARSE_LEASE_SECONDS), now, MAX_PARSE_ATTEMPTS) == 1;
    }
}
