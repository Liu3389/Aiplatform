package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体类（热数据）
 * 
 * @author AI Gateway Platform
 */
@Data
@TableName("chat_message")
public class ChatMessage {

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

    /** AI 对话请求幂等键；普通历史消息为空。 */
    private String requestId;

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
     * 关联文件URL列表（JSON数组，兼容旧数据）
     */
    private String fileUrls;

    /**
     * 关联文件ID列表（JSON数组，关联file_parse_task表）
     */
    private String fileIds;

    /**
     * 元数据（JSON格式）
     */
    private String metadata;

    /**
     * 思考耗时（秒），仅 AI 消息有值
     */
    private Double thinkingTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
