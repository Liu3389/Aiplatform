package com.ai.gateway.mq;

import com.ai.gateway.service.FileParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 消费端只携带数据库任务 ID；文件元数据仍以持久化任务表为准。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "rabbit-enabled", havingValue = "true")
public class FileParseMessageListener {

    private final FileParseService fileParseService;

    @RabbitListener(queues = "${app.messaging.file-parse-queue:ai.file.parse}")
    public void consume(String taskIdMessage) {
        Long taskId = Long.valueOf(taskIdMessage);
        log.info("收到文件解析任务: taskId={}", taskId);
        fileParseService.processFileTask(taskId);
    }
}
