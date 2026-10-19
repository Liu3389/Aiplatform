-- ============================================================
-- 迁移脚本 v6：AI 点数结算请求幂等键与本地消息表
-- 适用于已运行的业务数据库；不清空历史数据，可重复执行
-- ============================================================

USE ai_gateway;
SET NAMES utf8mb4;

SET @has_request_id = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_record' AND COLUMN_NAME = 'request_id'
);
SET @ddl = IF(@has_request_id = 0,
    'ALTER TABLE billing_record ADD COLUMN request_id varchar(64) DEFAULT NULL COMMENT ''AI请求幂等键；充值流水为空'' AFTER call_log_id',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_request_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_record' AND INDEX_NAME = 'uk_billing_request_id'
);
SET @ddl = IF(@has_request_index = 0,
    'CREATE UNIQUE INDEX uk_billing_request_id ON billing_record (request_id)',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_dispatch_time = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_parse_task' AND COLUMN_NAME = 'last_dispatch_at'
);
SET @ddl = IF(@has_dispatch_time = 0,
    'ALTER TABLE file_parse_task ADD COLUMN last_dispatch_at datetime DEFAULT NULL COMMENT ''最近 Rabbit 发布租约时间'' AFTER parse_end_time',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_dispatch_attempts = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_parse_task' AND COLUMN_NAME = 'dispatch_attempts'
);
SET @ddl = IF(@has_dispatch_attempts = 0,
    'ALTER TABLE file_parse_task ADD COLUMN dispatch_attempts int NOT NULL DEFAULT 0 COMMENT ''解析任务发布尝试次数'' AFTER last_dispatch_at',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

CREATE TABLE IF NOT EXISTS `billing_settlement_outbox`
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

SELECT 'v6 AI结算幂等、outbox与文件任务投递迁移完成' AS result;
