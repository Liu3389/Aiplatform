-- Migration v16: add a per-model output-token capability cap.
-- Existing models receive a conservative platform default; the cap can be
-- adjusted per model after its provider/model capability is verified.
SET NAMES utf8mb4;

SET @has_model_output_token_cap = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'platform_model_config' AND COLUMN_NAME = 'max_output_tokens'
);
SET @ddl = IF(@has_model_output_token_cap = 0,
    'ALTER TABLE platform_model_config ADD COLUMN max_output_tokens int NOT NULL DEFAULT 4096 COMMENT ''平台单模型输出Token上限'' AFTER supports_vision',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

UPDATE platform_model_config SET max_output_tokens = 4096 WHERE max_output_tokens IS NULL OR max_output_tokens <= 0;
UPDATE platform_model_config SET max_output_tokens = 32768 WHERE max_output_tokens > 32768;

SELECT 'v16 模型输出Token上限迁移完成' AS result;
