package com.ai.gateway.mapper;

import com.ai.gateway.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息Mapper（热数据）
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
