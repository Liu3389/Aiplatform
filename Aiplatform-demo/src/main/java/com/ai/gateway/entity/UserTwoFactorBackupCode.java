package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_two_factor_backup_code")
public class UserTwoFactorBackupCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String codeHash;
    private LocalDateTime consumedAt;
    private LocalDateTime createTime;
}
