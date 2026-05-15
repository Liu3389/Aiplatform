-- ========================================
-- 数据库全面优化脚本
-- 执行日期: 2026-05-15
-- 说明: 索引优化、表结构完善、数据一致性检查
-- ========================================

-- 1. 添加缺失的索引（提升查询性能）

-- user表索引优化
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_username` (`username`);
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_email` (`email`);
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_membership` (`membership`);
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_status` (`status`);
ALTER TABLE `user` ADD INDEX IF NOT EXISTS `idx_create_time` (`create_time`);

-- conversation表复合索引（游标分页优化）
ALTER TABLE `conversation` ADD INDEX IF NOT EXISTS `idx_user_deleted_time` (`user_id`, `is_deleted`, `update_time`);

-- call_log表索引优化
ALTER TABLE `call_log` ADD INDEX IF NOT EXISTS `idx_user_model` (`user_id`, `model`);
ALTER TABLE `call_log` ADD INDEX IF NOT EXISTS `idx_create_time` (`create_time`);
ALTER TABLE `call_log` ADD INDEX IF NOT EXISTS `idx_status` (`status`);

-- points_bill表索引优化
ALTER TABLE `points_bill` ADD INDEX IF NOT EXISTS `idx_user_type` (`user_id`, `type`);
ALTER TABLE `points_bill` ADD INDEX IF NOT EXISTS `idx_expire_time` (`expire_time`);
ALTER TABLE `points_bill` ADD INDEX IF NOT EXISTS `idx_create_time` (`create_time`);

-- coupon_usage_record表索引优化
ALTER TABLE `coupon_usage_record` ADD INDEX IF NOT EXISTS `idx_user_coupon` (`user_id`, `coupon_id`);
ALTER TABLE `coupon_usage_record` ADD INDEX IF NOT EXISTS `idx_order` (`order_type`, `order_id`);
ALTER TABLE `coupon_usage_record` ADD INDEX IF NOT EXISTS `idx_create_time` (`create_time`);

-- user_subscription表索引优化
ALTER TABLE `user_subscription` ADD INDEX IF NOT EXISTS `idx_user_status` (`user_id`, `status`);
ALTER TABLE `user_subscription` ADD INDEX IF NOT EXISTS `idx_package` (`package_id`);
ALTER TABLE `user_subscription` ADD INDEX IF NOT EXISTS `idx_expire_time` (`expire_time`);

-- chat_message表索引优化
ALTER TABLE `chat_message` ADD INDEX IF NOT EXISTS `idx_conversation` (`conversation_id`);
ALTER TABLE `chat_message` ADD INDEX IF NOT EXISTS `idx_create_time` (`create_time`);

-- 2. 更新模型配置（点数结算）
UPDATE `model_config` 
SET `points_per_request` = 1.000,
    `points_mode` = 'STANDARD'
WHERE `model_name` = 'deepseek-v4-flash';

UPDATE `model_config` 
SET `points_per_request` = 3.000,
    `points_mode` = 'PREMIUM'
WHERE `model_name` = 'deepseek-v4-pro';

-- 3. 数据一致性检查

-- 检查孤儿记录（user_id不存在于user表）
SELECT 'orphan_call_logs' as issue, COUNT(*) as count 
FROM call_log cl 
LEFT JOIN user u ON cl.user_id = u.id 
WHERE u.id IS NULL;

SELECT 'orphan_points_bills' as issue, COUNT(*) as count 
FROM points_bill pb 
LEFT JOIN user u ON pb.user_id = u.id 
WHERE u.id IS NULL;

SELECT 'orphan_conversations' as issue, COUNT(*) as count 
FROM conversation c 
LEFT JOIN user u ON c.user_id = u.id 
WHERE u.id IS NULL;

-- 4. 清理无效数据（可选，谨慎执行）
-- DELETE FROM call_log WHERE user_id NOT IN (SELECT id FROM user);
-- DELETE FROM points_bill WHERE user_id NOT IN (SELECT id FROM user);

-- 5. 验证索引创建结果
SHOW INDEX FROM user;
SHOW INDEX FROM conversation;
SHOW INDEX FROM call_log;
SHOW INDEX FROM points_bill;

-- 6. 优化表统计信息
ANALYZE TABLE user;
ANALYZE TABLE conversation;
ANALYZE TABLE call_log;
ANALYZE TABLE points_bill;
ANALYZE TABLE coupon_usage_record;
ANALYZE TABLE user_subscription;
ANALYZE TABLE chat_message;
ANALYZE TABLE model_config;
