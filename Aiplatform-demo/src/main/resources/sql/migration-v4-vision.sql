-- ============================================================
-- 迁移脚本 v4：DeepSeek-V4-Flash-Vision-Exp 视觉模型
-- 适用于已存在的业务数据库，可重复执行；数据库由 mysql 命令选择
-- API 地址和密钥复用已配置的 DeepSeek-V4-Flash，不在迁移脚本中重复写入密钥
-- ============================================================

-- 中文配置必须按 utf8mb4 发送给 MySQL，避免视觉模型描述被写成 mojibake。
SET NAMES utf8mb4;

-- 兼容已部署的旧版聊天表：消息实体会记录 AI 思考耗时。
SET @thinking_time_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'chat_message'
                               AND COLUMN_NAME = 'thinking_time');
SET @thinking_time_ddl = IF(@thinking_time_exists = 0,
                            'ALTER TABLE chat_message ADD COLUMN thinking_time double DEFAULT NULL COMMENT ''思考耗时（秒）'' AFTER metadata',
                            'SELECT 1');
PREPARE thinking_time_stmt FROM @thinking_time_ddl;
EXECUTE thinking_time_stmt;
DEALLOCATE PREPARE thinking_time_stmt;

-- 兼容还未执行 v3 的旧库：视觉模型也依赖模型描述字段。
SET @description_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE()
                             AND TABLE_NAME = 'platform_model_config'
                             AND COLUMN_NAME = 'description');
SET @description_ddl = IF(@description_exists = 0,
                          'ALTER TABLE platform_model_config ADD COLUMN description varchar(255) DEFAULT NULL COMMENT ''模型描述（模型商店展示）'' AFTER provider',
                          'SELECT 1');
PREPARE description_stmt FROM @description_ddl;
EXECUTE description_stmt;
DEALLOCATE PREPARE description_stmt;

-- 兼容旧版计费表：余额充值没有关联的调用日志，call_log_id 必须允许为空。
SET @billing_call_log_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                                WHERE TABLE_SCHEMA = DATABASE()
                                  AND TABLE_NAME = 'billing_record'
                                  AND COLUMN_NAME = 'call_log_id');
SET @billing_ddl = IF(@billing_call_log_exists = 0,
                      'SELECT 1',
                      'ALTER TABLE billing_record MODIFY COLUMN call_log_id bigint NULL DEFAULT NULL COMMENT ''关联的调用日志ID（充值记录无关联）''');
PREPARE billing_stmt FROM @billing_ddl;
EXECUTE billing_stmt;
DEALLOCATE PREPARE billing_stmt;

INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, description, base_url, api_key,
                                   supports_vision, status, sort_order, access_level)
SELECT 'DeepSeek-V4-Flash-Vision-Exp',
       'deepseek-flash',
       2.000,
       'deepseek',
       'DeepSeek 视觉理解模型，支持截图、图表和图片问答',
       base_model.base_url,
       base_model.api_key,
       1,
       1,
       16,
       1
FROM platform_model_config base_model
WHERE base_model.actual_model IN ('deepseek-v4-flash', 'deepseek-flash')
  AND NOT EXISTS (
      SELECT 1
      FROM platform_model_config existing_model
      WHERE existing_model.display_name = 'DeepSeek-V4-Flash-Vision-Exp'
  )
LIMIT 1;
