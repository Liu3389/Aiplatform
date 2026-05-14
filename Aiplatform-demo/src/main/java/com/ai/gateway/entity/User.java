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

    private BigDecimal balance;

    private Integer status;

    private String role;

    private String freeApiStrategy;

    private Integer freeQuota;

    private Integer dailyCallLimit;

    private Integer monthlyCallLimit;

    private String membership;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
