-- ============================================
-- AI Gateway Platform - 一键重建数据库脚本
-- 版本: v1.0 (2026-05-16)
-- 说明: 整合建表与测试数据，可重复执行（幂等）
-- 用法: mysql -h HOST -u USER -p < rebuild.sql
-- 安全: API Key 使用占位符，部署时通过环境变量注入
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 第一部分：清理旧数据（按依赖关系逆序删除）
-- ============================================

DELETE FROM `user_notification`;
DELETE FROM `system_notification`;
DELETE FROM `admin_operation_log`;
DELETE FROM `billing_record`;
DELETE FROM `call_log`;
DELETE FROM `points_bill`;
DELETE FROM `chat_message`;
DELETE FROM `chat_message_history`;
DELETE FROM `conversation`;
DELETE FROM `api_key`;
DELETE FROM `coupon_usage_record`;
DELETE FROM `coupon`;
DELETE FROM `user_subscription`;
DELETE FROM `user`;
DELETE FROM `user_marketplace_key`;
DELETE FROM `marketplace_usage_log`;
DELETE FROM `marketplace_provider`;
DELETE FROM `platform_model_config`;
DELETE FROM `package_template`;
DELETE FROM `daily_statistics`;
DELETE FROM `statistics_counter`;
DELETE FROM `platform_statistics`;

-- ============================================
-- 第二部分：核心建表
-- ============================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(128) NOT NULL COMMENT '密码（BCrypt加密存储）',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `balance` decimal(10,3) NOT NULL DEFAULT '0.000' COMMENT '账户余额（元）',
  `points` decimal(10,3) NOT NULL DEFAULT '100.000' COMMENT '点数余额',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `role` varchar(32) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN/SUPER_ADMIN',
  `membership` varchar(32) DEFAULT NULL COMMENT '会员类型标识',
  `membership_expire_time` datetime DEFAULT NULL COMMENT '会员过期时间',
  `free_api_strategy` varchar(32) DEFAULT 'NONE' COMMENT '免费策略',
  `free_quota` int DEFAULT 0 COMMENT '免费配额总量',
  `daily_call_limit` int DEFAULT 0 COMMENT '每日调用限制',
  `monthly_call_limit` int DEFAULT 0 COMMENT '每月调用限制',
  `daily_free_count` int DEFAULT 0 COMMENT '每日免费次数',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`),
  KEY `idx_membership` (`membership`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. API Key 表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台统一API Key表';

-- 3. 平台对话模型配置表
CREATE TABLE IF NOT EXISTS `platform_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `display_name` varchar(64) NOT NULL COMMENT '展示名称（如 GPT-4o）',
  `actual_model` varchar(64) NOT NULL COMMENT '实际调用模型（如 deepseek-v4-pro）',
  `fixed_points` decimal(10,3) DEFAULT '3.000' COMMENT '每次对话固定消耗点数',
  `provider` varchar(32) DEFAULT 'deepseek' COMMENT '关联厂商',
  `base_url` varchar(256) DEFAULT NULL COMMENT '厂商 API 地址',
  `api_key` varchar(128) DEFAULT NULL COMMENT '平台持有的 API Key',
  `sort_order` int DEFAULT 0 COMMENT '前端展示排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台对话模型配置表';

-- 4. 模型商店厂商配置表
CREATE TABLE IF NOT EXISTS `marketplace_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_name` varchar(32) NOT NULL COMMENT '厂商名称',
  `base_url` varchar(256) NOT NULL COMMENT 'API基础地址',
  `platform_api_key` varchar(128) NOT NULL COMMENT '平台持有的厂商 Key',
  `input_price` decimal(10,6) NOT NULL COMMENT '每千输入Token价格（元）',
  `output_price` decimal(10,6) NOT NULL COMMENT '每千输出Token价格（元）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-下架，1-上架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_name` (`provider_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型商店厂商配置表';

