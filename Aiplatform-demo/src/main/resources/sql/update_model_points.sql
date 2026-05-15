-- 更新模型点数消耗配置
-- 执行日期: 2026-05-15
-- 说明: deepseek-v4-flash设置为1点/次，支持每日30点配额机制

-- 1. 更新 deepseek-v4-flash 的点数为 1点/次
UPDATE model_config 
SET points_per_request = 1.000,
    points_mode = 'STANDARD'
WHERE model_name = 'deepseek-v4-flash';

-- 2. 更新 deepseek-v4-pro 的点数为 3点/次（保持不变）
UPDATE model_config 
SET points_per_request = 3.000,
    points_mode = 'PREMIUM'
WHERE model_name = 'deepseek-v4-pro';

-- 3. 验证更新结果
SELECT model_name, points_per_request, points_mode, status 
FROM model_config 
WHERE provider = 'deepseek';
