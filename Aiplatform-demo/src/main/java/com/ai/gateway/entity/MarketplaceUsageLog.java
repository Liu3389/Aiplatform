package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("marketplace_usage_log")
public class MarketplaceUsageLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userKeyId; // 关联用户 Key ID
    private Long userId; // 用户 ID
    private String model; // 使用的模型
    private Integer inputTokens; // 输入 Token
    private Integer outputTokens; // 输出 Token
    private BigDecimal cost; // 本次费用（元）
    private Integer duration; // 耗时（毫秒）
    private LocalDateTime createTime;
}
