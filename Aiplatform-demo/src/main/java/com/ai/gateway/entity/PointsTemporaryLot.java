package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Remaining balance of one expiring points grant. */
@Data
@TableName("points_temporary_lot")
public class PointsTemporaryLot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String businessId;
    private BigDecimal grantedPoints;
    private BigDecimal remainingPoints;
    private LocalDateTime expireTime;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime expiredAt;
}
