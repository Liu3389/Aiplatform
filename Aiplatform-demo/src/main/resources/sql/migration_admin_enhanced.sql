-- ============================================
-- 管理员后台增强功能数据库迁移脚本
-- 创建时间: 2026-05-14
-- 说明: 添加操作日志、统计报表、通知系统等表结构
-- ============================================

USE ai_gateway;

-- ============================================
-- 1. 管理员操作日志表
-- ============================================
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `admin_id` bigint NOT NULL COMMENT '管理员ID',
  `admin_username` varchar(64) NOT NULL COMMENT '管理员用户名',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型：USER_MANAGE/POINTS_MANAGE/MEMBERSHIP_MANAGE/BATCH_OPERATION/SYSTEM_CONFIG',
  `operation_action` varchar(64) NOT NULL COMMENT '操作动作：BAN/UNBAN/RECHARGE/GRANT_POINTS/ASSIGN_ROLE等',
  `target_user_ids` text COMMENT '目标用户ID列表（JSON数组）',
  `operation_detail` text COMMENT '操作详情（JSON格式）',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '操作IP地址',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '浏览器标识',
  `result` varchar(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果：SUCCESS/FAILED',
  `error_message` text COMMENT '错误信息（失败时记录）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- ============================================
-- 2. 平台统计数据表（每日统计）
-- ============================================
CREATE TABLE IF NOT EXISTS `platform_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `total_users` int NOT NULL DEFAULT 0 COMMENT '总用户数',
  `new_users` int NOT NULL DEFAULT 0 COMMENT '新增用户数',
  `active_users` int NOT NULL DEFAULT 0 COMMENT '活跃用户数',
  `retained_users` int NOT NULL DEFAULT 0 COMMENT '留存用户数',
  `total_calls` bigint NOT NULL DEFAULT 0 COMMENT '总对话次数',
  `total_tokens` bigint NOT NULL DEFAULT 0 COMMENT '总Token消耗',
  `total_points_consumed` decimal(12,3) NOT NULL DEFAULT 0.000 COMMENT '总点数消耗',
  `total_revenue` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '总营收金额',
  `recharge_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '充值金额',
  `package_sales` int NOT NULL DEFAULT 0 COMMENT '套餐销量',
  `avg_order_value` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '平均客单价',
  `model_usage` json DEFAULT NULL COMMENT '各模型使用量（JSON格式）',
  `hourly_traffic` json DEFAULT NULL COMMENT '小时流量分布（JSON格式）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台统计数据表';

-- ============================================
-- 3. 系统公告表
-- ============================================
CREATE TABLE IF NOT EXISTS `system_announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(128) NOT NULL COMMENT '公告标题',
  `content` text NOT NULL COMMENT '公告内容',
  `type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '公告类型：SYSTEM-系统公告, ACTIVITY-活动公告, MAINTENANCE-维护公告',
  `priority` tinyint NOT NULL DEFAULT 0 COMMENT '优先级：0-普通, 1-重要, 2-紧急',
  `target_users` text COMMENT '目标用户ID列表（JSON数组，NULL表示全体用户）',
  `publish_time` datetime NOT NULL COMMENT '发布时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间（NULL表示永久有效）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待发布, 1-已发布, 2-已下架',
  `created_by` bigint NOT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告表';

-- ============================================
-- 4. 用户通知表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` text NOT NULL COMMENT '通知内容',
  `type` varchar(32) NOT NULL COMMENT '通知类型：RECHARGE-充值到账, POINTS_GRANT-点数发放, MEMBERSHIP_EXPIRE-会员到期, ANNOUNCEMENT-系统公告, SYSTEM-系统通知',
  `related_id` bigint DEFAULT NULL COMMENT '关联ID（如订单ID、公告ID等）',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读, 1-已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';

-- ============================================
-- 5. 为user表添加缺失字段
-- ============================================
-- 检查并添加points字段（如果不存在）
ALTER TABLE `user` 
ADD COLUMN IF NOT EXISTS `points` decimal(10,3) NOT NULL DEFAULT 100.000 COMMENT '点数余额（保留三位小数）' AFTER `balance`;

-- 添加membership_expire_time字段
ALTER TABLE `user` 
ADD COLUMN IF NOT EXISTS `membership_expire_time` datetime DEFAULT NULL COMMENT '会员有效期' AFTER `membership`;

-- 添加daily_free_count字段
ALTER TABLE `user` 
ADD COLUMN IF NOT EXISTS `daily_free_count` int NOT NULL DEFAULT 10 COMMENT '每日免费次数' AFTER `monthly_call_limit`;

-- 添加last_login_time字段
ALTER TABLE `user` 
ADD COLUMN IF NOT EXISTS `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间' AFTER `update_time`;

-- ============================================
-- 6. 初始化默认数据
-- ============================================

-- 插入示例公告
INSERT INTO `system_announcement` (`title`, `content`, `type`, `priority`, `publish_time`, `expire_time`, `status`, `created_by`) VALUES
('欢迎使用凌点AI平台', '感谢您使用凌点AI模型调度网关！我们提供多种AI模型接入服务，包括DeepSeek、GPT-4、Claude等。如有问题请联系客服。', 'SYSTEM', 1, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1, 1),
('系统维护通知', '为了提供更好的服务，我们将于本周日凌晨2:00-4:00进行系统维护，期间可能影响部分功能使用，敬请谅解。', 'MAINTENANCE', 2, DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), 0, 1);

-- ============================================
-- 7. 验证表结构
-- ============================================
SELECT '========================================' AS '';
SELECT '管理员后台增强功能数据库迁移完成！' AS message;
SELECT '========================================' AS '';

SELECT '新增表:' AS info;
SELECT TABLE_NAME, TABLE_COMMENT 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'ai_gateway' 
AND TABLE_NAME IN ('admin_operation_log', 'platform_statistics', 'system_announcement', 'user_notification')
ORDER BY TABLE_NAME;

SELECT '========================================' AS '';
SELECT '请重启应用以加载新功能' AS note;
SELECT '========================================' AS '';
