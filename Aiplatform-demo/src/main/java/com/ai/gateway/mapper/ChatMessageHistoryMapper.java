package com.ai.gateway.mapper;

import com.ai.gateway.entity.ChatMessageHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 聊天消息历史Mapper（冷数据）
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface ChatMessageHistoryMapper extends BaseMapper<ChatMessageHistory> {

    @Select("SELECT * FROM chat_message_history WHERE request_id = #{requestId} AND role = #{role} LIMIT 1")
    ChatMessageHistory selectByRequestIdAndRole(@Param("requestId") String requestId, @Param("role") String role);

    /**
     * 批量插入历史消息（原生SQL批量插入，性能优于逐条insert）
     */
    @Insert("<script>" +
            "INSERT INTO chat_message_history (conversation_id, user_id, request_id, role, content, model, tokens, file_urls, file_ids, metadata, thinking_time, original_create_time, archive_time) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.conversationId}, #{item.userId}, #{item.requestId}, #{item.role}, #{item.content}, #{item.model}, #{item.tokens}, #{item.fileUrls}, #{item.fileIds}, #{item.metadata}, #{item.thinkingTime}, #{item.originalCreateTime}, #{item.archiveTime})" +
            "</foreach>" +
            "</script>")
    void insertBatch(List<ChatMessageHistory> list);
}
