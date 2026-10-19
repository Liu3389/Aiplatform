-- ============================================================
-- 迁移脚本 v5：修复模型商店描述的字符集污染
-- 适用于已运行的业务数据库，可重复执行
-- 执行前请使用 mysql --default-character-set=utf8mb4
-- ============================================================

USE ai_gateway;
SET NAMES utf8mb4;

-- v3/v4 曾在未显式指定客户端字符集的情况下执行，导致部分中文被按
-- latin1 解释后写入 utf8mb4，例如“轻量”变成“è½»é‡”。
-- 这里按模型编码逐条回写正确文案，重复执行不会产生副作用。
UPDATE platform_model_config SET description = 'DeepSeek 轻量旗舰，极速响应，性价比之选'
WHERE actual_model = 'deepseek-v4-flash';

UPDATE platform_model_config SET description = 'DeepSeek 旗舰模型，深度推理与复杂任务首选'
WHERE actual_model = 'deepseek-v4-pro';

UPDATE platform_model_config SET description = 'OpenAI 最新旗舰，多模态推理与智能体能力全面领先'
WHERE actual_model = 'gpt-5.2';

UPDATE platform_model_config SET description = 'OpenAI 轻量前沿模型，高速低成本'
WHERE actual_model = 'gpt-5-mini';

UPDATE platform_model_config SET description = 'OpenAI 推理系列，数学与代码推理强化'
WHERE actual_model = 'o4-mini';

UPDATE platform_model_config SET description = 'Anthropic 顶级旗舰，长文写作与复杂编程最强'
WHERE actual_model = 'claude-opus-4-5';

UPDATE platform_model_config SET description = 'Anthropic 均衡旗舰，智能与速度兼备'
WHERE actual_model = 'claude-sonnet-4-5';

UPDATE platform_model_config SET description = 'Google 新一代旗舰，原生多模态与超长上下文'
WHERE actual_model = 'gemini-3-pro';

UPDATE platform_model_config SET description = 'Google 高速多模态模型，大上下文低延迟'
WHERE actual_model = 'gemini-2.5-flash';

UPDATE platform_model_config SET description = 'xAI 旗舰模型，实时信息与工具调用能力突出'
WHERE actual_model = 'grok-4.1';

UPDATE platform_model_config SET description = '阿里通义千问旗舰，中文理解与推理顶级水准'
WHERE actual_model = 'qwen3-max';

UPDATE platform_model_config SET description = '智谱新一代旗舰，中文创作与代码生成强化'
WHERE actual_model = 'glm-5';

UPDATE platform_model_config SET description = '月之暗面长文本旗舰，超长上下文深度分析'
WHERE actual_model = 'kimi-k2.5';

UPDATE platform_model_config SET description = '字节跳动豆包大模型，多模态理解均衡'
WHERE actual_model = 'doubao-seed-1.6';

UPDATE platform_model_config SET description = 'MiniMax 新一代开源旗舰，高效推理'
WHERE actual_model = 'minimax-m2';

UPDATE platform_model_config SET description = 'DeepSeek 视觉理解模型，支持截图、图表和图片问答'
WHERE actual_model = 'deepseek-v4-flash-vision-exp';

SELECT '模型描述字符集修复完成' AS result;
