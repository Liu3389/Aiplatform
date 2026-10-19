-- ============================================================
-- 迁移脚本 v7：DeepSeek V4 Responses API 路由
-- 适用于已部署线上库，可重复执行
-- ============================================================

USE ai_gateway;
SET NAMES utf8mb4;

-- DeepSeek 当前将 V4 Flash 的旧模型别名统一服务为 deepseek-flash。
-- 平台展示名不变，避免用户历史记录和前端文案发生变化。
UPDATE platform_model_config
SET actual_model = 'deepseek-flash',
    base_url = 'https://api.deepseek.com'
WHERE provider = 'deepseek'
  AND actual_model IN ('deepseek-v4-flash', 'deepseek-v4-flash-vision-exp');
