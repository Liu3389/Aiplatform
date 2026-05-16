package com.ai.gateway.service;

import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.ChatMessageHistory;
import com.ai.gateway.mapper.ChatMessageMapper;
import com.ai.gateway.mapper.ChatMessageHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据归档服务 - 定时将热数据迁移到冷数据表
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataArchiveService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageHistoryMapper chatMessageHistoryMapper;

    // 冷热数据分界线（30天）
    private static final int ARCHIVE_DAYS = 30;

    /**
     * 每天凌晨2点执行归档任务
     * 修复 P2-17: 采用分批处理机制，防止表过大时 OOM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void archiveOldMessages() {
        log.info("开始执行数据归档任务...");

        try {
            LocalDateTime archiveThreshold = LocalDateTime.now().minusDays(ARCHIVE_DAYS);
            int batchSize = 1000;
            int totalArchived = 0;

            while (true) {
                // 分批查询需要归档的消息
                LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
                wrapper.lt(ChatMessage::getCreateTime, archiveThreshold);

                Page<ChatMessage> page = new Page<>(1, batchSize);
                List<ChatMessage> messagesToArchive = chatMessageMapper.selectList(page, wrapper);

                if (messagesToArchive.isEmpty()) {
                    break;
                }

                log.info("正在归档一批数据: size={}", messagesToArchive.size());

                // 转换并批量插入历史表
                List<ChatMessageHistory> historyMessages = messagesToArchive.stream().map(msg -> {
                    ChatMessageHistory history = new ChatMessageHistory();
                    history.setConversationId(msg.getConversationId());
                    history.setUserId(msg.getUserId());
                    history.setRole(msg.getRole());
                    history.setContent(msg.getContent());
                    history.setModel(msg.getModel());
                    history.setTokens(msg.getTokens());
                    history.setFileUrls(msg.getFileUrls());
                    history.setMetadata(msg.getMetadata());
                    history.setOriginalCreateTime(msg.getCreateTime());
                    history.setArchiveTime(LocalDateTime.now());
                    return history;
                }).collect(Collectors.toList());

                if (!historyMessages.isEmpty()) {
                    for (int i = 0; i < historyMessages.size(); i += 500) {
                        int end = Math.min(i + 500, historyMessages.size());
                        List<ChatMessageHistory> batch = historyMessages.subList(i, end);
                        chatMessageHistoryMapper.insertBatch(batch);
                    }
                }

                // 删除已归档的热数据
                List<Long> ids = messagesToArchive.stream().map(ChatMessage::getId).collect(Collectors.toList());
                chatMessageMapper.deleteBatchIds(ids);

                totalArchived += messagesToArchive.size();
            }

            log.info("数据归档完成: 共归档 {} 条消息", totalArchived);

        } catch (Exception e) {
            log.error("数据归档失败", e);
            throw e;
        }
    }

    /**
     * 手动触发归档（用于测试）
     */
    public void manualArchive() {
        log.info("手动触发数据归档");
        archiveOldMessages();
    }
}