-- 5. 用户模型商店 Key 记录表
CREATE TABLE IF NOT EXISTS `user_marketplace_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `provider_id` bigint NOT NULL COMMENT '关联厂商ID',
  `api_key` varchar(128) NOT NULL COMMENT '分发给用户的 API Key',
  `key_name` varchar(64) DEFAULT NULL COMMENT 'Key 备注名',
  `total_requests` bigint DEFAULT '0' COMMENT '累计请求次数',
  `total_input_tokens` bigint DEFAULT '0' COMMENT '累计输入 Token',
  `total_output_tokens` bigint DEFAULT '0' COMMENT '累计输出 Token',
  `total_cost` decimal(10,3) DEFAULT '0.000' COMMENT '累计花费金额（元）',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/FROZEN/EXPIRED',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_api_key` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户模型商店 Key 记录表';

-- 6. 模型商店调用日志表
CREATE TABLE IF NOT EXISTS `marketplace_usage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_key_id` bigint NOT NULL COMMENT '关联用户 Key ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model` varchar(64) NOT NULL COMMENT '使用的模型',
  `input_tokens` int NOT NULL DEFAULT '0' COMMENT '输入 Token',
  `output_tokens` int NOT NULL DEFAULT '0' COMMENT '输出 Token',
  `cost` decimal(10,3) NOT NULL DEFAULT '0.000' COMMENT '本次费用（元）',
  `duration` int NOT NULL DEFAULT '0' COMMENT '耗时（毫秒）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_key_id` (`user_key_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型商店调用日志表';

