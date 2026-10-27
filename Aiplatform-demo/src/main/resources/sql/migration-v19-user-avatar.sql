-- v19: support user-selected avatars while retaining OAuth avatars as fallback.
SET @has_user_avatar_url = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'avatar_url'
);
SET @user_avatar_url_ddl = IF(
  @has_user_avatar_url = 0,
  'ALTER TABLE `user` ADD COLUMN `avatar_url` varchar(500) DEFAULT NULL COMMENT ''自定义头像存储引用'' AFTER `username`',
  'SELECT 1'
);
PREPARE user_avatar_url_stmt FROM @user_avatar_url_ddl;
EXECUTE user_avatar_url_stmt;
DEALLOCATE PREPARE user_avatar_url_stmt;
