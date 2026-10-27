-- v20: keep the login username stable while allowing administrators and users to manage a display nickname.
SET @has_user_nickname = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'nickname'
);
SET @user_nickname_ddl = IF(
  @has_user_nickname = 0,
  'ALTER TABLE `user` ADD COLUMN `nickname` varchar(64) DEFAULT NULL COMMENT ''用户展示昵称'' AFTER `username`',
  'SELECT 1'
);
PREPARE user_nickname_stmt FROM @user_nickname_ddl;
EXECUTE user_nickname_stmt;
DEALLOCATE PREPARE user_nickname_stmt;

SELECT 'v20 用户昵称字段检查完成' AS result;
