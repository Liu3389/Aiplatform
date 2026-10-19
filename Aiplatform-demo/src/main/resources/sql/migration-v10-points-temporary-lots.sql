-- Tracks the unspent part of each expiring grant; existing point balances are not backfilled
-- because historical aggregate consumption cannot be attributed to temporary/permanent lots safely.
ALTER TABLE `points_bill` MODIFY COLUMN `change_type` varchar(32) NOT NULL
  COMMENT '变动类型：GRANT/DAILY_GRANT/DAILY_PERMANENT_GRANT/DEDUCT/SETTLE/ROLLBACK/EXPIRE';

CREATE TABLE IF NOT EXISTS `points_temporary_lot`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `business_id` varchar(100) NOT NULL COMMENT '每日发放幂等业务ID',
  `granted_points` decimal(10,3) NOT NULL COMMENT '发放点数',
  `remaining_points` decimal(10,3) NOT NULL COMMENT '尚未消费且未过期点数',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DEPLETED/EXPIRED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expired_at` datetime DEFAULT NULL COMMENT '实际过期入账时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_temp_user_business` (`user_id`,`business_id`),
  KEY `idx_points_temp_expiry` (`status`,`expire_time`,`user_id`),
  KEY `idx_points_temp_user_status` (`user_id`,`status`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限时点数剩余余额';

SELECT 'v10 限时点数余额表创建完成' AS result;
