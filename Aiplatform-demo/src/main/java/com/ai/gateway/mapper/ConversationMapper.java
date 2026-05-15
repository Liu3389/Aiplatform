package com.ai.gateway.mapper;

import com.ai.gateway.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话Mapper
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
