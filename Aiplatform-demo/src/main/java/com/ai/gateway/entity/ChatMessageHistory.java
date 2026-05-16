package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息历史实体类（冷数据）
 * 
 * @author AI Gateway Platform
 */
@Data
@TableName("chat_message_history")
public class ChatMessageHistory {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色：user/assistant/system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * Token数量
     */
    private Integer tokens;

    /**
     * 关联文件URL列表（JSON数组）
     */
    private String fileUrls;

    /**
     * 元数据（JSON格式）
     */
    private String metadata;

    /**
     * 原始创建时间
     */
    private LocalDateTime originalCreateTime;

    /**
     * 归档时间
     */
    private LocalDateTime archiveTime;
}
