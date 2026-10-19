CREATE TABLE IF NOT EXISTS `user_oauth_identity`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '平台用户ID',
  `provider` varchar(32) NOT NULL COMMENT 'OAuth/OIDC 提供方',
  `provider_subject` varchar(255) NOT NULL COMMENT '提供方稳定用户标识',
  `display_name` varchar(255) DEFAULT NULL COMMENT '展示名称',
  `email` varchar(255) DEFAULT NULL COMMENT '提供方已验证邮箱',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_provider_subject` (`provider`,`provider_subject`),
  UNIQUE KEY `uk_oauth_user_provider` (`user_id`,`provider`),
  CONSTRAINT `fk_oauth_identity_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户第三方身份绑定';

SELECT 'v12 OAuth 身份表创建完成' AS result;
