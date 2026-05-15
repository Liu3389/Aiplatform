package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    private BigDecimal balance; // 后端存储三位小数，前端展示两位

    private Integer status;

    private String role;

    private String freeApiStrategy;

    private Integer freeQuota;

    private Integer dailyCallLimit;

    private Integer monthlyCallLimit;

    private String membership;

    /**
     * 点数余额（保留三位小数）
     */
    private BigDecimal points;

    /**
     * 每日免费次数
     */
    private Integer dailyFreeCount;

    /**
     * 会员过期时间
     */
    private LocalDateTime membershipExpireTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
