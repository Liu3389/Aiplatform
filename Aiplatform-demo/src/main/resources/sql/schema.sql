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
-- 模型配置：免费 DeepSeek 模型
-- ============================================
INSERT INTO `model_config` (`model_name`, `provider`, `base_url`, `api_key`, `input_price`, `output_price`, `status`) VALUES ('deepseek-v4-pro',
                                                                                                                              'deepseek',
                                                                                                                              'https://api.deepseek.com/v1/chat/completions',
                                                                                                                              'REDACTED_PROVIDER_API_KEY',
                                                                                                                              0.000000,
                                                                                                                              0.000000,
                                                                                                                              1),
                                                                                                                             ('deepseek-v4-flash',
                                                                                                                              'deepseek',
                                                                                                                              'https://api.deepseek.com/v1/chat/completions',
                                                                                                                              'REDACTED_PROVIDER_API_KEY',
                                                                                                                              0.000000,
                                                                                                                              0.000000,
                                                                                                                              1),
('gpt-3.5-turbo', 'openai', 'https://api.openai.com/v1/chat/completions', 'sk-your-openai-api-key', 0.001000, 0.002000, 1),
('gpt-4o', 'openai', 'https://api.openai.com/v1/chat/completions', 'sk-your-openai-api-key', 0.005000, 0.015000, 1);
