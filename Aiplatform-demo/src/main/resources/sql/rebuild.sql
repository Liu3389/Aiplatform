-- ============================================
-- AI Gateway Platform - 一键重建数据库脚本
-- 版本: v1.1 (2026-05-19)
-- 说明: 整合建表与测试数据，可重复执行（幂等）
-- 变更: 修复每日点数发放按会员等级区分、完善充值/消费记录查询、新增平台营收统计
-- 用法: mysql -h HOST -u USER -p < rebuild.sql
-- 安全: API Key 使用占位符，部署时通过环境变量注入
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 第一部分：清理旧表（按依赖关系逆序删除，支持全新环境）
-- ============================================

DROP TABLE IF EXISTS `user_notification`;
DROP TABLE IF EXISTS `system_notification`;
DROP TABLE IF EXISTS `admin_operation_log`;
DROP TABLE IF EXISTS `billing_record`;
DROP TABLE IF EXISTS `call_log`;
DROP TABLE IF EXISTS `points_bill`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_message_history`;
DROP TABLE IF EXISTS `conversation`;
DROP TABLE IF EXISTS `api_key`;
DROP TABLE IF EXISTS `coupon_usage_record`;
DROP TABLE IF EXISTS `coupon`;
DROP TABLE IF EXISTS `user_subscription`;
DROP TABLE IF EXISTS `user_oauth_identity`;
DROP TABLE IF EXISTS `user_contact_verification`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `user_marketplace_key`;
DROP TABLE IF EXISTS `marketplace_usage_log`;
DROP TABLE IF EXISTS `marketplace_provider`;
DROP TABLE IF EXISTS `platform_model_config`;
DROP TABLE IF EXISTS `package_template`;
DROP TABLE IF EXISTS `daily_statistics`;
DROP TABLE IF EXISTS `statistics_counter`;
DROP TABLE IF EXISTS `platform_statistics`;
DROP TABLE IF EXISTS `file_parse_task`;

-- ============================================
-- 第二部分：核心建表
-- ============================================

-- 1. 用户表
CREATE TABLE `user`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `nickname` varchar(64) DEFAULT NULL COMMENT '用户展示昵称',
  `avatar_url` varchar(500) DEFAULT NULL COMMENT '自定义头像存储引用',
  `password` varchar(128) NOT NULL COMMENT '密码（BCrypt加密存储）',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(32) DEFAULT NULL COMMENT '已验证手机号',
  `balance` decimal(10,3) NOT NULL DEFAULT '0.000' COMMENT '账户余额（元）',
  `points` decimal(10,3) NOT NULL DEFAULT '100.000' COMMENT '点数余额',
  `points_version` bigint NOT NULL DEFAULT 0 COMMENT '点数余额版本，用于防止结算重放覆盖新余额',
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

CREATE TABLE `user_contact_verification`
(
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `email` varchar(128) DEFAULT NULL COMMENT '经验证码验证的邮箱',
  `phone` varchar(32) DEFAULT NULL COMMENT '经验证码验证的手机号',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '验证更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_verified_contact_email` (`email`),
  UNIQUE KEY `uk_verified_contact_phone` (`phone`),
  CONSTRAINT `fk_verified_contact_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户已验证联系方式';

-- 2. API Key 表
CREATE TABLE `api_key`
(
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
CREATE TABLE `platform_model_config`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `display_name` varchar(64) NOT NULL COMMENT '展示名称（如 GPT-4o）',
  `actual_model` varchar(64) NOT NULL COMMENT '实际调用模型（如 deepseek-v4-pro）',
  `fixed_points` decimal(10,3) DEFAULT '3.000' COMMENT '每次对话固定消耗点数',
  `provider` varchar(32) DEFAULT 'deepseek' COMMENT '关联厂商',
  `description` varchar(255) DEFAULT NULL COMMENT '模型描述（模型商店展示）',
  `base_url` varchar(256) DEFAULT NULL COMMENT '厂商 API 地址',
  `api_key` varchar(128) DEFAULT NULL COMMENT '平台持有的 API Key',
  `sort_order` int DEFAULT 0 COMMENT '前端展示排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `supports_vision` tinyint NOT NULL DEFAULT 0 COMMENT '是否支持图片：0-不支持，1-支持',
  `max_output_tokens` int NOT NULL DEFAULT 4096 COMMENT '平台单模型输出Token上限',
  `access_level`    int              DEFAULT 0 COMMENT '所需会员等级（0-基础，1-中端，2-高端，3-全部）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台对话模型配置表';

-- 4. 模型商店厂商配置表
CREATE TABLE `marketplace_provider`
(
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
CREATE TABLE `user_marketplace_key`
(
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
CREATE TABLE `marketplace_usage_log`
(
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
CREATE TABLE `package_template`
(
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_code` VARCHAR(32) NOT NULL COMMENT '套餐代码（唯一标识）',
  `package_name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '套餐描述',
  `identity_label` VARCHAR(32) DEFAULT NULL COMMENT '身份标识',
  `points_granted`     DECIMAL(10, 3) NOT NULL DEFAULT 0 COMMENT '开通赠送点数（永久有效）',
  `daily_points`       DECIMAL(10, 3) NOT NULL DEFAULT 0 COMMENT '每日发放永久点数（付费会员可叠加）',
  `price_monthly`      DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '月付价格（元）',
  `price_quarterly`    DECIMAL(10, 2)          DEFAULT NULL COMMENT '季付价格（元）',
  `price_yearly`       DECIMAL(10, 2)          DEFAULT NULL COMMENT '年付价格（元）',
  `max_input_tokens`   INT                     DEFAULT 0 COMMENT '单次最大输入Token数（0表示无限制）',
  `max_output_tokens`  INT                     DEFAULT 0 COMMENT '单次最大输出Token数（0表示无限制）',
  `model_access_level` INT                     DEFAULT 0 COMMENT '模型访问等级（0-基础，1-中端，2-高端，3-全部）',
  `priority_level` INT DEFAULT 0 COMMENT '优先级等级',
  `daily_call_limit`   INT                     DEFAULT 0 COMMENT '每日调用限制次数',
  `monthly_call_limit` INT                     DEFAULT 0 COMMENT '每月调用限制次数',
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
CREATE TABLE `conversation`
(
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
CREATE TABLE `chat_message`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `request_id` varchar(64) DEFAULT NULL COMMENT 'AI请求幂等键',
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `file_urls` json DEFAULT NULL COMMENT '关联文件URL列表（兼容旧数据）',
  `file_ids`  json DEFAULT NULL COMMENT '关联文件ID列表（JSON数组，关联file_parse_task表）',
  `metadata` json DEFAULT NULL COMMENT '元数据',
  `thinking_time` double DEFAULT NULL COMMENT '思考耗时（秒）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_message_request_role` (`request_id`, `role`),
  KEY `idx_conversation_create` (`conversation_id`, `create_time`),
  KEY `idx_user_conversation` (`user_id`, `conversation_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表（热数据-最近30天）';

-- 10. 聊天消息历史表（冷数据）
CREATE TABLE `chat_message_history`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `request_id` varchar(64) DEFAULT NULL COMMENT 'AI请求幂等键',
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `file_urls` json DEFAULT NULL COMMENT '关联文件URL列表（兼容旧数据）',
  `file_ids`  json DEFAULT NULL COMMENT '关联文件ID列表（JSON数组，关联file_parse_task表）',
  `metadata` json DEFAULT NULL COMMENT '元数据',
  `thinking_time` double DEFAULT NULL COMMENT '思考耗时（秒）',
  `original_create_time` datetime NOT NULL COMMENT '原始创建时间',
  `archive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_original_time` (`conversation_id`, `original_create_time`),
  KEY `idx_user_conversation` (`user_id`, `conversation_id`),
  KEY `idx_chat_history_request_role` (`request_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息历史表（冷数据-30天前）';

-- 11. 调用日志表
CREATE TABLE `call_log`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_key` varchar(64) DEFAULT NULL COMMENT '使用的API Key（平台对话模式可为空）',
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
CREATE TABLE `billing_record`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `call_log_id` bigint DEFAULT NULL COMMENT '关联的调用日志ID（充值记录无关联）',
  `request_id` varchar(64) DEFAULT NULL COMMENT 'AI请求幂等键；充值流水为空',
  `amount` decimal(10,3) NOT NULL COMMENT '金额（元）',
  `type` tinyint NOT NULL COMMENT '类型：1-扣费，2-充值',
  `balance_before` decimal(10,3) NOT NULL COMMENT '操作前余额',
  `balance_after` decimal(10,3) NOT NULL COMMENT '操作后余额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_request_id` (`request_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_call_log_id` (`call_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费记录表';

-- 12.1 AI 对话结算 outbox：先持久化结算意图，再由后台投递器发布 RabbitMQ
CREATE TABLE `billing_settlement_outbox`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(64) NOT NULL COMMENT 'AI请求幂等键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `actual_points` decimal(10,3) NOT NULL COMMENT '本次应结算点数',
  `model_name` varchar(100) NOT NULL COMMENT '模型展示名',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SETTLED',
  `dispatch_attempts` int NOT NULL DEFAULT 0 COMMENT '投递或本地处理尝试次数',
  `process_attempts` int NOT NULL DEFAULT 0 COMMENT '消费失败次数',
  `last_attempt_at` datetime DEFAULT NULL COMMENT '最近投递/处理时间；超时后允许重试',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `settled_at` datetime DEFAULT NULL COMMENT '结算完成时间',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次错误',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_request_id` (`request_id`),
  KEY `idx_settlement_dispatch` (`status`, `last_attempt_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI点数结算本地消息表';

-- 12.2 AI 请求预扣状态：持久化预扣、上游调用、响应和恢复进度
CREATE TABLE `ai_charge_intent`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(64) NOT NULL COMMENT 'AI请求幂等键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `reserved_points` decimal(10,3) NOT NULL COMMENT '请求开始前预扣点数',
  `actual_points` decimal(10,3) NOT NULL COMMENT '响应后确认的结算点数',
  `model_name` varchar(100) NOT NULL COMMENT '模型展示名',
  `request_fingerprint` char(64) DEFAULT NULL COMMENT '客户端幂等请求内容 SHA-256 摘要',
  `status` varchar(24) NOT NULL COMMENT '请求状态：PREPARING/RESERVED/IN_FLIGHT/RESPONSE_RECEIVED/MESSAGES_PERSISTED/SETTLEMENT_PENDING/REFUND_PENDING/SETTLED/REFUNDED/MANUAL_REVIEW',
  `reservation_confirmed` tinyint NOT NULL DEFAULT 0 COMMENT '是否已确认 Redis 预扣',
  `upstream_started_at` datetime DEFAULT NULL COMMENT '开始调用上游时间',
  `response_received_at` datetime DEFAULT NULL COMMENT '收到完整上游响应时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
  `recovery_claimed_at` datetime DEFAULT NULL COMMENT '恢复扫描租约时间',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近恢复或失败原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_charge_request_id` (`request_id`),
  KEY `idx_ai_charge_recovery` (`status`, `update_time`, `recovery_claimed_at`),
  KEY `idx_ai_charge_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI请求点数预扣与恢复状态';

-- 12.3 AI 请求人工对账审计
CREATE TABLE `ai_charge_review_audit`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(64) NOT NULL COMMENT 'AI请求幂等键',
  `user_id` bigint NOT NULL COMMENT '被处理的用户ID',
  `admin_id` bigint NOT NULL COMMENT '执行核验的超级管理员ID',
  `action` varchar(16) NOT NULL COMMENT '处理方向：REFUND/SETTLE',
  `actual_points` decimal(10,3) DEFAULT NULL COMMENT '上游确认计费时的实际点数',
  `evidence` varchar(1000) NOT NULL COMMENT '供应商日志引用与核验结论，不存储密钥',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/RESOLVED/FAILED',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次重试说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次登记时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
  `completed_at` datetime DEFAULT NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_charge_review_request` (`request_id`),
  KEY `idx_ai_charge_review_status_time` (`status`, `update_time`),
  KEY `idx_ai_charge_review_admin_time` (`admin_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI点数人工对账审计记录';

-- 13. 点数明细账单表
CREATE TABLE `points_bill`
(
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `change_type` VARCHAR(32) NOT NULL COMMENT '变动类型：GRANT/DAILY_GRANT/DAILY_PERMANENT_GRANT/DEDUCT/SETTLE/ROLLBACK/EXPIRE',
  `points_change` DECIMAL(10,3) NOT NULL COMMENT '变动点数（正增负减）',
  `balance_before` DECIMAL(10,3) NOT NULL COMMENT '变动前点数余额',
  `balance_after` DECIMAL(10,3) NOT NULL COMMENT '变动后点数余额',
  `business_id` VARCHAR(100) DEFAULT NULL COMMENT '关联业务ID',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
  `model_name` VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
  `expire_time` DATETIME DEFAULT NULL COMMENT '点数过期时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `is_temporary` TINYINT NOT NULL DEFAULT 0 COMMENT '是否限时点数（1-限时/每日发放，0-永久/充值赠送）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_business_id` (`business_id`),
  KEY `idx_user_time` (`user_id`, `create_time` DESC),
  KEY `idx_change_type` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点数明细账单表';

-- 14. 每笔限时点数发放的剩余余额
CREATE TABLE `points_temporary_lot`
(
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `business_id` VARCHAR(100) NOT NULL COMMENT '每日发放幂等业务ID',
  `granted_points` DECIMAL(10,3) NOT NULL COMMENT '发放点数',
  `remaining_points` DECIMAL(10,3) NOT NULL COMMENT '尚未消费且未过期点数',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DEPLETED/EXPIRED',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expired_at` DATETIME DEFAULT NULL COMMENT '实际过期入账时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_temp_user_business` (`user_id`,`business_id`),
  KEY `idx_points_temp_expiry` (`status`,`expire_time`,`user_id`),
  KEY `idx_points_temp_user_status` (`user_id`,`status`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限时点数剩余余额';

-- 用户两步验证配置，TOTP 密钥以 AES-GCM 密文保存；备用码只保存哈希
CREATE TABLE `user_two_factor`
(
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `secret_ciphertext` VARCHAR(255) DEFAULT NULL COMMENT 'AES-GCM加密的TOTP密钥',
  `pending_secret_ciphertext` VARCHAR(255) DEFAULT NULL COMMENT '待确认的AES-GCM加密TOTP密钥',
  `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已完成启用验证',
  `last_totp_step` BIGINT NOT NULL DEFAULT -1 COMMENT '最近一次消费的TOTP时间步，防止重放',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_two_factor_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户两步验证配置';

CREATE TABLE `user_two_factor_backup_code`
(
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `code_hash` CHAR(64) NOT NULL COMMENT '高熵备用码SHA-256摘要',
  `consumed_at` DATETIME DEFAULT NULL COMMENT '消费时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_two_factor_backup_hash` (`user_id`,`code_hash`),
  KEY `idx_two_factor_backup_unused` (`user_id`,`consumed_at`),
  CONSTRAINT `fk_two_factor_backup_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户两步验证一次性备用码';

-- OAuth/OIDC 第三方身份与平台用户的持久化绑定
CREATE TABLE `user_oauth_identity`
(
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '平台用户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT 'OAuth/OIDC 提供方',
  `provider_subject` VARCHAR(255) NOT NULL COMMENT '提供方稳定用户标识',
  `display_name` VARCHAR(255) DEFAULT NULL COMMENT '展示名称',
  `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT '第三方头像 HTTPS 地址',
  `email` VARCHAR(255) DEFAULT NULL COMMENT '提供方已验证邮箱',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_provider_subject` (`provider`,`provider_subject`),
  UNIQUE KEY `uk_oauth_user_provider` (`user_id`,`provider`),
  CONSTRAINT `fk_oauth_identity_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户第三方身份绑定';

-- 15. 用户订阅记录表
CREATE TABLE `user_subscription`
(
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
CREATE TABLE `coupon`
(
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
  KEY `idx_user_amount_plan_used` (`user_id`, `amount`, `applicable_plan`, `used`),
  KEY `idx_user_used_expire` (`user_id`, `used`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代金券表';

-- 16. 优惠券使用记录表
CREATE TABLE `coupon_usage_record`
(
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
  UNIQUE KEY `uk_coupon_id` (`coupon_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券使用记录表';

-- 17. 管理员操作日志表
CREATE TABLE `admin_operation_log`
(
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
CREATE TABLE `system_notification`
(
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

-- 19. 用户通知表（统一通知入口：广播投影 + 点对点通知均落此表）
CREATE TABLE `user_notification`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` text NOT NULL COMMENT '通知内容',
  `type` varchar(32) NOT NULL COMMENT '通知类型',
  `related_id` bigint DEFAULT NULL COMMENT '关联ID（广播时指向 system_notification.id）',
  `metadata` json DEFAULT NULL COMMENT '业务元数据（如 couponId 等）',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读, 1-已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_unread_time` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';

-- 20. 文件解析任务表
CREATE TABLE `file_parse_task`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_id`            VARCHAR(64)  NOT NULL COMMENT '文件唯一ID（UUID）',
    `user_id`            BIGINT       NOT NULL COMMENT '用户ID',
    `original_name`      VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `oss_url`            VARCHAR(512) NOT NULL COMMENT 'OSS存储地址',
    `file_size`          BIGINT       NOT NULL COMMENT '文件大小（字节）',
    `file_type`          VARCHAR(32)  NOT NULL COMMENT '文件类型（txt/md/docx/xlsx等）',

    -- 解析状态
    `parse_status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING/PARSING/SUCCESS/FAILED',
    `parsed_text_length` INT                   DEFAULT 0 COMMENT '解析后文本长度',
    `text_summary`       VARCHAR(500)          DEFAULT NULL COMMENT '文本摘要（AI生成，50-100字）',
    `error_message`      TEXT                  DEFAULT NULL COMMENT '错误信息（解析失败时）',

    -- 时间戳
    `upload_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `parse_start_time`   DATETIME              DEFAULT NULL COMMENT '开始解析时间',
    `parse_end_time`     DATETIME              DEFAULT NULL COMMENT '解析完成时间',
    `last_dispatch_at`   DATETIME              DEFAULT NULL COMMENT '最近 Rabbit 发布租约时间',
    `dispatch_attempts` INT NOT NULL DEFAULT 0 COMMENT '解析任务发布尝试次数',
    `parse_lease_token`  VARCHAR(64)           DEFAULT NULL COMMENT '当前解析 worker fencing token',
    `parse_lease_until`  DATETIME              DEFAULT NULL COMMENT '当前解析租约到期时间',
    `parse_attempts`     INT NOT NULL DEFAULT 0 COMMENT '解析 worker 实际领取次数',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 关联信息
    `conversation_id`    BIGINT                DEFAULT NULL COMMENT '关联会话ID（对话时使用）',
    `is_used`            TINYINT(1)            DEFAULT 0 COMMENT '是否已在对话中使用',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_id` (`file_id`),
    KEY `idx_user_status` (`user_id`, `parse_status`),
    KEY `idx_parse_dispatch` (`parse_status`, `last_dispatch_at`, `id`),
    KEY `idx_parse_lease` (`parse_status`, `parse_lease_until`, `id`),
    KEY `idx_upload_time` (`upload_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件解析任务表';

-- 21. 每日统计表
CREATE TABLE `daily_statistics`
(
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

-- 22. 实时计数器表
CREATE TABLE `statistics_counter`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counter_type` varchar(32) NOT NULL COMMENT '计数器类型',
  `counter_value` bigint NOT NULL DEFAULT 0 COMMENT '计数值',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_counter_type` (`counter_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实时计数器表';

-- 23. 平台统计数据表
CREATE TABLE `platform_statistics`
(
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
-- 第三部分：基础数据（DDL 后必需的字典/配置/管理员，不含测试数据）
-- 测试数据请见 seed-test.sql
-- ============================================

-- 平台对话模型配置（仅保留可用真实模型；虚拟模型与 mock 密钥见 seed-test.sql）
-- 仅写入公开模型元数据；所有 API Key 均留空，部署后通过管理界面配置真实上游凭据。
INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, description, base_url, api_key,
                                   supports_vision, status, sort_order, access_level)
VALUES ('DeepSeek-V4-Flash', 'deepseek-flash', 1.000, 'deepseek', 'DeepSeek 轻量旗舰，极速响应，性价比之选',
        'https://api.deepseek.com', NULL, 0, 1, 1, 0),
       ('DeepSeek-V4-Pro', 'deepseek-v4-pro', 3.000, 'deepseek', 'DeepSeek 旗舰模型，深度推理与复杂任务首选',
        'https://api.deepseek.com', NULL, 0, 1, 2, 1),
       ('GPT-5.2', 'gpt-5.2', 8.000, 'openai', 'OpenAI 最新旗舰，多模态推理与智能体能力全面领先',
        NULL, NULL, 1, 1, 3, 2),
       ('GPT-5 mini', 'gpt-5-mini', 3.000, 'openai', 'OpenAI 轻量前沿模型，高速低成本',
        NULL, NULL, 1, 1, 4, 1),
       ('o4-mini', 'o4-mini', 5.000, 'openai', 'OpenAI 推理系列，数学与代码推理强化',
        NULL, NULL, 0, 1, 5, 2),
       ('Claude Opus 4.5', 'claude-opus-4-5', 10.000, 'anthropic', 'Anthropic 顶级旗舰，长文写作与复杂编程最强',
        NULL, NULL, 1, 1, 6, 3),
       ('Claude Sonnet 4.5', 'claude-sonnet-4-5', 5.000, 'anthropic', 'Anthropic 均衡旗舰，智能与速度兼备',
        NULL, NULL, 1, 1, 7, 2),
       ('Gemini 3 Pro', 'gemini-3-pro', 8.000, 'google', 'Google 新一代旗舰，原生多模态与超长上下文',
        NULL, NULL, 1, 1, 8, 3),
       ('Gemini 2.5 Flash', 'gemini-2.5-flash', 2.000, 'google', 'Google 高速多模态模型，大上下文低延迟',
        NULL, NULL, 1, 1, 9, 1),
       ('Grok 4.1', 'grok-4.1', 6.000, 'xai', 'xAI 旗舰模型，实时信息与工具调用能力突出',
        NULL, NULL, 0, 1, 10, 2),
       ('Qwen3-Max', 'qwen3-max', 4.000, 'alibaba', '阿里通义千问旗舰，中文理解与推理顶级水准',
        NULL, NULL, 1, 1, 11, 2),
       ('GLM-5', 'glm-5', 3.000, 'zhipu', '智谱新一代旗舰，中文创作与代码生成强化',
        NULL, NULL, 1, 1, 12, 1),
       ('Kimi K2.5', 'kimi-k2.5', 3.000, 'moonshot', '月之暗面长文本旗舰，超长上下文深度分析',
        NULL, NULL, 0, 1, 13, 1),
       ('Doubao Seed 1.6', 'doubao-seed-1.6', 2.000, 'bytedance', '字节跳动豆包大模型，多模态理解均衡',
        NULL, NULL, 1, 1, 14, 1),
       ('MiniMax M2', 'minimax-m2', 2.000, 'minimax', 'MiniMax 新一代开源旗舰，高效推理',
        NULL, NULL, 0, 1, 15, 1);

-- 视觉模型复用 DeepSeek-V4-Flash 的 API 配置，避免在初始化脚本中重复保存密钥
INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, description, base_url, api_key,
                                   supports_vision, status, sort_order, access_level)
SELECT 'DeepSeek-V4-Flash-Vision-Exp', 'deepseek-flash', 2.000, 'deepseek',
       'DeepSeek 视觉理解模型，支持截图、图表和图片问答', base_model.base_url, base_model.api_key,
       1, 1, 16, 1
FROM platform_model_config base_model
WHERE base_model.actual_model = 'deepseek-flash'
  AND NOT EXISTS (
      SELECT 1 FROM platform_model_config existing_model
      WHERE existing_model.display_name = 'DeepSeek-V4-Flash-Vision-Exp'
  )
LIMIT 1;

-- 模型商店厂商配置（仅保留可用真实厂商；mock 厂商见 seed-test.sql）
INSERT INTO marketplace_provider (provider_name, base_url, platform_api_key, input_price, output_price, status) VALUES
('DeepSeek', 'https://api.deepseek.com/v1', '', 0.100000, 0.200000, 1);

-- 套餐模板（严格按照平台计费规则）
INSERT INTO package_template (package_code, package_name, description, identity_label, points_granted, daily_points,
                              price_monthly, price_quarterly, price_yearly, max_input_tokens, max_output_tokens,
                              model_access_level, priority_level, features, status, sort_order)
VALUES ('free', '免费普通用户', '基础免费套餐，每日赠送30点数', '普通用户', 0, 30, 0, NULL, NULL, 6000, 6000, 0, 0,
        '{"models":["deepseek-v4-flash"],"features":["SSE流式输出","图片对话"]}', 1, 1),
       ('silver', '白银月卡会员', '适合个人日常使用，每日80永久点数', '白银会员', 600, 80, 15, 40, 100, 12000, 12000, 1,
        1, '{"models":["deepseek-v4-flash","deepseek-v4-pro"],"features":["单文件解析","多文件解析","优先响应"]}', 1,
        2),
       ('gold', '黄金进阶会员', '专业开发者首选，每日150永久点数', '黄金会员', 1800, 150, 30, 80, 200, 18000, 18000, 2,
        2,
        '{"models":["deepseek-v4-flash","deepseek-v4-pro","gpt-4o"],"features":["长文本对话","云端存储","后台优先队列"]}',
        1, 3),
       ('diamond', '钻石旗舰会员', '企业级服务，每日300永久点数', '钻石会员', 5000, 300, 60, 160, 420, 32000, 32000, 3,
        3, '{"models":["all"],"features":["超大文件解析","所有模型权限","最高优先级"]}', 1, 4);

-- 超级管理员账户（密码: Test@123456 / BCryptHash）
INSERT INTO user (username, password, email, balance, points, status, role)
VALUES ('superadmin',
        '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK',
        'admin@aiplatform.com', 0.000,
        10000.000, 1, 'SUPER_ADMIN');

-- 实时计数器初始数据（系统运行必需，非测试数据）
INSERT INTO statistics_counter (counter_type, counter_value) VALUES
('USER_TOTAL', 0), ('CONVERSATION_TOTAL', 0), ('MESSAGE_TOTAL', 0),
('API_CALL_TOTAL', 0), ('API_CALL_SUCCESS', 0), ('API_CALL_FAILED', 0);

SET FOREIGN_KEY_CHECKS = 1;

SELECT '============================================' AS '';
SELECT '  ✅ 数据库基础数据初始化完成！' AS result;
SELECT '  - superadmin 超级管理员（密码: Test@123456）' AS admin;
SELECT '  - 2 个真实可用模型（DeepSeek）' AS models;
SELECT '  - 4 个套餐模板（free/silver/gold/diamond）' AS packages;
SELECT '  � 如需测试数据请执行: mysql ... < seed-test.sql' AS tip;
SELECT '============================================' AS '';

SELECT id, package_code, package_name, status
FROM package_template;
