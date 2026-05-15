package com.ai.gateway.mapper;

import com.ai.gateway.entity.ChatMessageHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息历史Mapper（冷数据）
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface ChatMessageHistoryMapper extends BaseMapper<ChatMessageHistory> {
}
