CREATE TABLE IF NOT EXISTS `user_two_factor`
(
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `secret_ciphertext` varchar(255) DEFAULT NULL COMMENT 'AES-GCM加密的TOTP密钥',
  `pending_secret_ciphertext` varchar(255) DEFAULT NULL COMMENT '待确认的AES-GCM加密TOTP密钥',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已完成启用验证',
  `last_totp_step` bigint NOT NULL DEFAULT -1 COMMENT '最近一次消费的TOTP时间步，防止重放',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_two_factor_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户两步验证配置';

CREATE TABLE IF NOT EXISTS `user_two_factor_backup_code`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `code_hash` char(64) NOT NULL COMMENT '高熵备用码SHA-256摘要',
  `consumed_at` datetime DEFAULT NULL COMMENT '消费时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_two_factor_backup_hash` (`user_id`,`code_hash`),
  KEY `idx_two_factor_backup_unused` (`user_id`,`consumed_at`),
  CONSTRAINT `fk_two_factor_backup_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户两步验证一次性备用码';

SELECT 'v11 用户两步验证表创建完成' AS result;
