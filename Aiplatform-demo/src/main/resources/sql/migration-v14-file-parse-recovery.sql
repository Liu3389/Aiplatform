-- ============================================================
-- 迁移脚本 v14：文件解析任务租约、fencing token 与有限重试
-- 适用于已运行的业务数据库；不清空历史数据，可重复执行
-- ============================================================

SET NAMES utf8mb4;

SET @has_parse_lease_token = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_parse_task' AND COLUMN_NAME = 'parse_lease_token'
);
SET @ddl = IF(@has_parse_lease_token = 0,
    'ALTER TABLE file_parse_task ADD COLUMN parse_lease_token varchar(64) DEFAULT NULL COMMENT ''当前解析 worker fencing token'' AFTER dispatch_attempts',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_parse_lease_until = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_parse_task' AND COLUMN_NAME = 'parse_lease_until'
);
SET @ddl = IF(@has_parse_lease_until = 0,
    'ALTER TABLE file_parse_task ADD COLUMN parse_lease_until datetime DEFAULT NULL COMMENT ''当前解析租约到期时间'' AFTER parse_lease_token',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_parse_attempts = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_parse_task' AND COLUMN_NAME = 'parse_attempts'
);
SET @ddl = IF(@has_parse_attempts = 0,
    'ALTER TABLE file_parse_task ADD COLUMN parse_attempts int NOT NULL DEFAULT 0 COMMENT ''解析 worker 实际领取次数'' AFTER parse_lease_until',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_parse_lease_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_parse_task' AND INDEX_NAME = 'idx_parse_lease'
);
SET @ddl = IF(@has_parse_lease_index = 0,
    'CREATE INDEX idx_parse_lease ON file_parse_task (parse_status, parse_lease_until, id)',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_parse_dispatch_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_parse_task' AND INDEX_NAME = 'idx_parse_dispatch'
);
SET @ddl = IF(@has_parse_dispatch_index = 0,
    'CREATE INDEX idx_parse_dispatch ON file_parse_task (parse_status, last_dispatch_at, id)',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SELECT 'v14 文件解析任务租约与重启恢复迁移完成' AS result;
