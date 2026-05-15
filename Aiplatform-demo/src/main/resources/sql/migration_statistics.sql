-- ============================================
-- 管理员后台统计系统 - 数据库迁移脚本
-- 创建时间: 2026-05-15
-- 说明: 创建统计表和计数器表，避免实时全表扫描
-- ============================================

USE ai_gateway;

-- ============================================
-- 1. 每日统计表（核心统计表）
-- ============================================
CREATE TABLE IF NOT EXISTS `daily_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  
  -- 用户统计
  `new_users` int NOT NULL DEFAULT 0 COMMENT '新增用户数',
  `total_users` int NOT NULL DEFAULT 0 COMMENT '累计用户总数',
  `active_users` int NOT NULL DEFAULT 0 COMMENT '活跃用户数（有对话的用户）',
  
  -- 对话统计
  `new_conversations` int NOT NULL DEFAULT 0 COMMENT '新增会话数',
  `total_conversations` int NOT NULL DEFAULT 0 COMMENT '累计会话总数',
  `total_messages` int NOT NULL DEFAULT 0 COMMENT '新增消息数',
  `total_message_count` bigint NOT NULL DEFAULT 0 COMMENT '累计消息总数',
  
  -- Token统计
  `total_input_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输入Token总数',
  `total_output_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输出Token总数',
  `total_tokens` bigint NOT NULL DEFAULT 0 COMMENT 'Token总数',
  
  -- 点数统计
  `total_points_consumed` decimal(12,3) NOT NULL DEFAULT 0.000 COMMENT '点数消耗总数',
  
  -- API调用统计
  `api_call_success` int NOT NULL DEFAULT 0 COMMENT 'API调用成功次数',
  `api_call_failed` int NOT NULL DEFAULT 0 COMMENT 'API调用失败次数',
  `api_call_total` int NOT NULL DEFAULT 0 COMMENT 'API调用总次数',
  
  -- 模型使用统计（JSON格式存储各模型使用次数）
  `model_usage` json DEFAULT NULL COMMENT '模型使用统计 {\"gpt-4o\": 100, \"deepseek-v4\": 200}',
  
  -- 时段分布（JSON格式存储24小时分布）
  `hourly_distribution` json DEFAULT NULL COMMENT '小时分布 {\"0\": 10, \"1\": 5, ...}',
  
  -- 错误统计
  `error_count` int NOT NULL DEFAULT 0 COMMENT '错误次数',
  `timeout_count` int NOT NULL DEFAULT 0 COMMENT '超时次数',
  
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日统计表';

-- ============================================
-- 2. 实时计数器表（用于快速累加）
-- ============================================
CREATE TABLE IF NOT EXISTS `statistics_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counter_type` varchar(32) NOT NULL COMMENT '计数器类型：USER_TOTAL/CONVERSATION_TOTAL/MESSAGE_TOTAL等',
  `counter_value` bigint NOT NULL DEFAULT 0 COMMENT '计数值',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_counter_type` (`counter_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实时计数器表';

-- 初始化计数器
INSERT INTO `statistics_counter` (`counter_type`, `counter_value`) VALUES
('USER_TOTAL', 0),
('CONVERSATION_TOTAL', 0),
('MESSAGE_TOTAL', 0),
('API_CALL_TOTAL', 0),
('API_CALL_SUCCESS', 0),
('API_CALL_FAILED', 0)
ON DUPLICATE KEY UPDATE counter_value = counter_value;

-- ============================================
-- 3. 用户留存统计表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_retention_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_date` date NOT NULL COMMENT '统计日期（用户注册日期）',
  `new_users` int NOT NULL DEFAULT 0 COMMENT '当日新增用户数',
  `retained_day1` int NOT NULL DEFAULT 0 COMMENT '次日留存用户数',
  `retained_day7` int NOT NULL DEFAULT 0 COMMENT '7日留存用户数',
  `retained_day30` int NOT NULL DEFAULT 0 COMMENT '30日留存用户数',
  `retention_rate_day1` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '次日留存率',
  `retention_rate_day7` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '7日留存率',
  `retention_rate_day30` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '30日留存率',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户留存统计表';

-- ============================================
-- 4. 热门问题统计表
-- ============================================
CREATE TABLE IF NOT EXISTS `hot_questions_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `question_hash` varchar(64) NOT NULL COMMENT '问题哈希值（去重）',
  `question_preview` varchar(255) NOT NULL COMMENT '问题预览（前100字符）',
  `occurrence_count` int NOT NULL DEFAULT 0 COMMENT '出现次数',
  `unique_users` int NOT NULL DEFAULT 0 COMMENT '提问的唯一用户数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_occurrence` (`occurrence_count`),
  UNIQUE KEY `uk_date_question` (`stat_date`, `question_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热门问题统计表';

-- ============================================
-- 5. 内容安全统计表
-- ============================================
CREATE TABLE IF NOT EXISTS `content_safety_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `sensitive_word_trigger` int NOT NULL DEFAULT 0 COMMENT '敏感词触发次数',
  `violation_report` int NOT NULL DEFAULT 0 COMMENT '违规举报数',
  `violation_handled` int NOT NULL DEFAULT 0 COMMENT '已处理违规数',
  `blocked_messages` int NOT NULL DEFAULT 0 COMMENT '被拦截消息数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容安全统计表';

-- ============================================
-- 6. 为现有表添加索引优化（如果不存在）
-- ============================================

-- user表索引
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_create_time` (`create_time`);
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_last_login_time` (`last_login_time`);
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_status` (`status`);

-- conversation表索引
ALTER TABLE `conversation` ADD INDEX IF NOT EXISTS `idx_create_time` (`create_time`);
ALTER TABLE `conversation` ADD INDEX IF NOT EXISTS `idx_user_create` (`user_id`, `create_time`);

-- chat_message表索引
ALTER TABLE `chat_message` ADD INDEX IF NOT EXISTS `idx_role` (`role`);

-- call_log表索引
ALTER TABLE `call_log` ADD INDEX IF NOT EXISTS `idx_model` (`model`);
ALTER TABLE `call_log` ADD INDEX IF NOT EXISTS `idx_status_time` (`status`, `create_time`);

-- ============================================
-- 7. 验证表结构
-- ============================================
SELECT '========================================' AS '';
SELECT '管理员后台统计系统数据库迁移完成！' AS message;
SELECT '========================================' AS '';

SELECT '新增表:' AS info;
SELECT TABLE_NAME, TABLE_COMMENT 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'ai_gateway' 
AND TABLE_NAME IN ('daily_statistics', 'statistics_counter', 'user_retention_statistics', 'hot_questions_statistics', 'content_safety_statistics')
ORDER BY TABLE_NAME;

SELECT '========================================' AS '';
SELECT '请重启应用以加载新功能' AS note;
SELECT '========================================' AS '';
