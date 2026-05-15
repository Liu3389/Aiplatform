-- 更新 DeepSeek API Key 配置
-- 执行日期: 2026-05-15
-- 说明: 使用真实可用的 DeepSeek API Key

-- 方案1: 轮询使用三个Key（负载均衡）
-- 将三个Key分别配置到不同的模型实例中

-- 先清空现有配置
DELETE FROM `model_config` WHERE provider = 'deepseek';

-- 插入新的模型配置，使用第一个API Key
INSERT INTO `model_config` 
(`model_name`, `provider`, `base_url`, `api_key`, `input_price`, `output_price`, `points_mode`, `points_per_request`, `status`) 
VALUES
('deepseek-v4-flash', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 0.000000, 0.000000, 'FREE', 0.000, 1),
('deepseek-v4-pro', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 0.000000, 0.000000, 'PREMIUM', 3.000, 1);

-- 验证配置
SELECT model_name, provider, api_key, status FROM model_config WHERE provider = 'deepseek';
