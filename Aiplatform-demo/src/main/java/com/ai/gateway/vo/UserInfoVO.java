package com.ai.gateway.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户信息")
public class UserInfoVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "邮箱是否已完成验证码验证")
    private boolean emailVerified;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "手机号是否已完成验证码验证")
    private boolean phoneVerified;

    @Schema(description = "账户余额")
    private BigDecimal balance;

    @Schema(description = "用户状态：1启用 0禁用")
    private Integer status;

    @Schema(description = "角色：USER / ADMIN / SUPER_ADMIN")
    private String role;

    @Schema(description = "免费API策略")
    private String freeApiStrategy;

    @Schema(description = "免费额度")
    private Integer freeQuota;

    @Schema(description = "每日调用上限")
    private Integer dailyCallLimit;

    @Schema(description = "每月调用上限")
    private Integer monthlyCallLimit;

    @Schema(description = "会员等级：free / silver / gold / diamond")
    private String membership;

    /**
     * 会员过期时间
     */
    @Schema(description = "会员过期时间")
    private LocalDateTime membershipExpireTime;

    /**
     * 点数余额（保留三位小数）
     */
    @Schema(description = "点数余额（保留三位小数）")
    private BigDecimal points;

    @Schema(description = "用户点数")
    private Integer userPoints;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
