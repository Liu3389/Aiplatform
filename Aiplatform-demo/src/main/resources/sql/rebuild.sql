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
  `base_url` varchar(256) DEFAULT NULL COMMENT '厂商 API 地址',
  `api_key` varchar(128) DEFAULT NULL COMMENT '平台持有的 API Key',
  `sort_order` int DEFAULT 0 COMMENT '前端展示排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `supports_vision` tinyint NOT NULL DEFAULT 0 COMMENT '是否支持图片：0-不支持，1-支持',
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
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `file_urls` json DEFAULT NULL COMMENT '关联文件URL列表（兼容旧数据）',
  `file_ids`  json DEFAULT NULL COMMENT '关联文件ID列表（JSON数组，关联file_parse_task表）',
  `metadata` json DEFAULT NULL COMMENT '元数据',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
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
  `role` varchar(16) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `model` varchar(64) DEFAULT NULL COMMENT '使用的模型',
  `tokens` int DEFAULT '0' COMMENT 'Token数量',
  `file_urls` json DEFAULT NULL COMMENT '关联文件URL列表（兼容旧数据）',
  `file_ids`  json DEFAULT NULL COMMENT '关联文件ID列表（JSON数组，关联file_parse_task表）',
  `metadata` json DEFAULT NULL COMMENT '元数据',
  `original_create_time` datetime NOT NULL COMMENT '原始创建时间',
  `archive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_original_time` (`conversation_id`, `original_create_time`),
  KEY `idx_user_conversation` (`user_id`, `conversation_id`)
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
CREATE TABLE `points_bill`
(
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
  `is_temporary` TINYINT NOT NULL DEFAULT 0 COMMENT '是否限时点数（1-限时/每日发放，0-永久/充值赠送）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_business_id` (`business_id`),
  KEY `idx_user_time` (`user_id`, `create_time` DESC),
  KEY `idx_change_type` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点数明细账单表';

-- 14. 用户订阅记录表
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
  KEY `idx_user_amount_plan_used` (`user_id`, `amount`, `applicable_plan`, `used`)
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
  KEY `idx_coupon_id` (`coupon_id`),
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

-- 19. 用户通知表
CREATE TABLE `user_notification`
(
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
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 关联信息
    `conversation_id`    BIGINT                DEFAULT NULL COMMENT '关联会话ID（对话时使用）',
    `is_used`            TINYINT(1)            DEFAULT 0 COMMENT '是否已在对话中使用',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_id` (`file_id`),
    KEY `idx_user_status` (`user_id`, `parse_status`),
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
-- 第三部分：测试数据
-- ============================================

-- 平台对话模型配置（access_level: 0-基础，1-中端，2-高端，3-全部）
INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, base_url, api_key,
                                   supports_vision, status, sort_order, access_level)
VALUES ('DeepSeek-V4-Flash', 'deepseek-v4-flash', 1.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions',
        'REDACTED_PROVIDER_API_KEY', 0, 1, 1, 0),
       ('DeepSeek-V4-Pro', 'deepseek-v4-pro', 3.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions',
        'REDACTED_PROVIDER_API_KEY', 0, 1, 2, 1),
       ('GPT-4o (虚拟)', 'gpt-4o', 5.000, 'openai', 'https://api.openai.com/v1/chat/completions', 'mock-openai-key', 1,
        1, 3, 2),
       ('Claude-3.5-Sonnet (虚拟)', 'claude-3-5-sonnet', 4.000, 'anthropic', 'https://api.anthropic.com/v1/messages',
        'mock-anthropic-key', 1, 1, 4, 2),
       ('Gemini-2.0-Pro (虚拟)', 'gemini-2.0-pro', 4.000, 'google',
        'https://generativelanguage.googleapis.com/v1beta/models', 'mock-google-key', 1, 1, 5, 2),
       ('Qwen-Max (虚拟)', 'qwen-max', 2.000, 'qwen',
        'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation', 'mock-qwen-key', 0, 1, 6, 1),
       ('Qwen-Plus (虚拟)', 'qwen-plus', 1.000, 'qwen',
        'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation', 'mock-qwen-key', 0, 1, 7, 0),
       ('Claude-3-Opus (虚拟)', 'claude-3-opus', 5.000, 'anthropic', 'https://api.anthropic.com/v1/messages',
        'mock-anthropic-key', 1, 1, 8, 3),
       ('GPT-4-Turbo (虚拟)', 'gpt-4-turbo', 4.000, 'openai', 'https://api.openai.com/v1/chat/completions',
        'mock-openai-key', 1, 1, 9, 2),
       ('Gemini-2.0-Flash (虚拟)', 'gemini-2.0-flash', 1.000, 'google',
        'https://generativelanguage.googleapis.com/v1beta/models', 'mock-google-key', 1, 1, 10, 1);

-- 模型商店厂商配置
INSERT INTO marketplace_provider (provider_name, base_url, platform_api_key, input_price, output_price, status) VALUES
('OpenAI', 'https://api.openai.com/v1', 'mock-openai-key', 0.150000, 0.600000, 1),
('Anthropic', 'https://api.anthropic.com/v1', 'mock-anthropic-key', 0.300000, 1.500000, 1),
('DeepSeek', 'https://api.deepseek.com/v1', 'REDACTED_PROVIDER_API_KEY', 0.100000, 0.200000, 1);

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

-- 管理员账户（密码: Test@123456 / BCryptHash）
INSERT INTO user (username, password, email, balance, points, status, role)
VALUES ('superadmin',
        '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK',
        'admin@aiplatform.com', 0.000,
        10000.000, 1, 'SUPER_ADMIN');

-- 测试用户（密码: Test@123456）- 用于接口测试
INSERT INTO user (username, password, email, balance, points, status, role, free_api_strategy, free_quota,
                  daily_call_limit, monthly_call_limit)
VALUES ('testuser',
        '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK',
        'test@example.com',
        1000.000,
        1000.000,
        1,
        'USER',
        'QUOTA_BASED',
        100,
        100,
        3000);

-- 白银会员（月卡，30天后过期）
INSERT INTO user (username, password, email, balance, points, status, role, membership, membership_expire_time,
                  free_api_strategy, daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('silver_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'silver@test.com', 500.000,
        1200.000, 1, 'USER', 'silver', DATE_ADD(NOW(), INTERVAL 30 DAY), 'DAILY_FREE', 100, 3000, 80);

-- 黄金会员（季卡，90天后过期）
INSERT INTO user (username, password, email, balance, points, status, role, membership, membership_expire_time,
                  free_api_strategy, daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('gold_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'gold@test.com', 1000.000,
        3000.000, 1, 'USER', 'gold', DATE_ADD(NOW(), INTERVAL 90 DAY), 'DAILY_FREE', 200, 6000, 150);

-- 钻石会员（年卡，365天后过期）
INSERT INTO user (username, password, email, balance, points, status, role, membership, membership_expire_time,
                  free_api_strategy, daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('diamond_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'diamond@test.com', 5000.000,
        10000.000, 1, 'USER', 'diamond', DATE_ADD(NOW(), INTERVAL 365 DAY), 'DAILY_FREE', 500, 15000, 300);

-- 普通会员（无会员，仅免费额度）
INSERT INTO user (username, password, email, balance, points, status, role, membership, free_api_strategy,
                  daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('free_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'free@test.com', 100.000, 300.000,
        1, 'USER', NULL, 'QUOTA_BASED', 50, 1500, 30);

-- 禁用用户
INSERT INTO user (username, password, email, balance, points, status, role, membership)
VALUES ('disabled_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'disabled@test.com', 0.000,
        0.000, 0, 'USER', 'silver');

-- API Key（每个用户生成一个Key）
INSERT INTO api_key (api_key, user_id, name, status, rate_limit)
VALUES ('sk-test-silver-user-key-001', (SELECT id FROM user WHERE username = 'silver_user'), 'Silver User Key', 1, 100),
       ('sk-test-gold-user-key-001', (SELECT id FROM user WHERE username = 'gold_user'), 'Gold User Key', 1, 200),
       ('sk-test-diamond-user-key-001', (SELECT id FROM user WHERE username = 'diamond_user'), 'Diamond User Key', 1,
        500),
       ('sk-test-free-user-key-001', (SELECT id FROM user WHERE username = 'free_user'), 'Free User Key', 1, 50),
       ('sk-test-testuser-key-001', (SELECT id FROM user WHERE username = 'testuser'), 'Test User Key', 1, 100);

-- 为 silver_user 创建会话
INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'silver_user'), 'Python学习助手', 'deepseek-v4-flash', 6,
        DATE_SUB(NOW(), INTERVAL 2 HOUR)),
       ((SELECT id FROM user WHERE username = 'silver_user'), '数据分析讨论', 'deepseek-v4-pro', 4,
        DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 为 gold_user 创建会话
INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'gold_user'), 'Java项目架构设计', 'gpt-4o', 8,
        DATE_SUB(NOW(), INTERVAL 3 HOUR)),
       ((SELECT id FROM user WHERE username = 'gold_user'), '微服务最佳实践', 'deepseek-v4-pro', 5,
        DATE_SUB(NOW(), INTERVAL 2 DAY)),
       ((SELECT id FROM user WHERE username = 'gold_user'), '数据库优化方案', 'qwen-max', 3,
        DATE_SUB(NOW(), INTERVAL 5 DAY));

-- 为 diamond_user 创建会话
INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'diamond_user'), '企业级系统设计', 'claude-3-opus', 10,
        DATE_SUB(NOW(), INTERVAL 1 HOUR)),
       ((SELECT id FROM user WHERE username = 'diamond_user'), 'AI模型选型分析', 'gpt-4-turbo', 7,
        DATE_SUB(NOW(), INTERVAL 6 HOUR)),
       ((SELECT id FROM user WHERE username = 'diamond_user'), '性能调优指南', 'deepseek-v4-pro', 12,
        DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 为 free_user 创建会话
INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'free_user'), 'Python基础问题', 'deepseek-v4-flash', 3,
        DATE_SUB(NOW(), INTERVAL 4 HOUR)),
       ((SELECT id FROM user WHERE username = 'free_user'), 'JavaScript入门', 'deepseek-v4-flash', 2,
        DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 为 testuser 创建会话
INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'testuser'), '测试会话1', 'deepseek-v4-flash', 4,
        DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
       ((SELECT id FROM user WHERE username = 'testuser'), '测试会话2', 'deepseek-v4-pro', 2,
        DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- silver_user 的第一个会话消息
SET @conv_silver_1 = (SELECT id
                      FROM conversation
                      WHERE user_id = (SELECT id FROM user WHERE username = 'silver_user')
                      ORDER BY create_time DESC
                      LIMIT 1);
SET @user_silver = (SELECT id
                    FROM user
                    WHERE username = 'silver_user');

INSERT INTO chat_message (conversation_id, user_id, role, content, model, tokens, file_ids)
VALUES (@conv_silver_1, @user_silver, 'user', '如何用Python实现快速排序？', 'deepseek-v4-flash', 15, NULL),
       (@conv_silver_1, @user_silver, 'assistant',
        '快速排序是一种高效的排序算法。基本思路是选择一个基准元素，将数组分为两部分，然后递归排序。', 'deepseek-v4-flash',
        180, NULL),
       (@conv_silver_1, @user_silver, 'user', '这个算法的空间复杂度是多少？', 'deepseek-v4-flash', 12, NULL),
       (@conv_silver_1, @user_silver, 'assistant',
        '上述实现的空间复杂度是 O(n)，因为每次递归都会创建新的列表。原地排序的版本可以将空间复杂度降低到 O(log n)。',
        'deepseek-v4-flash', 85, NULL),
       (@conv_silver_1, @user_silver, 'user', '能否给出原地排序的实现？', 'deepseek-v4-flash', 10, '[
         "test-file-001-uuid"
       ]'),
       (@conv_silver_1, @user_silver, 'assistant', '原地快速排序实现不需要额外的存储空间，通过交换元素完成排序。',
        'deepseek-v4-flash', 220, '[
         "test-file-001-uuid"
       ]');

-- gold_user 的第一个会话消息
SET @conv_gold_1 = (SELECT id
                    FROM conversation
                    WHERE user_id = (SELECT id FROM user WHERE username = 'gold_user')
                      AND model = 'gpt-4o'
                    ORDER BY create_time DESC
                    LIMIT 1);
SET @user_gold = (SELECT id
                  FROM user
                  WHERE username = 'gold_user');

INSERT INTO chat_message (conversation_id, user_id, role, content, model, tokens)
VALUES (@conv_gold_1, @user_gold, 'user', '如何设计一个高可用的微服务架构？', 'gpt-4o', 12),
       (@conv_gold_1, @user_gold, 'assistant',
        '设计高可用微服务架构需要考虑以下关键点：\n\n1. **服务发现与注册**：使用 Consul、Eureka 或 Nacos\n2. **负载均衡**：客户端或服务端负载均衡\n3. **熔断降级**：Hystrix 或 Resilience4j\n4. **分布式配置中心**：Spring Cloud Config\n5. **链路追踪**：Zipkin 或 SkyWalking',
        'gpt-4o', 280),
       (@conv_gold_1, @user_gold, 'user', '服务间通信应该用REST还是gRPC？', 'gpt-4o', 10),
       (@conv_gold_1, @user_gold, 'assistant',
        '选择建议：\n\n**REST API 适用场景：**对外暴露的API、跨语言调用、调试友好\n\n**gRPC 适用场景：**内部服务间通信、高性能要求、流式数据传输\n\n混合方案：外部用REST，内部用gRPC。',
        'gpt-4o', 165);

-- diamond_user 的第一个会话消息
SET @conv_diamond_1 = (SELECT id
                       FROM conversation
                       WHERE user_id = (SELECT id FROM user WHERE username = 'diamond_user')
                         AND model = 'claude-3-opus'
                       ORDER BY create_time DESC
                       LIMIT 1);
SET @user_diamond = (SELECT id
                     FROM user
                     WHERE username = 'diamond_user');

INSERT INTO chat_message (conversation_id, user_id, role, content, model, tokens)
VALUES (@conv_diamond_1, @user_diamond, 'user', '请帮我设计一个电商平台的订单系统架构', 'claude-3-opus', 15),
       (@conv_diamond_1, @user_diamond, 'assistant',
        '电商平台订单系统架构设计：\n\n## 核心模块\n\n### 1. 订单服务\n- 订单创建、查询、修改、取消\n- 订单状态机管理\n- 分布式事务保证一致性\n\n### 2. 库存服务\n- 库存预占与释放\n- 库存扣减\n- 库存回滚\n\n### 3. 支付服务\n- 多渠道支付接入\n- 支付回调处理\n- 退款流程',
        'claude-3-opus', 450);

-- ============================================
-- 第七部分：点数账单记录（消费和赠送）
-- ============================================

-- silver_user 的消费记录
INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_silver, 'DEDUCT', -1.000, 1200.000, 1199.000, @conv_silver_1, 'AI对话消耗 - deepseek-v4-flash', 0),
       (@user_silver, 'DEDUCT', -1.000, 1199.000, 1198.000, @conv_silver_1, 'AI对话消耗 - deepseek-v4-flash', 0),
       (@user_silver, 'GRANT', 80.000, 1198.000, 1278.000, NULL, '每日赠送点数 - 白银会员', 1),
       (@user_silver, 'GRANT', 80.000, 1278.000, 1358.000, NULL, '每日赠送点数 - 白银会员', 1);

-- gold_user 的消费记录
INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_gold, 'DEDUCT', -5.000, 3000.000, 2995.000, @conv_gold_1, 'AI对话消耗 - gpt-4o', 0),
       (@user_gold, 'DEDUCT', -5.000, 2995.000, 2990.000, @conv_gold_1, 'AI对话消耗 - gpt-4o', 0),
       (@user_gold, 'GRANT', 150.000, 2990.000, 3140.000, NULL, '每日赠送点数 - 黄金会员', 1),
       (@user_gold, 'GRANT', 150.000, 3140.000, 3290.000, NULL, '每日赠送点数 - 黄金会员', 1);

-- diamond_user 的消费记录
INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_diamond, 'DEDUCT', -5.000, 10000.000, 9995.000, @conv_diamond_1, 'AI对话消耗 - claude-3-opus', 0),
       (@user_diamond, 'GRANT', 300.000, 9995.000, 10295.000, NULL, '每日赠送点数 - 钻石会员', 1);

-- free_user 的消费记录
SET @user_free = (SELECT id
                  FROM user
                  WHERE username = 'free_user');
SET @conv_free_1 = (SELECT id
                    FROM conversation
                    WHERE user_id = @user_free
                    LIMIT 1);

INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_free, 'DEDUCT', -1.000, 300.000, 299.000, @conv_free_1, 'AI对话消耗 - deepseek-v4-flash', 0),
       (@user_free, 'GRANT', 30.000, 299.000, 329.000, NULL, '每日赠送点数 - 免费用户', 1);

-- testuser 的消费记录
SET @user_test = (SELECT id
                  FROM user
                  WHERE username = 'testuser');
SET @conv_test_1 = (SELECT id
                    FROM conversation
                    WHERE user_id = @user_test
                    LIMIT 1);

INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_test, 'DEDUCT', -1.000, 1000.000, 999.000, @conv_test_1, 'AI对话消耗 - deepseek-v4-flash', 0);

-- ============================================
-- 第八部分：用户订阅记录
-- ============================================

-- 获取套餐ID
SET @pkg_silver = (SELECT id
                   FROM package_template
                   WHERE package_code = 'silver');
SET @pkg_gold = (SELECT id
                 FROM package_template
                 WHERE package_code = 'gold');
SET @pkg_diamond = (SELECT id
                    FROM package_template
                    WHERE package_code = 'diamond');

-- 插入用户订阅记录（测试数据）
INSERT INTO user_subscription (user_id, package_id, package_code, package_name, identity_label, points_granted,
                               price_paid, start_time, expire_time, status, auto_renew, order_id, coupon_id,
                               discount_amount)
VALUES ((SELECT id FROM user WHERE username = 'silver_user'), @pkg_silver, 'silver', '白银月卡会员', '白银会员', 600,
        15.00, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), 'ACTIVE', 1,
        CONCAT('SUB_', UNIX_TIMESTAMP() * 1000 + 1), NULL, 0),
       ((SELECT id FROM user WHERE username = 'gold_user'), @pkg_gold, 'gold', '黄金进阶会员', '黄金会员', 1800, 80.00,
        DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY), 'ACTIVE', 0,
        CONCAT('SUB_', UNIX_TIMESTAMP() * 1000 + 2), NULL, 0),
       ((SELECT id FROM user WHERE username = 'diamond_user'), @pkg_diamond, 'diamond', '钻石旗舰会员', '钻石会员',
        5000, 420.00, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_ADD(NOW(), INTERVAL 305 DAY), 'ACTIVE', 1,
        CONCAT('SUB_', UNIX_TIMESTAMP() * 1000 + 3), NULL, 0);

-- ============================================
-- 第九部分：优惠券
-- ============================================

SET @user_silver_id = (SELECT id
                       FROM user
                       WHERE username = 'silver_user');
SET @user_gold_id = (SELECT id
                     FROM user
                     WHERE username = 'gold_user');

INSERT INTO coupon (user_id, coupon_code, amount, threshold_amount, applicable_plan, expire_time, used)
VALUES (@user_silver_id, 'WELCOME2024', 30.000, 50.000, 'silver', DATE_ADD(NOW(), INTERVAL 30 DAY), 0),
       (@user_gold_id, 'NEWYEAR2025', 20.000, 100.000, NULL, DATE_ADD(NOW(), INTERVAL 15 DAY), 0);

-- ============================================
-- 第十部分：系统通知
-- ============================================

SET @user_all = (SELECT id
                 FROM user
                 WHERE username = 'testuser');

INSERT INTO system_notification (user_id, title, content, type, is_read)
VALUES (@user_all, '系统维护通知', '系统将于今晚23:00-01:00进行例行维护，期间可能无法访问。', 'SYSTEM', 0),
       (@user_all, '新功能上线', 'AI文档助手功能已上线，支持PDF、Word、Excel等多种格式！', 'FEATURE', 0),
       (@user_all, '优惠活动', '春节特惠活动开启，所有套餐8折优惠！', 'PROMOTION', 1);

-- 统计数据（模拟）
INSERT INTO daily_statistics (stat_date, new_users, total_users, active_users, new_conversations, total_conversations,
                              total_messages, total_input_tokens, total_output_tokens, total_tokens,
                              total_points_consumed, api_call_success, api_call_failed, api_call_total)
VALUES (DATE_SUB(CURDATE(), INTERVAL 1 DAY), 5, 156, 89, 45, 1234, 5678, 125000, 234000, 359000, 456.789, 2345, 23,
        2368),
       (DATE_SUB(CURDATE(), INTERVAL 2 DAY), 3, 151, 76, 38, 1189, 4892, 108000, 198000, 306000, 389.456, 2012, 18,
        2030),
       (DATE_SUB(CURDATE(), INTERVAL 3 DAY), 8, 148, 92, 52, 1151, 6234, 142000, 267000, 409000, 523.123, 2567, 31,
        2598);

INSERT INTO platform_statistics (stat_date, total_users, new_users, active_users, retained_users, total_calls,
                                 total_tokens, total_points_consumed, total_revenue, recharge_amount, package_sales,
                                 avg_order_value)
VALUES (DATE_SUB(CURDATE(), INTERVAL 1 DAY), 156, 5, 89, 78, 2368, 359000, 456.789, 1250.500, 850.000, 23, 36.957),
       (DATE_SUB(CURDATE(), INTERVAL 2 DAY), 151, 3, 76, 65, 2030, 306000, 389.456, 980.300, 650.000, 18, 36.111);

-- 实时计数器初始数据
INSERT INTO statistics_counter (counter_type, counter_value) VALUES
('USER_TOTAL', 0), ('CONVERSATION_TOTAL', 0), ('MESSAGE_TOTAL', 0),
('API_CALL_TOTAL', 0), ('API_CALL_SUCCESS', 0), ('API_CALL_FAILED', 0);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 第十一部分：文件解析任务测试数据（新标准流程）
-- ============================================

INSERT INTO file_parse_task (file_id, user_id, original_name, oss_url, file_size, file_type, parse_status,
                             parsed_text_length, text_summary, upload_time, parse_start_time, parse_end_time, is_used)
VALUES ('test-file-001-uuid',
        (SELECT id FROM user WHERE username = 'testuser' LIMIT 1),
        'test_report.pdf',
        'https://oss.example.com/documents/1/test_report.pdf',
        1024000,
        'pdf',
        'SUCCESS',
        15234,
        '这是一份关于系统架构设计的PDF文档，包含了微服务设计、数据库优化、性能调优等核心内容。',
        NOW(),
        DATE_ADD(NOW(), INTERVAL 1 SECOND),
        DATE_ADD(NOW(), INTERVAL 5 SECOND),
        0),

       ('test-file-002-uuid',
        (SELECT id FROM user WHERE username = 'testuser' LIMIT 1),
        'data_analysis.xlsx',
        'https://oss.example.com/documents/1/data_analysis.xlsx',
        512000,
        'xlsx',
        'SUCCESS',
        8956,
        'Excel数据分析表格，包含销售数据、用户增长趋势、月度报表等关键指标统计信息。',
        NOW(),
        DATE_ADD(NOW(), INTERVAL 1 SECOND),
        DATE_ADD(NOW(), INTERVAL 3 SECOND),
        0);

SELECT '============================================' AS '';
SELECT '  ✅ 数据库重建完成！' AS result;
SELECT '  ' AS '';
SELECT '  📝 测试账户列表（密码均为: Test@123456）：' AS accounts;
SELECT '  - superadmin (超级管理员)' AS account1;
SELECT '  - testuser (测试用户)' AS account2;
SELECT '  - silver_user (白银会员)' AS account3;
SELECT '  - gold_user (黄金会员)' AS account4;
SELECT '  - diamond_user (钻石会员)' AS account5;
SELECT '  - free_user (免费用户)' AS account6;
SELECT '  - disabled_user (禁用用户)' AS account7;
SELECT '  ' AS '';
SELECT '  📊 包含测试数据：' AS data_info;
SELECT '  - 7个测试用户' AS data1;
SELECT '  - 5个API Key' AS data2;
SELECT '  - 15个会话记录' AS data3;
SELECT '  - 10条聊天消息' AS data4;
SELECT '  - 12条点数账单' AS data5;
SELECT '  - 3条用户订阅' AS data6;
SELECT '  - 2张优惠券' AS data7;
SELECT '  - 3条系统通知' AS data8;
SELECT '  - 3天统计数据' AS data9;
SELECT '  - 2个文件解析任务（新）' AS data10;
SELECT '============================================' AS '';

SELECT id, package_code, package_name, status
FROM package_template;