-- 系统通知表
CREATE TABLE IF NOT EXISTS `system_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `content` varchar(1024) DEFAULT NULL,
  `type` varchar(32) DEFAULT 'system',
  `is_read` tinyint(1) DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_unread` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 代金卷表
CREATE TABLE IF NOT EXISTS `coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `coupon_code` varchar(32) NOT NULL,
  `amount` decimal(10,4) NOT NULL DEFAULT 0 COMMENT '代金卷面额(元)',
  `threshold_amount` decimal(10,4) NOT NULL DEFAULT 0 COMMENT '使用门槛(元),0=无门槛',
  `applicable_plan` varchar(32) DEFAULT NULL COMMENT '适用套餐, NULL=通用',
  `expire_time` datetime DEFAULT NULL,
  `used` tinyint(1) DEFAULT 0,
  `used_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_coupon_code` (`coupon_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
