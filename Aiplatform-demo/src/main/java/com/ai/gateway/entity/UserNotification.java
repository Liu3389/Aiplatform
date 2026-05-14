package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户通知实体
 */
@Data
@TableName("user_notification")
public class UserNotification implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 通知标题
     */
    private String title;
    
    /**
     * 通知内容
     */
    private String content;
    
    /**
     * 通知类型：RECHARGE-充值到账, POINTS_GRANT-点数发放, MEMBERSHIP_EXPIRE-会员到期, ANNOUNCEMENT-系统公告, SYSTEM-系统通知
     */
    private String type;
    
    /**
     * 关联ID（如订单ID、公告ID等）
     */
    private Long relatedId;
    
    /**
     * 是否已读：0-未读, 1-已读
     */
    private Integer isRead;
    
    /**
     * 阅读时间
     */
    private LocalDateTime readTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
