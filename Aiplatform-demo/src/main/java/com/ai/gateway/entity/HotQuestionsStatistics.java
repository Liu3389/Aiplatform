package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 热门问题统计表实体
 */
@Data
@TableName("hot_questions_statistics")
public class HotQuestionsStatistics implements Serializable {

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
     * 问题哈希值（去重）
     */
    private String questionHash;

    /**
     * 问题预览（前100字符）
     */
    private String questionPreview;

    /**
     * 出现次数
     */
    private Integer occurrenceCount;

    /**
     * 提问的唯一用户数
     */
    private Integer uniqueUsers;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
