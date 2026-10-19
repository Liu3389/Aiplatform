-- 为点数余额增加单调递增版本，结算重放时避免旧 Redis 快照覆盖后续余额变更。
SET @has_points_version = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'points_version'
);
SET @ddl = IF(@has_points_version = 0,
    'ALTER TABLE user ADD COLUMN points_version bigint NOT NULL DEFAULT 0 COMMENT ''点数余额版本，用于防止结算重放覆盖新余额'' AFTER points',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SELECT 'v8 点数结算余额版本迁移完成' AS result;
