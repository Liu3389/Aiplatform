-- AI模型调度网关数据库建表语句
-- 数据库名: ai_gateway

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password`           varchar(128) NOT NULL COMMENT '密码（BCrypt加密存储）',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `balance` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '账户余额（美元）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `role`               varchar(32)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN/SUPER_ADMIN',
  `free_api_strategy`  varchar(32)           DEFAULT 'NONE' COMMENT '免费策略：NONE/UNLIMITED/QUOTA_BASED/COUNT_LIMITED/TIME_LIMITED/MODEL_SPECIFIC',
  `free_quota`         int                   DEFAULT 0 COMMENT '免费配额',
  `daily_call_limit`   int                   DEFAULT 0 COMMENT '每日调用限制',
  `monthly_call_limit` int                   DEFAULT 0 COMMENT '每月调用限制',
  `membership`         varchar(32)           DEFAULT NULL COMMENT '会员类型',
  `points`             decimal(10,3) NOT NULL DEFAULT '100.000' COMMENT '点数余额（保留三位小数）',
  `daily_free_count`   int                   DEFAULT 0 COMMENT '每日免费次数',
  `membership_expire_time` datetime          DEFAULT NULL COMMENT '会员过期时间',
  `last_login_time`    datetime              DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- API Key表
CREATE TABLE IF NOT EXISTS `api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_key` varchar(256) NOT NULL COMMENT 'API Key（AES加密存储）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `name` varchar(64) DEFAULT NULL COMMENT 'API Key名称/备注',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `rate_limit` int NOT NULL DEFAULT '100' COMMENT '每分钟请求限制次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间（NULL表示永久有效）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Key表';

-- 模型配置表
CREATE TABLE IF NOT EXISTS `model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` varchar(64) NOT NULL COMMENT '模型名称',
  `provider`   varchar(32) NOT NULL COMMENT '厂商',
  `base_url` varchar(256) NOT NULL COMMENT 'API基础地址',
  `api_key` varchar(128) NOT NULL COMMENT '厂商API Key',
  `input_price` decimal(10,6) NOT NULL COMMENT '每千输入Token价格（美元）',
  `output_price` decimal(10,6) NOT NULL COMMENT '每千输出Token价格（美元）',
  `points_mode` varchar(20) DEFAULT 'STANDARD' COMMENT '点数消耗模式：FREE-免费模型, STANDARD-中端模型, PREMIUM-高端模型',
  `points_per_request` decimal(10,3) DEFAULT NULL COMMENT '每次对话消耗的点数（保留三位小数精度，优先使用此值）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_name` (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

-- 调用日志表
CREATE TABLE IF NOT EXISTS `call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_key` varchar(64) NOT NULL COMMENT '使用的API Key',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model` varchar(64) NOT NULL COMMENT '使用的模型名称',
  `input_tokens` int NOT NULL DEFAULT '0' COMMENT '输入Token数量',
  `output_tokens` int NOT NULL DEFAULT '0' COMMENT '输出Token数量',
  `cost` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '本次调用费用（美元）',
  `duration` int NOT NULL DEFAULT '0' COMMENT '调用时长（毫秒）',
  `status` tinyint NOT NULL COMMENT '状态：0-失败，1-成功',
  `error_message` text COMMENT '错误信息（失败时记录）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_api_key` (`api_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调用日志表';

-- 计费记录表
CREATE TABLE IF NOT EXISTS `billing_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `call_log_id` bigint NOT NULL COMMENT '关联的调用日志ID',
  `amount` decimal(10,6) NOT NULL COMMENT '金额（美元）',
  `type` tinyint NOT NULL COMMENT '类型：1-扣费，2-充值',
  `balance_before` decimal(10,4) NOT NULL COMMENT '操作前余额（美元）',
  `balance_after` decimal(10,4) NOT NULL COMMENT '操作后余额（美元）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_call_log_id` (`call_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费记录表';

-- ============================================
-- 点数明细账单表（统一点数体系）
-- ============================================
CREATE TABLE IF NOT EXISTS `points_bill` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `change_type` VARCHAR(20) NOT NULL COMMENT '变动类型：RECHARGE-充值, DEDUCT-扣除, GRANT-发放, EXPIRE-过期清零, REFUND-退款',
  `points_change` DECIMAL(10,3) NOT NULL COMMENT '变动点数（正数为增加，负数为减少，保留三位小数）',
  `balance_before` DECIMAL(10,3) NOT NULL COMMENT '变动前点数余额',
  `balance_after` DECIMAL(10,3) NOT NULL COMMENT '变动后点数余额',
  `business_id` VARCHAR(100) DEFAULT NULL COMMENT '关联业务ID（如对话ID、充值订单ID等）',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
  `model_name` VARCHAR(100) DEFAULT NULL COMMENT '模型名称（如果是对话消耗）',
  `expire_time` DATETIME DEFAULT NULL COMMENT '有效期（过期清零时使用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_business_id` (`business_id`),
  KEY `idx_user_time` (`user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点数明细账单表';

-- ============================================
-- 模型配置：DeepSeek 模型
-- 点数标准: FREE=0点/次, STANDARD=1点/次, PREMIUM=3点/次
-- 注意：deepseek-chat 和 deepseek-reasoner 将于 2026/07/24 弃用
-- ============================================
-- 先清空现有数据，避免重复
DELETE FROM `model_config`;

-- 插入模型配置（统一使用 DeepSeek API）
-- 注意：请在 application.yml 或环境变量中配置 DEEPSEEK_API_KEY
INSERT INTO `model_config` (`model_name`, `provider`, `base_url`, `api_key`, `input_price`, `output_price`, `points_mode`, `points_per_request`, `status`) VALUES
('deepseek-v4-flash', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 0.000000, 0.000000, 'FREE', 0.000, 1),
('deepseek-v4-pro',   'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 0.000000, 0.000000, 'PREMIUM', 3.000, 1);

-- ============================================
-- 会话管理表（支持游标分页和冷热分离）
-- ============================================

-- 会话表
CREATE TABLE IF NOT EXISTS `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(255) DEFAULT NULL COMMENT '会话标题',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `message_count` int NOT NULL DEFAULT '0' COMMENT '消息数量',
  `last_message_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一条消息时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除：0-否，1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_deleted_time` (`user_id`, `is_deleted`, `last_message_time`) COMMENT '用户会话列表查询索引',
  KEY `idx_last_message_time` (`last_message_time`) COMMENT '按时间排序索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- 聊天消息表（热数据 - 最近30天）
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `metadata` json DEFAULT NULL COMMENT '元数据（JSON格式）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_create` (`conversation_id`, `create_time`) COMMENT '游标分页查询索引（核心）',
  KEY `idx_user_conversation` (`user_id`, `conversation_id`) COMMENT '权限校验索引',
  KEY `idx_create_time` (`create_time`) COMMENT '时间范围查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表（热数据）';

-- 聊天消息历史表（冷数据 - 30天前）
CREATE TABLE IF NOT EXISTS `chat_message_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `metadata` json DEFAULT NULL COMMENT '元数据（JSON格式）',
  `original_create_time` datetime NOT NULL COMMENT '原始创建时间',
  `archive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_original_time` (`conversation_id`, `original_create_time`) COMMENT '游标分页查询索引（核心）',
  KEY `idx_user_conversation` (`user_id`, `conversation_id`) COMMENT '权限校验索引',
  KEY `idx_original_create_time` (`original_create_time`) COMMENT '时间范围查询索引',
  KEY `idx_archive_time` (`archive_time`) COMMENT '归档任务查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息历史表（冷数据）';

-- ============================================
-- 优惠券使用记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `coupon_usage_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `coupon_id` BIGINT NOT NULL COMMENT '优惠券ID',
  `coupon_code` VARCHAR(32) NOT NULL COMMENT '优惠券码',
  `coupon_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '优惠券面额',
  `order_type` VARCHAR(32) NOT NULL COMMENT '订单类型：RECHARGE-充值, SUBSCRIPTION-订阅, PACKAGE-套餐',
  `order_id` VARCHAR(100) DEFAULT NULL COMMENT '关联订单ID（如充值订单号、订阅ID等）',
  `order_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '订单原始金额',
  `discount_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '优惠金额',
  `actual_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '实际支付金额',
  `usage_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_coupon_id` (`coupon_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_usage_time` (`usage_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券使用记录表';

-- ============================================
-- 套餐模板表和用户订阅表
-- ============================================

-- 套餐模板表（管理员配置）
CREATE TABLE IF NOT EXISTS `package_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_code` VARCHAR(32) NOT NULL COMMENT '套餐代码（唯一标识）',
  `package_name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '套餐描述',
  `identity_label` VARCHAR(32) DEFAULT NULL COMMENT '身份标识（如：至尊、尊享、畅享）',
  `points` DECIMAL(10,3) NOT NULL DEFAULT 0 COMMENT '包含点数',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价格（元）',
  `duration_days` INT DEFAULT NULL COMMENT '有效期天数（NULL表示永久）',
  `daily_call_limit` INT DEFAULT 0 COMMENT '每日调用限制（0表示无限制）',
  `monthly_call_limit` INT DEFAULT 0 COMMENT '每月调用限制（0表示无限制）',
  `max_tokens_per_call` INT DEFAULT 0 COMMENT '单次最大Token数（0表示无限制）',
  `priority_level` INT DEFAULT 0 COMMENT '优先级等级（数字越大优先级越高）',
  `features` TEXT DEFAULT NULL COMMENT '特性说明（JSON格式）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_code` (`package_code`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐模板表';

-- 用户订阅记录表
CREATE TABLE IF NOT EXISTS `user_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `package_id` BIGINT NOT NULL COMMENT '套餐模板ID',
  `package_code` VARCHAR(32) NOT NULL COMMENT '套餐代码',
  `package_name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `identity_label` VARCHAR(32) DEFAULT NULL COMMENT '身份标识',
  `points_granted` DECIMAL(10,3) NOT NULL DEFAULT 0 COMMENT '授予点数',
  `price_paid` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实际支付价格',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间（NULL表示永久）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效中，EXPIRED-已过期，CANCELLED-已取消',
  `auto_renew` TINYINT(1) DEFAULT 0 COMMENT '是否自动续费',
  `order_id` VARCHAR(100) DEFAULT NULL COMMENT '关联订单ID',
  `coupon_id` BIGINT DEFAULT NULL COMMENT '使用的优惠券ID',
  `discount_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expire_time` (`expire_time`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅记录表';

-- ============================================
-- 用户自定义 API Key 表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_api_key` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT 'API提供商（如：deepseek/openai/anthropic）',
  `display_name` VARCHAR(64) DEFAULT NULL COMMENT '显示名称/备注',
  `api_key` VARCHAR(256) NOT NULL COMMENT 'API Key（AES加密存储）',
  `base_url` VARCHAR(256) DEFAULT NULL COMMENT '自定义API基础地址',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户自定义API Key表';
