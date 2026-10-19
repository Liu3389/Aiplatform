-- ============================================================
-- 迁移脚本 v3 (2026-08-27): 模型商店前沿模型展示 + description 字段
-- 适用于已运行的线上库，可重复执行
-- ============================================================
USE ai_gateway;

-- 中文配置必须按 utf8mb4 发送给 MySQL，避免模型描述被写成 mojibake。
SET NAMES utf8mb4;

-- 1. platform_model_config 增加 description 列
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'platform_model_config' AND COLUMN_NAME = 'description');
SET @ddl = IF(@col_exists = 0,
              'ALTER TABLE platform_model_config ADD COLUMN description varchar(255) DEFAULT NULL COMMENT ''模型描述（模型商店展示）'' AFTER provider',
              'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 已接入模型补充描述
UPDATE platform_model_config SET description = 'DeepSeek 轻量旗舰，极速响应，性价比之选'
WHERE display_name = 'DeepSeek-V4-Flash' AND (description IS NULL OR description = '');
UPDATE platform_model_config SET description = 'DeepSeek 旗舰模型，深度推理与复杂任务首选'
WHERE display_name = 'DeepSeek-V4-Pro' AND (description IS NULL OR description = '');

-- 3. 插入前沿展示模型（按 display_name 去重，可重复执行；接入后填 base_url/api_key 即可启用对话）
INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, description, base_url, api_key,
                                   supports_vision, status, sort_order, access_level)
SELECT src.dn, src.am, src.fp, src.pv, src.ds, NULL, NULL, src.sv, 1, src.so, src.al
FROM (
    SELECT 'GPT-5.2' AS dn, 'gpt-5.2' AS am, 8.000 AS fp, 'openai' AS pv, 'OpenAI 最新旗舰，多模态推理与智能体能力全面领先' AS ds, 3 AS so, 2 AS al, 1 AS sv
    UNION ALL SELECT 'GPT-5 mini', 'gpt-5-mini', 3.000, 'openai', 'OpenAI 轻量前沿模型，高速低成本', 4, 1, 1
    UNION ALL SELECT 'o4-mini', 'o4-mini', 5.000, 'openai', 'OpenAI 推理系列，数学与代码推理强化', 5, 2, 0
    UNION ALL SELECT 'Claude Opus 4.5', 'claude-opus-4-5', 10.000, 'anthropic', 'Anthropic 顶级旗舰，长文写作与复杂编程最强', 6, 3, 1
    UNION ALL SELECT 'Claude Sonnet 4.5', 'claude-sonnet-4-5', 5.000, 'anthropic', 'Anthropic 均衡旗舰，智能与速度兼备', 7, 2, 1
    UNION ALL SELECT 'Gemini 3 Pro', 'gemini-3-pro', 8.000, 'google', 'Google 新一代旗舰，原生多模态与超长上下文', 8, 3, 1
    UNION ALL SELECT 'Gemini 2.5 Flash', 'gemini-2.5-flash', 2.000, 'google', 'Google 高速多模态模型，大上下文低延迟', 9, 1, 1
    UNION ALL SELECT 'Grok 4.1', 'grok-4.1', 6.000, 'xai', 'xAI 旗舰模型，实时信息与工具调用能力突出', 10, 2, 0
    UNION ALL SELECT 'Qwen3-Max', 'qwen3-max', 4.000, 'alibaba', '阿里通义千问旗舰，中文理解与推理顶级水准', 11, 2, 1
    UNION ALL SELECT 'GLM-5', 'glm-5', 3.000, 'zhipu', '智谱新一代旗舰，中文创作与代码生成强化', 12, 1, 1
    UNION ALL SELECT 'Kimi K2.5', 'kimi-k2.5', 3.000, 'moonshot', '月之暗面长文本旗舰，超长上下文深度分析', 13, 1, 0
    UNION ALL SELECT 'Doubao Seed 1.6', 'doubao-seed-1.6', 2.000, 'bytedance', '字节跳动豆包大模型，多模态理解均衡', 14, 1, 1
    UNION ALL SELECT 'MiniMax M2', 'minimax-m2', 2.000, 'minimax', 'MiniMax 新一代开源旗舰，高效推理', 15, 1, 0
) AS src
WHERE NOT EXISTS (SELECT 1 FROM platform_model_config t WHERE t.display_name = src.dn);
