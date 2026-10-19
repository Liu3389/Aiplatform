SET @add_user_phone = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `phone` varchar(32) DEFAULT NULL COMMENT ''已验证手机号'' AFTER `email`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'phone'
);
PREPARE add_user_phone_stmt FROM @add_user_phone;
EXECUTE add_user_phone_stmt;
DEALLOCATE PREPARE add_user_phone_stmt;

CREATE TABLE IF NOT EXISTS `user_contact_verification`
(
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `email` varchar(128) DEFAULT NULL COMMENT '经验证码验证的邮箱',
  `phone` varchar(32) DEFAULT NULL COMMENT '经验证码验证的手机号',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '验证更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_verified_contact_email` (`email`),
  UNIQUE KEY `uk_verified_contact_phone` (`phone`),
  CONSTRAINT `fk_verified_contact_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户已验证联系方式';

SELECT 'v13 已验证联系方式结构就绪；旧邮箱未被自动视为已验证' AS result;
