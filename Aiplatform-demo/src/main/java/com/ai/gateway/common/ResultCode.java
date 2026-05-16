package com.ai.gateway.common;

import lombok.Getter;

/**
 * 响应码枚举
 * 
 * @author AI Gateway Platform
 */
@Getter
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 失败
     */
    ERROR(500, "操作失败"),

    /**
     * 参数错误
     */
    PARAM_ERROR(400, "参数错误"),

    /**
     * 未授权
     */
    UNAUTHORIZED(401, "未授权"),

    /**
     * 禁止访问
     */
    FORBIDDEN(403, "禁止访问"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 请求过于频繁
     */
    RATE_LIMIT_EXCEEDED(429, "请求过于频繁，请稍后重试"),

    /**
     * 余额不足
     */
    INSUFFICIENT_BALANCE(402, "余额不足"),

    /**
     * API Key无效
     */
    INVALID_API_KEY(401, "API Key无效或已过期"),

    /**
     * API Key已禁用
     */
    API_KEY_DISABLED(403, "API Key已被禁用"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(404, "用户不存在"),

    /**
     * 用户名或密码错误
     */
    LOGIN_ERROR(401, "用户名或密码错误"),

    /**
     * 用户已被禁用
     */
    USER_DISABLED(403, "用户已被禁用"),

    /**
     * 模型不存在
     */
    MODEL_NOT_FOUND(404, "模型不存在"),

    /**
     * 模型已禁用
     */
    MODEL_DISABLED(403, "模型已禁用"),

    /**
     * 点数不足
     */
    INSUFFICIENT_POINTS(40001, "点数不足"),

    /**
     * 优惠券不存在
     */
    COUPON_NOT_FOUND(40002, "优惠券不存在"),

    /**
     * 优惠券已使用
     */
    COUPON_ALREADY_USED(40003, "优惠券已使用"),

    /**
     * 优惠券已过期
     */
    COUPON_EXPIRED(40004, "优惠券已过期"),

    /**
     * 套餐不存在
     */
    PACKAGE_NOT_FOUND(40005, "套餐不存在"),

    /**
     * 套餐已下架
     */
    PACKAGE_OFFLINE(40006, "套餐已下架"),

    /**
     * 活跃订阅存在
     */
    ACTIVE_SUBSCRIPTION_EXISTS(40007, "存在活跃订阅，无法操作"),

    /**
     * 重复操作
     */
    DUPLICATE_OPERATION(40008, "请勿重复操作"),

    /**
     * 权限不足
     */
    PERMISSION_DENIED(40009, "权限不足");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
