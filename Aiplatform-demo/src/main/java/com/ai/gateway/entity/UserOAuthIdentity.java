package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_oauth_identity")
public class UserOAuthIdentity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String provider;
    private String providerSubject;
    private String displayName;
    private String avatarUrl;
    private String email;
    private LocalDateTime createTime;
}
