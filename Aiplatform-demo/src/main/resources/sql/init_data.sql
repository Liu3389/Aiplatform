-- ============================================
-- AI Gateway Platform - 数据初始化脚本 (Init Data)
-- 版本: v3.1 (2026-05-16) - 修复表名和列名与 schema 不一致的问题
-- 说明: 包含默认模型配置、管理员账户及测试用户数据
-- 密码: Test@123456 (BCrypt加密)
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 清空现有数据（按依赖关系逆序）
DELETE FROM `user_notification`;
DELETE FROM `system_notification`;
DELETE FROM `admin_operation_log`;
DELETE FROM `billing_record`;
DELETE FROM `call_log`;
DELETE FROM `points_bill`;
DELETE FROM `chat_message`;
DELETE FROM `chat_message_history`;
DELETE FROM `conversation`;
DELETE FROM `api_key`;
DELETE FROM `coupon_usage_record`;
DELETE FROM `coupon`;
DELETE FROM `user_subscription`;
DELETE FROM `user`;
DELETE FROM `user_marketplace_key`;
DELETE FROM `marketplace_usage_log`;
DELETE FROM `marketplace_provider`;
DELETE FROM `platform_model_config`;
DELETE FROM `package_template`;
DELETE FROM `daily_statistics`;
DELETE FROM `statistics_counter`;
DELETE FROM `platform_statistics`;

-- 2. 初始化平台对话模型配置
INSERT INTO `platform_model_config` (`display_name`, `actual_model`, `fixed_points`, `provider`, `base_url`, `api_key`, `status`, `sort_order`) VALUES
('DeepSeek-V4-Flash', 'deepseek-v4-flash', 1.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 1),
('DeepSeek-V4-Pro',   'deepseek-v4-pro',   3.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 2);

-- 3. 初始化套餐模板
INSERT INTO `package_template` (`package_code`, `package_name`, `description`, `identity_label`, `points`, `price`, `duration_days`, `daily_call_limit`, `monthly_call_limit`, `max_tokens_per_call`, `priority_level`, `features`, `status`, `sort_order`) VALUES
('free', '免费版', '基础免费套餐，适合体验用户', '普通用户', 0, 0, NULL, 10, 300, 4096, 0, '{"description":"每日10次调用"}', 1, 1),
('basic', '基础版', '适合个人开发者日常使用', '畅享会员', 100, 29.9, 30, 50, 1500, 8192, 1, '{"description":"每日50次调用"}', 1, 2),
('premium', '专业版', '适合专业开发者和小型团队', '尊享会员', 500, 99.9, 30, 200, 6000, 16384, 2, '{"description":"每日200次调用"}', 1, 3),
('ultimate', '旗舰版', '适合企业和重度使用者', '至尊会员', 2000, 299.9, 30, 1000, 30000, 32768, 3, '{"description":"每日1000次调用"}', 1, 4);

-- 4. 初始化管理员账户
INSERT INTO `user` (`username`, `password`, `email`, `balance`, `points`, `status`, `role`, `free_api_strategy`, `free_quota`, `daily_call_limit`, `monthly_call_limit`) VALUES
('superadmin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'superadmin@test.com', 10000.000, 10000.000, 1, 'SUPER_ADMIN', 'UNLIMITED', 0, 0, 0),
('admin',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@test.com',      5000.000,  5000.000,  1, 'ADMIN',       'UNLIMITED', 0, 0, 0);

-- 5. 初始化测试用户
INSERT INTO `user` (`username`, `password`, `email`, `balance`, `points`, `status`, `role`, `free_api_strategy`, `free_quota`, `daily_call_limit`, `monthly_call_limit`) VALUES
('testuser01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'test01@test.com', 1000.000, 1000.000, 1, 'USER', 'QUOTA_BASED', 100, 100, 3000),
('testuser02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'test02@test.com',  100.000,  100.000, 1, 'USER', 'COUNT_LIMITED', 0, 50, 1500);

-- 6. 初始化计数器
INSERT INTO `statistics_counter` (`counter_type`, `counter_value`) VALUES
('USER_TOTAL', 0), ('CONVERSATION_TOTAL', 0), ('MESSAGE_TOTAL', 0), 
('API_CALL_TOTAL', 0), ('API_CALL_SUCCESS', 0), ('API_CALL_FAILED', 0)
ON DUPLICATE KEY UPDATE counter_value = counter_value;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '数据初始化完成！' AS message;
SELECT CONCAT('用户总数: ', COUNT(*)) AS summary FROM user;
SELECT '默认密码: Test@123456' AS password_info;
