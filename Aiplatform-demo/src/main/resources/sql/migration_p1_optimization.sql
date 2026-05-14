-- P1优化：点数计费体系与缓存优化
-- 创建日期: 2026-05-14

-- 1. 创建点数明细账单表
CREATE TABLE IF NOT EXISTS `points_bill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `change_type` VARCHAR(20) NOT NULL COMMENT '变动类型：RECHARGE-充值, DEDUCT-扣除, GRANT-发放, EXPIRE-过期清零, REFUND-退款',
    `points_change` DECIMAL(10,3) NOT NULL COMMENT '变动点数（正数为增加，负数为减少，保留三位小数）',
    `balance_before` DECIMAL(10,3) NOT NULL COMMENT '变动前点数余额',
    `balance_after` DECIMAL(10,3) NOT NULL COMMENT '变动后点数余额',
    `business_id` VARCHAR(100) DEFAULT NULL COMMENT '关联业务ID（如对话ID、充值订单ID等）',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
    `model_name` VARCHAR(100) DEFAULT NULL COMMENT '模型名称（如果是对话消耗）',
    `expire_time` DATETIME DEFAULT NULL COMMENT '有效期（过期清零时使用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_business_id` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点数明细账单表';

-- 2. 为model_config表添加点数字段
ALTER TABLE `model_config` 
ADD COLUMN `points_mode` VARCHAR(20) DEFAULT 'STANDARD' COMMENT '点数消耗模式：FREE-免费模型, STANDARD-中端模型, PREMIUM-高端模型' AFTER `output_price`,
ADD COLUMN `points_per_request` DECIMAL(10,3) DEFAULT NULL COMMENT '每次对话消耗的点数（保留三位小数精度，优先使用此值）' AFTER `points_mode`;

-- 3. 更新现有模型的点数配置
UPDATE `model_config` SET `points_mode` = 'FREE', `points_per_request` = 0.000 WHERE `model_name` LIKE '%flash%' OR `model_name` LIKE '%free%';
UPDATE `model_config` SET `points_mode` = 'STANDARD', `points_per_request` = 1.000 WHERE `model_name` LIKE '%chat%' AND `model_name` NOT LIKE '%flash%';
UPDATE `model_config` SET `points_mode` = 'PREMIUM', `points_per_request` = 3.000 WHERE `model_name` LIKE '%pro%' OR `model_name` LIKE '%plus%';

-- 4. 为用户表添加点数字段（如果还没有）
-- 注意：如果user表已有balance字段，可以将其视为点数余额
-- ALTER TABLE `user` ADD COLUMN `points` DECIMAL(10,3) DEFAULT 0.000 COMMENT '点数余额' AFTER `balance`;

-- 5. 插入测试数据 - 点数账单示例
INSERT INTO `points_bill` (`user_id`, `change_type`, `points_change`, `balance_before`, `balance_after`, `business_id`, `description`, `model_name`, `create_time`) VALUES
(1, 'GRANT', 100.000, 0.000, 100.000, 'initial_grant', '初始赠送点数', NULL, NOW()),
(1, 'DEDUCT', -1.000, 100.000, 99.000, 'req_001', '对话消耗: deepseek-v4-flash', 'deepseek-v4-flash', NOW()),
(1, 'DEDUCT', -3.000, 99.000, 96.000, 'req_002', '对话消耗: gpt-4', 'gpt-4', NOW());

-- 6. 创建索引优化查询性能
CREATE INDEX IF NOT EXISTS `idx_points_bill_user_time` ON `points_bill` (`user_id`, `create_time` DESC);

-- 7. 添加注释说明
-- 数值计算规范：
-- - 后端底层业务运算（金额换算、点数计算）保留三位小数精度
-- - 所有返回给前端的金额、业务数据统一保留两位小数（四舍五入）
-- - 模型商店金额展示遵循相同规范

-- 缓存策略：
-- - 模型配置缓存：30分钟TTL
-- - 用户信息缓存：10分钟TTL
-- - 用户余额缓存：5分钟TTL
-- - 用户角色缓存：10分钟TTL
