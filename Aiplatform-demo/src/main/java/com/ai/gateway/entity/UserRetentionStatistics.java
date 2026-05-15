package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户留存统计表实体
 */
@Data
@TableName("user_retention_statistics")
public class UserRetentionStatistics implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期（用户注册日期）
     */
    private LocalDate statDate;

    /**
     * 当日新增用户数
     */
    private Integer newUsers;

    /**
     * 次日留存用户数
     */
    private Integer retainedDay1;

    /**
     * 7日留存用户数
     */
    private Integer retainedDay7;

    /**
     * 30日留存用户数
     */
    private Integer retainedDay30;

    /**
     * 次日留存率
     */
    private BigDecimal retentionRateDay1;

    /**
     * 7日留存率
     */
    private BigDecimal retentionRateDay7;

    /**
     * 30日留存率
     */
    private BigDecimal retentionRateDay30;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
