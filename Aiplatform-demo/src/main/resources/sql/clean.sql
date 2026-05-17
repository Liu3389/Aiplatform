-- ============================================
-- AI Gateway Platform - 清空所有数据脚本
-- 版本: v1.0 (2026-05-16)
-- 说明: 清空所有表数据，但保留表结构（比重建更快）
-- 用法: mysql -h HOST -u USER -p < clean.sql
-- 警告: 此操作不可逆，请谨慎使用！
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- 按依赖关系逆序清空所有表
TRUNCATE TABLE `user_notification`;
TRUNCATE TABLE `system_notification`;
TRUNCATE TABLE `admin_operation_log`;
TRUNCATE TABLE `billing_record`;
TRUNCATE TABLE `call_log`;
TRUNCATE TABLE `points_bill`;
TRUNCATE TABLE `chat_message`;
TRUNCATE TABLE `chat_message_history`;
TRUNCATE TABLE `conversation`;
TRUNCATE TABLE `api_key`;
TRUNCATE TABLE `coupon_usage_record`;
TRUNCATE TABLE `coupon`;
TRUNCATE TABLE `user_subscription`;
TRUNCATE TABLE `user`;
TRUNCATE TABLE `user_marketplace_key`;
TRUNCATE TABLE `marketplace_usage_log`;
TRUNCATE TABLE `marketplace_provider`;
TRUNCATE TABLE `platform_model_config`;
TRUNCATE TABLE `package_template`;
TRUNCATE TABLE `daily_statistics`;
TRUNCATE TABLE `statistics_counter`;
TRUNCATE TABLE `platform_statistics`;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '✅ 所有数据已清空！' AS result;
SELECT '提示：如需重新初始化，请执行 rebuild.sql' AS next_step;
