package com.ai.gateway.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** 用户在平台内展示的昵称，不影响用户名/邮箱登录标识。 */
    private String nickname;

    /** 用户自定义头像的存储引用；未设置时回退到第三方登录头像。 */
    private String avatarUrl;

    private String password;

    private String email;

    private String phone;

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

    /** 点数余额并发版本，仅供账务恢复使用。 */
    @JsonIgnore
    private Long pointsVersion;

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
