-- =============================================================================
-- 凌点AI平台 增量迁移脚本 v2 (四大模块优化)
-- 适用: 已部署的生产数据库，仅添加新列/索引/约束，不修改不删除已有字段
-- 执行方式: docker exec -i lingdian-mysql mysql -uroot -p<password> lingdian_ai < migration-v2.sql
-- =============================================================================

-- 1. user_notification 表新增 metadata 列（业务元数据，携带 couponId 等）
--    Facade 模式统一通知系统所需
ALTER TABLE `user_notification`
  ADD COLUMN IF NOT EXISTS `metadata` JSON NULL COMMENT '业务元数据(couponId等)';

-- 2. coupon 表新增复合索引（加速用户券查询：按用户+使用状态+过期时间过滤）
--    代金券生命周期管理所需
ALTER TABLE `coupon`
  ADD INDEX IF NOT EXISTS `idx_user_used_expire` (`user_id`, `used`, `expire_time`);

-- 3. coupon_usage_record 表新增唯一约束（确保一张券只有一条使用记录）
--    并发安全修复所需，防止重复使用记录
--    注意: 如果已存在重复 coupon_id 数据，需先清理再添加约束
ALTER TABLE `coupon_usage_record`
  ADD UNIQUE INDEX IF NOT EXISTS `uk_coupon_id` (`coupon_id`);

-- 4. user_notification 表补齐索引（加速按用户+已读+时间查询）
ALTER TABLE `user_notification`
  ADD INDEX IF NOT EXISTS `idx_user_read_time` (`user_id`, `is_read`, `create_time`);

-- 验证
SELECT '=== 迁移完成验证 ===' AS info;
SELECT 'user_notification 列:' AS info;
SHOW COLUMNS FROM `user_notification` LIKE 'metadata';
SELECT 'coupon 索引:' AS info;
SHOW INDEX FROM `coupon` WHERE Key_name = 'idx_user_used_expire';
SELECT 'coupon_usage_record 约束:' AS info;
SHOW INDEX FROM `coupon_usage_record` WHERE Key_name = 'uk_coupon_id';
