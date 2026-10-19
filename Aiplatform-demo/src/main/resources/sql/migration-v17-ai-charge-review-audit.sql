-- Durable audit for evidence-based resolution of ambiguous AI upstream charges.
CREATE TABLE IF NOT EXISTS `ai_charge_review_audit`
(
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(64) NOT NULL COMMENT 'AI请求幂等键',
  `user_id` bigint NOT NULL COMMENT '被处理的用户ID',
  `admin_id` bigint NOT NULL COMMENT '执行核验的超级管理员ID',
  `action` varchar(16) NOT NULL COMMENT '处理方向：REFUND/SETTLE',
  `actual_points` decimal(10,3) DEFAULT NULL COMMENT '上游确认计费时的实际点数',
  `evidence` varchar(1000) NOT NULL COMMENT '供应商日志引用与核验结论，不存储密钥',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/RESOLVED/FAILED',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次重试说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次登记时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
  `completed_at` datetime DEFAULT NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_charge_review_request` (`request_id`),
  KEY `idx_ai_charge_review_status_time` (`status`, `update_time`),
  KEY `idx_ai_charge_review_admin_time` (`admin_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI点数人工对账审计记录';

SELECT 'v17 AI 人工对账审计表创建完成' AS result;
