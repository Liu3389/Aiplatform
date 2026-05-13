package com.ai.gateway.common;

/**
 * 系统常量定义
 * 
 * @author AI Gateway Platform
 */
public class Constants {

    /**
     * Redis Key前缀 - API Key信息
     */
    public static final String REDIS_API_KEY_PREFIX = "ai_gateway:api_key:";

    /**
     * Redis Key前缀 - 限流计数器
     */
    public static final String REDIS_RATE_LIMIT_PREFIX = "ai_gateway:rate_limit:";

    /**
     * Redis Key前缀 - 用户余额
     */
    public static final String REDIS_USER_BALANCE_PREFIX = "ai_gateway:user_balance:";

    /**
     * Redis Key前缀 - 计费预扣
     */
    public static final String REDIS_BILLING_PRE_DEDUCT_PREFIX = "ai_gateway:billing_pre_deduct:";

    /**
     * HTTP Header - API Key
     */
    public static final String HEADER_API_KEY = "X-API-Key";

    /**
     * HTTP Header - Authorization
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * 默认限流阈值（每分钟请求数）
     */
    public static final Integer DEFAULT_RATE_LIMIT = 100;

    /**
     * 限流时间窗口（秒）
     */
    public static final Long RATE_LIMIT_WINDOW_SECONDS = 60L;

    /**
     * Token价格计算基数（每千Token）
     */
    public static final Integer TOKEN_PRICE_BASE = 1000;

    /**
     * 计费类型 - 扣费
     */
    public static final Integer BILLING_TYPE_DEDUCT = 1;

    /**
     * 计费类型 - 充值
     */
    public static final Integer BILLING_TYPE_RECHARGE = 2;

    /**
     * 调用状态 - 成功
     */
    public static final Integer CALL_STATUS_SUCCESS = 1;

    /**
     * 调用状态 - 失败
     */
    public static final Integer CALL_STATUS_FAILED = 0;

    /**
     * 用户状态 - 启用
     */
    public static final Integer USER_STATUS_ENABLED = 1;

    /**
     * 用户状态 - 禁用
     */
    public static final Integer USER_STATUS_DISABLED = 0;

    /**
     * API Key状态 - 启用
     */
    public static final Integer API_KEY_STATUS_ENABLED = 1;

    /**
     * API Key状态 - 禁用
     */
    public static final Integer API_KEY_STATUS_DISABLED = 0;

    /**
     * 模型状态 - 启用
     */
    public static final Integer MODEL_STATUS_ENABLED = 1;

    /**
     * 模型状态 - 禁用
     */
    public static final Integer MODEL_STATUS_DISABLED = 0;
}
