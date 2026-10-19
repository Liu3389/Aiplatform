package com.ai.gateway.service;

import com.ai.gateway.entity.FileParseTask;
import com.ai.gateway.mapper.FileParseTaskMapper;
import com.ai.gateway.common.Result;
import com.ai.gateway.controller.FileUploadController;
import com.ai.gateway.dto.FileUploadResult;
import com.ai.gateway.dto.ParseStatusDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.messaging.rabbit-enabled=false")
class FileParseTaskRecoveryLifecycleTest {

    @Autowired private FileParseTaskMapper taskMapper;
    @Autowired private LocalFileParseTaskRecoveryService localRecoveryService;
    @Autowired private FileUploadController fileUploadController;
    @Autowired private OssService ossService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    private final List<FileParseTask> createdTasks = new ArrayList<>();

    @AfterEach
    void cleanUpLocalArtifacts() {
        for (FileParseTask task : createdTasks) {
            taskMapper.deleteById(task.getId());
            redisTemplate.delete("file:parsed:" + task.getFileId());
            ossService.deleteFile(task.getOssUrl());
        }
        createdTasks.clear();
    }

    @Test
    void localScannerRecoversExpiredParsingAndProcessesPendingTasks() throws Exception {
        assertNotNull(localRecoveryService, "RabbitMQ off must register a local persistent-task scanner");
        String recoveredContent = "D1 recovery proof " + UUID.randomUUID();
        FileParseTask crashed = insertTask("PARSING", 1, recoveredContent);
        FileParseTask pending = insertTask("PENDING", 0, "D1 pending scanner proof");

        localRecoveryService.recoverRecoverableTasks();

        FileParseTask recovered = awaitTerminal(crashed.getId());
        FileParseTask submitted = awaitTerminal(pending.getId());
        assertEquals("SUCCESS", recovered.getParseStatus(), recovered.getErrorMessage());
        assertEquals(2, recovered.getParseAttempts(), "recovery must claim a new worker attempt");
        assertEquals("SUCCESS", submitted.getParseStatus(), submitted.getErrorMessage());
        assertEquals(1, submitted.getParseAttempts());
        assertNull(recovered.getParseLeaseToken());
        assertNull(recovered.getParseLeaseUntil());
        assertEquals(recoveredContent, String.valueOf(redisTemplate.opsForValue()
                .get("file:parsed:" + crashed.getFileId())).trim());
    }

    @Test
    void expiredParsingTaskAtRetryLimitBecomesExplicitlyFailed() {
        FileParseTask exhausted = insertTask("PARSING", 5, "will not be parsed");

        localRecoveryService.recoverRecoverableTasks();

        FileParseTask failed = taskMapper.selectById(exhausted.getId());
        assertEquals("FAILED", failed.getParseStatus());
        assertTrue(failed.getErrorMessage().contains("多次异常退出"));
        assertNull(failed.getParseLeaseToken());
        assertNull(failed.getParseLeaseUntil());
    }

    @Test
    void staleWorkerTokenCannotOverwriteARecoveredAttempt() {
        FileParseTask crashed = insertTask("PARSING", 1, "fencing proof");
        LocalDateTime now = LocalDateTime.now();
        String recoveredToken = UUID.randomUUID().toString();
        assertEquals(1, taskMapper.claimForParsing(crashed.getId(), recoveredToken, now,
                now.plusMinutes(5), now.minusMinutes(5), 5));

        assertEquals(0, taskMapper.updateStatusToFailed(crashed.getId(), "crashed-worker-token",
                "late failure", LocalDateTime.now()));
        assertEquals(0, taskMapper.claimForParsing(crashed.getId(), UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().minusMinutes(5), 5));

        FileParseTask active = taskMapper.selectById(crashed.getId());
        assertEquals("PARSING", active.getParseStatus());
        assertEquals(recoveredToken, active.getParseLeaseToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadAndStatusControllerEndpointsReachLocalParser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        MockMultipartFile file = new MockMultipartFile("files", "d1-api-test.txt", "text/plain",
                "D1 controller endpoint proof".getBytes(StandardCharsets.UTF_8));

        Result<Map<String, Object>> uploadResponse = fileUploadController.uploadFiles(List.of(file), request);
        assertEquals(200, uploadResponse.getCode(), uploadResponse.getMessage());
        List<FileUploadResult> uploadedFiles = (List<FileUploadResult>) uploadResponse.getData().get("files");
        assertEquals(1, uploadedFiles.size());
        String fileId = uploadedFiles.getFirst().getFileId();
        FileParseTask uploadedTask = taskMapper.selectByFileIdAndUserId(fileId, 1L);
        assertNotNull(uploadedTask);
        createdTasks.add(uploadedTask);

        ParseStatusDTO status = null;
        long deadline = System.currentTimeMillis() + 20_000;
        do {
            Result<ParseStatusDTO> response = fileUploadController.getParseStatus(fileId, request);
            assertEquals(200, response.getCode(), response.getMessage());
            status = response.getData();
            if ("SUCCESS".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) break;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);

        assertNotNull(status);
        assertEquals("SUCCESS", status.getStatus(), status.getErrorMessage());
    }

    private FileParseTask insertTask(String status, int attempts, String content) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String ossUrl = ossService.uploadBytes(content.getBytes(StandardCharsets.UTF_8), fileId + ".txt", "d1-test/");
        LocalDateTime now = LocalDateTime.now();
        FileParseTask task = new FileParseTask();
        task.setFileId(fileId);
        task.setUserId(1L);
        task.setOriginalName(fileId + ".txt");
        task.setOssUrl(ossUrl);
        task.setFileSize((long) content.length());
        task.setFileType("txt");
        task.setParseStatus(status);
        task.setIsUsed(false);
        task.setParseAttempts(attempts);
        if ("PARSING".equals(status)) {
            task.setParseStartTime(now.minusMinutes(20));
            task.setParseLeaseUntil(now.minusMinutes(10));
            task.setParseLeaseToken("crashed-worker-token");
        }
        taskMapper.insert(task);
        createdTasks.add(task);
        return task;
    }

    private FileParseTask awaitTerminal(Long taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        FileParseTask task;
        do {
            task = taskMapper.selectById(taskId);
            if (task != null && ("SUCCESS".equals(task.getParseStatus()) || "FAILED".equals(task.getParseStatus()))) {
                return task;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return taskMapper.selectById(taskId);
    }
}