-- 7. 套餐模板表
CREATE TABLE IF NOT EXISTS `package_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_code` VARCHAR(32) NOT NULL COMMENT '套餐代码（唯一标识）',
  `package_name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '套餐描述',
  `identity_label` VARCHAR(32) DEFAULT NULL COMMENT '身份标识',
  `points` DECIMAL(10,3) NOT NULL DEFAULT 0 COMMENT '包含点数',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价格（元）',
  `duration_days` INT DEFAULT NULL COMMENT '有效期天数（NULL表示永久）',
  `daily_call_limit` INT DEFAULT 0 COMMENT '每日调用限制',
  `monthly_call_limit` INT DEFAULT 0 COMMENT '每月调用限制',
  `max_tokens_per_call` INT DEFAULT 0 COMMENT '单次最大Token数',
  `priority_level` INT DEFAULT 0 COMMENT '优先级等级',
  `features` TEXT DEFAULT NULL COMMENT '特性说明（JSON格式）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_code` (`package_code`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐模板表';

-- 8. 会话表
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
  KEY `idx_user_deleted_time` (`user_id`, `is_deleted`, `last_message_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- 9. 聊天消息表（热数据）
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `file_urls` json DEFAULT NULL COMMENT '关联文件URL列表',
  `metadata` json DEFAULT NULL COMMENT '元数据',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_create` (`conversation_id`, `create_time`),
  KEY `idx_user_conversation` (`user_id`, `conversation_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表（热数据-最近30天）';

-- 10. 聊天消息历史表（冷数据）
CREATE TABLE IF NOT EXISTS `chat_message_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `file_urls` json DEFAULT NULL COMMENT '关联文件URL列表',
  `metadata` json DEFAULT NULL COMMENT '元数据',
  `original_create_time` datetime NOT NULL COMMENT '原始创建时间',
  `archive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_original_time` (`conversation_id`, `original_create_time`),
  KEY `idx_user_conversation` (`user_id`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息历史表（冷数据-30天前）';

-- 11. 调用日志表
CREATE TABLE IF NOT EXISTS `call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_key` varchar(64) NOT NULL COMMENT '使用的API Key',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model` varchar(64) NOT NULL COMMENT '使用的模型名称',
  `input_tokens` int NOT NULL DEFAULT '0' COMMENT '输入Token数量',
  `output_tokens` int NOT NULL DEFAULT '0' COMMENT '输出Token数量',
  `cost` decimal(10,3) NOT NULL DEFAULT '0.000' COMMENT '本次调用费用（元）',
  `duration` int NOT NULL DEFAULT '0' COMMENT '调用时长（毫秒）',
  `status` tinyint NOT NULL COMMENT '状态：0-失败，1-成功',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_model` (`user_id`, `model`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_status` (`status`),
  KEY `idx_user_status_time` (`user_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调用日志表';

-- 12. 计费记录表
CREATE TABLE IF NOT EXISTS `billing_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `call_log_id` bigint NOT NULL COMMENT '关联的调用日志ID',
  `amount` decimal(10,3) NOT NULL COMMENT '金额（元）',
  `type` tinyint NOT NULL COMMENT '类型：1-扣费，2-充值',
  `balance_before` decimal(10,3) NOT NULL COMMENT '操作前余额',
  `balance_after` decimal(10,3) NOT NULL COMMENT '操作后余额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_call_log_id` (`call_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费记录表';

-- 13. 点数明细账单表
CREATE TABLE IF NOT EXISTS `points_bill` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `change_type` VARCHAR(20) NOT NULL COMMENT '变动类型：RECHARGE/DEDUCT/GRANT/EXPIRE',
  `points_change` DECIMAL(10,3) NOT NULL COMMENT '变动点数（正增负减）',
  `balance_before` DECIMAL(10,3) NOT NULL COMMENT '变动前点数余额',
  `balance_after` DECIMAL(10,3) NOT NULL COMMENT '变动后点数余额',
  `business_id` VARCHAR(100) DEFAULT NULL COMMENT '关联业务ID',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
  `model_name` VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
  `expire_time` DATETIME DEFAULT NULL COMMENT '点数过期时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_business_id` (`business_id`),
  KEY `idx_user_time` (`user_id`, `create_time` DESC),
  KEY `idx_change_type` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点数明细账单表';

-- 14. 用户订阅记录表
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
  `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED/CANCELLED',
  `auto_renew` TINYINT(1) DEFAULT 0 COMMENT '是否自动续费',
  `order_id` VARCHAR(100) DEFAULT NULL COMMENT '关联订单ID',
  `coupon_id` BIGINT DEFAULT NULL COMMENT '使用的优惠券ID',
  `discount_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_expire_time` (`expire_time`),
  KEY `idx_package_code_status` (`package_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅记录表';

-- 15. 代金券表
CREATE TABLE IF NOT EXISTS `coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `coupon_code` varchar(32) NOT NULL,
  `amount` decimal(10,3) NOT NULL DEFAULT 0 COMMENT '代金券面额(元)',
  `threshold_amount` decimal(10,3) NOT NULL DEFAULT 0 COMMENT '使用门槛(元)',
  `applicable_plan` varchar(32) DEFAULT NULL COMMENT '适用套餐',
  `expire_time` datetime DEFAULT NULL,
  `used` tinyint(1) DEFAULT 0,
  `used_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_coupon_code` (`coupon_code`),
  KEY `idx_user_amount_plan_used` (`user_id`, `amount`, `applicable_plan`, `used`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代金券表';

-- 16. 优惠券使用记录表
CREATE TABLE IF NOT EXISTS `coupon_usage_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `coupon_id` BIGINT NOT NULL COMMENT '优惠券ID',
  `coupon_code` VARCHAR(32) NOT NULL COMMENT '优惠券码',
  `coupon_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '优惠券面额',
  `order_type` VARCHAR(32) NOT NULL COMMENT '订单类型',
  `order_id` VARCHAR(100) DEFAULT NULL COMMENT '关联订单ID',
  `order_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '订单原始金额',
  `discount_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '优惠金额',
  `actual_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '实际支付金额',
  `usage_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_coupon_id` (`coupon_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券使用记录表';

-- 17. 管理员操作日志表
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `admin_id` bigint NOT NULL COMMENT '管理员ID',
  `admin_username` varchar(64) NOT NULL COMMENT '管理员用户名',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型',
  `operation_action` varchar(64) NOT NULL COMMENT '操作动作',
  `target_user_ids` text COMMENT '目标用户ID列表',
  `operation_detail` text COMMENT '操作详情',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '操作IP地址',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '浏览器标识',
  `result` varchar(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_create_time_desc` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- 18. 系统通知表
CREATE TABLE IF NOT EXISTS `system_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `content` varchar(1024) DEFAULT NULL,
  `type` varchar(32) DEFAULT 'system',
  `is_read` tinyint(1) DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_unread` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

-- 19. 用户通知表
CREATE TABLE IF NOT EXISTS `user_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` text NOT NULL COMMENT '通知内容',
  `type` varchar(32) NOT NULL COMMENT '通知类型',
  `related_id` bigint DEFAULT NULL COMMENT '关联ID',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';

-- 20. 每日统计表
CREATE TABLE IF NOT EXISTS `daily_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `new_users` int NOT NULL DEFAULT 0 COMMENT '新增用户数',
  `total_users` int NOT NULL DEFAULT 0 COMMENT '累计用户总数',
  `active_users` int NOT NULL DEFAULT 0 COMMENT '活跃用户数',
  `new_conversations` int NOT NULL DEFAULT 0 COMMENT '新增会话数',
  `total_conversations` int NOT NULL DEFAULT 0 COMMENT '累计会话总数',
  `total_messages` int NOT NULL DEFAULT 0 COMMENT '新增消息数',
  `total_message_count` bigint NOT NULL DEFAULT 0 COMMENT '累计消息总数',
  `total_input_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输入Token总数',
  `total_output_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输出Token总数',
  `total_tokens` bigint NOT NULL DEFAULT 0 COMMENT 'Token总数',
  `total_points_consumed` decimal(12,3) NOT NULL DEFAULT 0.000 COMMENT '点数消耗总数',
  `api_call_success` int NOT NULL DEFAULT 0 COMMENT 'API调用成功次数',
  `api_call_failed` int NOT NULL DEFAULT 0 COMMENT 'API调用失败次数',
  `api_call_total` int NOT NULL DEFAULT 0 COMMENT 'API调用总次数',
  `model_usage` json DEFAULT NULL COMMENT '模型使用统计',
  `hourly_distribution` json DEFAULT NULL COMMENT '小时分布',
  `error_count` int NOT NULL DEFAULT 0 COMMENT '错误次数',
  `timeout_count` int NOT NULL DEFAULT 0 COMMENT '超时次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日统计表';

-- 21. 实时计数器表
CREATE TABLE IF NOT EXISTS `statistics_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counter_type` varchar(32) NOT NULL COMMENT '计数器类型',
  `counter_value` bigint NOT NULL DEFAULT 0 COMMENT '计数值',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_counter_type` (`counter_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实时计数器表';

-- 22. 平台统计数据表
CREATE TABLE IF NOT EXISTS `platform_statistics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `total_users` INT NOT NULL DEFAULT 0 COMMENT '总用户数',
  `new_users` INT NOT NULL DEFAULT 0 COMMENT '新增用户数',
  `active_users` INT NOT NULL DEFAULT 0 COMMENT '活跃用户数',
  `retained_users` INT NOT NULL DEFAULT 0 COMMENT '留存用户数',
  `total_calls` BIGINT NOT NULL DEFAULT 0 COMMENT '总对话次数',
  `total_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '总Token消耗',
  `total_points_consumed` DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '总点数消耗',
  `total_revenue` DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '总营收金额',
  `recharge_amount` DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '充值金额',
  `package_sales` INT NOT NULL DEFAULT 0 COMMENT '套餐销量',
  `avg_order_value` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '平均客单价',
  `model_usage` JSON DEFAULT NULL COMMENT '各模型使用量（JSON格式）',
  `hourly_traffic` JSON DEFAULT NULL COMMENT '小时流量分布（JSON格式）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台统计数据表';

-- ============================================
-- 第三部分：测试数据
-- ============================================

-- 平台对话模型配置
INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, base_url, api_key, status, sort_order) VALUES
('DeepSeek-V4-Flash', 'deepseek-v4-flash', 1.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 1),
('DeepSeek-V4-Pro', 'deepseek-v4-pro', 3.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 2),
('GPT-4o (虚拟)', 'deepseek-v4-pro', 5.000, 'openai', 'https://api.openai.com/v1/chat/completions', 'mock-openai-key', 1, 3),
('Claude-3.5-Sonnet (虚拟)', 'deepseek-v4-pro', 4.000, 'anthropic', 'https://api.anthropic.com/v1/messages', 'mock-anthropic-key', 1, 4);

-- 模型商店厂商配置
INSERT INTO marketplace_provider (provider_name, base_url, platform_api_key, input_price, output_price, status) VALUES
('OpenAI', 'https://api.openai.com/v1', 'mock-openai-key', 0.150000, 0.600000, 1),
('Anthropic', 'https://api.anthropic.com/v1', 'mock-anthropic-key', 0.300000, 1.500000, 1),
('DeepSeek', 'https://api.deepseek.com/v1', '${DEEPSEEK_API_KEY:your-api-key-here}', 0.100000, 0.200000, 1);

-- 套餐模板
INSERT INTO package_template (package_code, package_name, description, identity_label, points, price, duration_days, daily_call_limit, monthly_call_limit, max_tokens_per_call, priority_level, features, status, sort_order) VALUES
('free', '免费版', '基础免费套餐，适合体验用户', '普通用户', 0, 0, NULL, 10, 300, 4096, 0, '{"desc":"每日10次调用"}', 1, 1),
('basic', '基础版', '适合个人开发者日常使用', '畅享会员', 100, 29.9, 30, 50, 1500, 8192, 1, '{"desc":"每日50次调用"}', 1, 2),
('premium', '专业版', '适合专业开发者和小型团队', '尊享会员', 500, 99.9, 30, 200, 6000, 16384, 2, '{"desc":"每日200次调用"}', 1, 3),
('ultimate', '旗舰版', '适合企业和重度使用者', '至尊会员', 2000, 299.9, 30, 1000, 30000, 32768, 3, '{"desc":"每日1000次调用"}', 1, 4);

-- 管理员账户（密码: Admin@123456 / BCryptHash）
INSERT INTO user (username, password, email, balance, points, status, role) VALUES
('superadmin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@aiplatform.com', 0.000, 10000.000, 1, 'SUPER_ADMIN');

-- 测试用户
INSERT INTO user (username, password, email, balance, points, status, role, free_api_strategy, free_quota, daily_call_limit, monthly_call_limit) VALUES
('testuser01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'test01@test.com', 1000.000, 1000.000, 1, 'USER', 'QUOTA_BASED', 100, 100, 3000),
('testuser02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'test02@test.com', 100.000, 100.000, 1, 'USER', 'COUNT_LIMITED', 0, 50, 1500);

-- 实时计数器初始数据
INSERT INTO statistics_counter (counter_type, counter_value) VALUES
('USER_TOTAL', 0), ('CONVERSATION_TOTAL', 0), ('MESSAGE_TOTAL', 0),
('API_CALL_TOTAL', 0), ('API_CALL_SUCCESS', 0), ('API_CALL_FAILED', 0);

SET FOREIGN_KEY_CHECKS = 1;

SELECT '============================================' AS '';
SELECT '  数据库重建完成！' AS result;
SELECT '  默认管理员: superadmin / Admin@123456' AS admin_info;
SELECT '  默认测试用户: testuser01, testuser02' AS test_users;
SELECT '  密码: Test@123456' AS password;
SELECT '============================================' AS '';
