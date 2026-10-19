package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_two_factor")
public class UserTwoFactor {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private String secretCiphertext;
    private String pendingSecretCiphertext;
    private Boolean enabled;
    private Long lastTotpStep;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
