package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 内容安全统计表实体
 */
@Data
@TableName("content_safety_statistics")
public class ContentSafetyStatistics implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 敏感词触发次数
     */
    private Integer sensitiveWordTrigger;

    /**
     * 违规举报数
     */
    private Integer violationReport;

    /**
     * 已处理违规数
     */
    private Integer violationHandled;

    /**
     * 被拦截消息数
     */
    private Integer blockedMessages;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
