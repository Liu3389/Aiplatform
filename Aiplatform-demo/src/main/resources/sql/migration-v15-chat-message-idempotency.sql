-- ============================================================
-- 迁移脚本 v15：聊天消息幂等持久化与结算状态
-- 保留现有消息和收费记录，可重复执行
-- ============================================================

SET NAMES utf8mb4;

SET @has_chat_message_request_id = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message' AND COLUMN_NAME = 'request_id'
);
SET @ddl = IF(@has_chat_message_request_id = 0,
    'ALTER TABLE chat_message ADD COLUMN request_id varchar(64) DEFAULT NULL COMMENT ''AI 请求幂等键'' AFTER user_id',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_chat_history_request_id = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message_history' AND COLUMN_NAME = 'request_id'
);
SET @ddl = IF(@has_chat_history_request_id = 0,
    'ALTER TABLE chat_message_history ADD COLUMN request_id varchar(64) DEFAULT NULL COMMENT ''AI 请求幂等键'' AFTER user_id',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_history_thinking_time = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message_history' AND COLUMN_NAME = 'thinking_time'
);
SET @ddl = IF(@has_history_thinking_time = 0,
    'ALTER TABLE chat_message_history ADD COLUMN thinking_time double DEFAULT NULL COMMENT ''思考耗时（秒）'' AFTER metadata',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_hot_request_role_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message' AND INDEX_NAME = 'uk_chat_message_request_role'
);
SET @ddl = IF(@has_hot_request_role_index = 0,
    'CREATE UNIQUE INDEX uk_chat_message_request_role ON chat_message (request_id, role)',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_history_request_role_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message_history' AND INDEX_NAME = 'idx_chat_history_request_role'
);
SET @ddl = IF(@has_history_request_role_index = 0,
    'CREATE INDEX idx_chat_history_request_role ON chat_message_history (request_id, role)',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_intent_fingerprint = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_charge_intent' AND COLUMN_NAME = 'request_fingerprint'
);
SET @ddl = IF(@has_intent_fingerprint = 0,
    'ALTER TABLE ai_charge_intent ADD COLUMN request_fingerprint char(64) DEFAULT NULL COMMENT ''客户端幂等请求内容 SHA-256 摘要'' AFTER request_id',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SELECT 'v15 聊天消息幂等持久化迁移完成' AS result;
