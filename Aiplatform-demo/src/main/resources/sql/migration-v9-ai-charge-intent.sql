-- AI 请求点数预扣生命周期持久化；幂等创建，适用于已部署过的 v1 数据库。
CREATE TABLE IF NOT EXISTS `ai_charge_intent`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(64) NOT NULL COMMENT 'AI请求幂等键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `reserved_points` decimal(10,3) NOT NULL COMMENT '请求开始前预扣点数',
  `actual_points` decimal(10,3) NOT NULL COMMENT '响应后确认的结算点数',
  `model_name` varchar(100) NOT NULL COMMENT '模型展示名',
  `status` varchar(24) NOT NULL COMMENT '请求状态：PREPARING/RESERVED/IN_FLIGHT/RESPONSE_RECEIVED/SETTLEMENT_PENDING/REFUND_PENDING/SETTLED/REFUNDED/MANUAL_REVIEW',
  `reservation_confirmed` tinyint NOT NULL DEFAULT 0 COMMENT '是否已确认 Redis 预扣',
  `upstream_started_at` datetime DEFAULT NULL COMMENT '开始调用上游时间',
  `response_received_at` datetime DEFAULT NULL COMMENT '收到完整上游响应时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
  `recovery_claimed_at` datetime DEFAULT NULL COMMENT '恢复扫描租约时间',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近恢复或失败原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_charge_request_id` (`request_id`),
  KEY `idx_ai_charge_recovery` (`status`, `update_time`, `recovery_claimed_at`),
  KEY `idx_ai_charge_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI请求点数预扣与恢复状态';

SELECT 'v9 AI 请求点数预扣状态表创建完成' AS result;
