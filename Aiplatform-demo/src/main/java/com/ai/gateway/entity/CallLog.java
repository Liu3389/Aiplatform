package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调用日志实体类
 * 
 * @author AI Gateway Platform
 */
@Data
@TableName("call_log")
public class CallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 使用的API Key
     */
    private String apiKey;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * 输入Token数量
     */
    private Integer inputTokens;

    /**
     * 输出Token数量
     */
    private Integer outputTokens;

    /**
     * 本次调用费用（元，修复 P0-8: 统一为 3 位小数）
     */
    private BigDecimal cost;

    /**
     * 调用时长（毫秒）
     */
    private Integer duration;

    /**
     * 状态：0-失败，1-成功
     */
    private Integer status;

    /**
     * 错误信息（失败时记录）
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
