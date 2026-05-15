package com.ai.gateway.service;

import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.ChatMessageHistory;
import com.ai.gateway.mapper.ChatMessageMapper;
import com.ai.gateway.mapper.ChatMessageHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
     * 将30天前的聊天记录从热表迁移到冷表
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void archiveOldMessages() {
        log.info("开始执行数据归档任务...");

        try {
            // 计算归档时间阈值
            LocalDateTime archiveThreshold = LocalDateTime.now().minusDays(ARCHIVE_DAYS);

            // 查询需要归档的消息
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.lt(ChatMessage::getCreateTime, archiveThreshold);

            List<ChatMessage> messagesToArchive = chatMessageMapper.selectList(wrapper);

            if (messagesToArchive.isEmpty()) {
                log.info("没有需要归档的数据");
                return;
            }

            log.info("找到 {} 条需要归档的消息", messagesToArchive.size());

            // 转换为历史消息对象
            List<ChatMessageHistory> historyMessages = messagesToArchive.stream().map(msg -> {
                ChatMessageHistory history = new ChatMessageHistory();
                history.setConversationId(msg.getConversationId());
                history.setUserId(msg.getUserId());
                history.setRole(msg.getRole());
                history.setContent(msg.getContent());
                history.setModel(msg.getModel());
                history.setTokens(msg.getTokens());
                history.setMetadata(msg.getMetadata());
                history.setOriginalCreateTime(msg.getCreateTime());
                history.setArchiveTime(LocalDateTime.now());
                return history;
            }).collect(Collectors.toList());

            // 批量插入到历史表
            for (ChatMessageHistory history : historyMessages) {
                chatMessageHistoryMapper.insert(history);
            }

            // 从热表中删除已归档的数据
            chatMessageMapper.delete(wrapper);

            log.info("数据归档完成: 归档 {} 条消息", historyMessages.size());

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
