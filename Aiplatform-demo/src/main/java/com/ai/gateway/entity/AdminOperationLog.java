package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员操作日志实体
 */
@Data
@TableName("admin_operation_log")
public class AdminOperationLog implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 管理员ID
     */
    private Long adminId;
    
    /**
     * 管理员用户名
     */
    private String adminUsername;
    
    /**
     * 操作类型：USER_MANAGE/POINTS_MANAGE/MEMBERSHIP_MANAGE/BATCH_OPERATION/SYSTEM_CONFIG
     */
    private String operationType;
    
    /**
     * 操作动作：BAN/UNBAN/RECHARGE/GRANT_POINTS/ASSIGN_ROLE等
     */
    private String operationAction;
    
    /**
     * 目标用户ID列表（JSON数组）
     */
    private String targetUserIds;
    
    /**
     * 操作详情（JSON格式）
     */
    private String operationDetail;
    
    /**
     * 操作IP地址
     */
    private String ipAddress;
    
    /**
     * 浏览器标识
     */
    private String userAgent;
    
    /**
     * 操作结果：SUCCESS/FAILED
     */
    private String result;
    
    /**
     * 错误信息（失败时记录）
     */
    private String errorMessage;
    
    /**
     * 操作时间
     */
    private LocalDateTime createTime;
}
