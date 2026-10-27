-- Add the provider avatar URL needed to render third-party profile imagery.
SET @has_avatar_url = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_oauth_identity'
    AND COLUMN_NAME = 'avatar_url'
);
SET @avatar_url_ddl = IF(
  @has_avatar_url = 0,
  'ALTER TABLE `user_oauth_identity` ADD COLUMN `avatar_url` varchar(500) DEFAULT NULL COMMENT ''第三方头像 HTTPS 地址'' AFTER `display_name`',
  'SELECT 1'
);
PREPARE avatar_url_stmt FROM @avatar_url_ddl;
EXECUTE avatar_url_stmt;
DEALLOCATE PREPARE avatar_url_stmt;

SELECT 'v18 OAuth 头像字段检查完成' AS result;
