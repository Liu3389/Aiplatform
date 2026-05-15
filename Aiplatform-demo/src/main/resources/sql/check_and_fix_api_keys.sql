-- 检查并修复 DeepSeek API Key 配置
-- 执行日期: 2026-05-15

-- 1. 查看当前配置
SELECT id, model_name, provider, base_url, LEFT(api_key, 10) as api_key_prefix, status 
FROM model_config 
WHERE provider = 'deepseek';

-- 2. 如果API Key有问题，重新更新
UPDATE model_config 
SET api_key = 'REDACTED_PROVIDER_API_KEY',
    base_url = 'https://api.deepseek.com/v1/chat/completions',
    status = 1
WHERE model_name = 'deepseek-v4-flash';

UPDATE model_config 
SET api_key = 'REDACTED_PROVIDER_API_KEY',
    base_url = 'https://api.deepseek.com/v1/chat/completions',
    status = 1
WHERE model_name = 'deepseek-v4-pro';

-- 3. 验证更新结果
SELECT model_name, api_key, status FROM model_config WHERE provider = 'deepseek';
