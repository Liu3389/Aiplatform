package com.ai.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Contacts in this table have been verified through the configured delivery channel. */
@Data
@TableName("user_contact_verification")
public class UserContactVerification implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private String email;

    private String phone;

    private LocalDateTime updatedAt;
}
