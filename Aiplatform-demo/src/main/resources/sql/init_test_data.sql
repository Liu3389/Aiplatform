-- ============================================
-- AI Gateway Platform - 测试数据初始化脚本
-- 版本: v5.0 (2026-05-15) - 适配双业务线架构
-- 说明: 插入平台对话模型、商店厂商配置及基础套餐
-- ============================================

-- 1. 清空旧数据（确保幂等性）
TRUNCATE TABLE platform_model_config;
TRUNCATE TABLE marketplace_provider;
TRUNCATE TABLE package_template;
TRUNCATE TABLE user;
TRUNCATE TABLE conversation;
TRUNCATE TABLE chat_message;

-- 2. 插入平台对话模型配置 (AI 对话平台)
-- 说明：前端展示各种主流模型，后端统一映射到 DeepSeek
INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, base_url, api_key, status) VALUES
('DeepSeek-V4-Flash', 'deepseek-v4-flash', 1.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 1),
('DeepSeek-V4-Pro', 'deepseek-v4-pro', 3.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 1),
('GPT-4o (虚拟)', 'deepseek-v4-pro', 5.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 1),
('Claude-3.5-Sonnet (虚拟)', 'deepseek-v4-pro', 4.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 1),
('Gemini-1.5-Pro (虚拟)', 'deepseek-v4-flash', 2.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 1);

-- 3. 插入模型商店厂商配置 (API Key 中转)
INSERT INTO marketplace_provider (provider_name, base_url, platform_api_key, input_price, output_price, status) VALUES
('OpenAI', 'https://api.openai.com/v1', 'sk-platform-mock-openai-key', 0.150000, 0.600000, 1),
('Anthropic', 'https://api.anthropic.com/v1', 'sk-platform-mock-anthropic-key', 0.300000, 1.500000, 1),
('DeepSeek', 'https://api.deepseek.com/v1', 'REDACTED_PROVIDER_API_KEY', 0.100000, 0.200000, 1);

-- 4. 插入套餐模板 (点数/会员体系)
INSERT INTO package_template (package_code, package_name, description, identity_label, points, price, duration_days, daily_call_limit, monthly_call_limit, max_tokens_per_call, priority_level, features, status, sort_order) VALUES
('FREE_TIER', '免费体验版', '每日赠送基础点数', '普通用户', 30.000, 0.00, 1, 10, 0, 2000, 0, '{"feature": "基础对话"}', 1, 1),
('BASIC_MONTHLY', '月度基础包', '适合轻度使用者', '青铜会员', 500.000, 29.90, 30, 0, 0, 4000, 1, '{"feature": "优先响应"}', 1, 2),
('PRO_MONTHLY', '月度专业包', '适合重度开发者', '白银会员', 1500.000, 79.90, 30, 0, 0, 8000, 2, '{"feature": "极速通道, 专属客服"}', 1, 3),
('ULTIMATE_YEARLY', '年度至尊包', '全年无限畅聊', '黄金会员', 20000.000, 899.00, 365, 0, 0, 16000, 3, '{"feature": "全模型解锁, API优先权"}', 1, 4);

SELECT '测试数据初始化完成！' AS result;
