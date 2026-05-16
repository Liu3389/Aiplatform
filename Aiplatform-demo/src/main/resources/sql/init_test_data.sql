-- ============================================
-- AI Gateway Platform - 测试数据初始化脚本
-- 版本: v5.2 (2026-05-16) - 统一表结构、移除硬编码密钥、完善截断列表
-- 说明: 插入平台对话模型、商店厂商配置及基础套餐，并包含管理员账号
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 清空旧数据（确保幂等性）
TRUNCATE TABLE user_notification;
TRUNCATE TABLE system_notification;
TRUNCATE TABLE admin_operation_log;
TRUNCATE TABLE billing_record;
TRUNCATE TABLE call_log;
TRUNCATE TABLE points_bill;
TRUNCATE TABLE chat_message;
TRUNCATE TABLE chat_message_history;
TRUNCATE TABLE conversation;
TRUNCATE TABLE api_key;
TRUNCATE TABLE coupon_usage_record;
TRUNCATE TABLE coupon;
TRUNCATE TABLE user_subscription;
TRUNCATE TABLE user;
TRUNCATE TABLE user_marketplace_key;
TRUNCATE TABLE marketplace_usage_log;
TRUNCATE TABLE marketplace_provider;
TRUNCATE TABLE platform_model_config;
TRUNCATE TABLE package_template;
TRUNCATE TABLE daily_statistics;
TRUNCATE TABLE statistics_counter;
TRUNCATE TABLE platform_statistics;

-- 2. 插入平台对话模型配置 (AI 对话平台)
-- 说明：前端展示各种主流模型，后端统一映射到 DeepSeek，支持多厂商路由预留
INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, base_url, api_key, status, sort_order) VALUES
('DeepSeek-V4-Flash', 'deepseek-v4-flash', 1.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 1),
('DeepSeek-V4-Pro', 'deepseek-v4-pro', 3.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 2),
('GPT-4o (虚拟)', 'deepseek-v4-pro', 5.000, 'openai', 'https://api.openai.com/v1/chat/completions', 'sk-platform-mock-openai-key', 1, 3),
('Claude-3.5-Sonnet (虚拟)', 'deepseek-v4-pro', 4.000, 'anthropic', 'https://api.anthropic.com/v1/messages', 'sk-platform-mock-anthropic-key', 1, 4);

-- 3. 插入模型商店厂商配置 (API Key 中转)
INSERT INTO marketplace_provider (provider_name, base_url, platform_api_key, input_price, output_price, status) VALUES
('OpenAI', 'https://api.openai.com/v1', 'sk-platform-mock-openai-key', 0.150000, 0.600000, 1),
('Anthropic', 'https://api.anthropic.com/v1', 'sk-platform-mock-anthropic-key', 0.300000, 1.500000, 1),
('DeepSeek', 'https://api.deepseek.com/v1', '${DEEPSEEK_API_KEY:your-api-key-here}', 0.100000, 0.200000, 1);

-- 4. 插入套餐模板 (点数/会员体系)
INSERT INTO package_template (package_code, package_name, description, identity_label, points, price, duration_days, daily_call_limit, monthly_call_limit, max_tokens_per_call, priority_level, features, status, sort_order) VALUES
('FREE_TIER', '免费体验版', '每日赠送基础点数', '普通用户', 30.000, 0.00, 1, 10, 0, 2000, 0, '{"feature": "基础对话"}', 1, 1),
('BASIC_MONTHLY', '月度基础包', '适合轻度使用者', '青铜会员', 500.000, 29.90, 30, 0, 0, 4000, 1, '{"feature": "优先响应"}', 1, 2),
('PRO_MONTHLY', '月度专业包', '适合重度开发者', '白银会员', 1500.000, 79.90, 30, 0, 0, 8000, 2, '{"feature": "极速通道, 专属客服"}', 1, 3),
('ULTIMATE_YEARLY', '年度至尊包', '全年无限畅聊', '黄金会员', 20000.000, 899.00, 365, 0, 0, 16000, 3, '{"feature": "全模型解锁, API优先权"}', 1, 4);

-- 5. 插入默认管理员账号 (密码: Admin@123456)
INSERT INTO user (username, password, email, balance, points, status, role) VALUES
('superadmin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@aiplatform.com', 0.000, 10000.000, 1, 'SUPER_ADMIN');

SET FOREIGN_KEY_CHECKS = 1;

SELECT '测试数据初始化完成！' AS result;
