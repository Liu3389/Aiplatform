package com.ai.gateway.mapper;

import com.ai.gateway.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 会话Mapper
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Update("UPDATE conversation SET message_count = COALESCE(message_count, 0) + #{messageCount}, "
            + "last_message_time = #{now}, update_time = #{now} "
            + "WHERE id = #{conversationId} AND user_id = #{userId} AND is_deleted = 0")
    int incrementConversationMessageCount(@Param("conversationId") Long conversationId,
                                          @Param("userId") Long userId,
                                          @Param("messageCount") int messageCount,
                                          @Param("now") java.time.LocalDateTime now);
}
