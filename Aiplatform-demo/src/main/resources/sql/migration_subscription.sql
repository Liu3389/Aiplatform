-- 套餐模板表和用户订阅表
-- 创建日期: 2026-05-15

-- ============================================
-- 套餐模板表（管理员配置）
-- ============================================
CREATE TABLE IF NOT EXISTS `package_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_code` VARCHAR(32) NOT NULL COMMENT '套餐代码（唯一标识）',
  `package_name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '套餐描述',
  `identity_label` VARCHAR(32) DEFAULT NULL COMMENT '身份标识（如：至尊、尊享、畅享）',
  `points` DECIMAL(10,3) NOT NULL DEFAULT 0 COMMENT '包含点数',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价格（元）',
  `duration_days` INT DEFAULT NULL COMMENT '有效期天数（NULL表示永久）',
  `daily_call_limit` INT DEFAULT 0 COMMENT '每日调用限制（0表示无限制）',
  `monthly_call_limit` INT DEFAULT 0 COMMENT '每月调用限制（0表示无限制）',
  `max_tokens_per_call` INT DEFAULT 0 COMMENT '单次最大Token数（0表示无限制）',
  `priority_level` INT DEFAULT 0 COMMENT '优先级等级（数字越大优先级越高）',
  `features` TEXT DEFAULT NULL COMMENT '特性说明（JSON格式）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_code` (`package_code`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐模板表';

-- ============================================
-- 用户订阅记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `package_id` BIGINT NOT NULL COMMENT '套餐模板ID',
  `package_code` VARCHAR(32) NOT NULL COMMENT '套餐代码',
  `package_name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `identity_label` VARCHAR(32) DEFAULT NULL COMMENT '身份标识',
  `points_granted` DECIMAL(10,3) NOT NULL DEFAULT 0 COMMENT '授予点数',
  `price_paid` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实际支付价格',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间（NULL表示永久）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效中，EXPIRED-已过期，CANCELLED-已取消',
  `auto_renew` TINYINT(1) DEFAULT 0 COMMENT '是否自动续费',
  `order_id` VARCHAR(100) DEFAULT NULL COMMENT '关联订单ID',
  `coupon_id` BIGINT DEFAULT NULL COMMENT '使用的优惠券ID',
  `discount_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expire_time` (`expire_time`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅记录表';

-- ============================================
-- 插入默认套餐模板数据
-- ============================================
INSERT INTO `package_template` 
(`package_code`, `package_name`, `description`, `identity_label`, `points`, `price`, `duration_days`, 
 `daily_call_limit`, `monthly_call_limit`, `max_tokens_per_call`, `priority_level`, `features`, `status`, `sort_order`) 
VALUES
('free', '免费版', '基础免费套餐，适合体验用户', '普通用户', 0, 0, NULL, 10, 300, 4096, 0, 
 '{"description":"每日10次调用，每月300次调用","tokens":"单次最多4096 tokens"}', 1, 1),

('basic', '基础版', '适合个人开发者日常使用', '畅享会员', 100, 29.9, 30, 50, 1500, 8192, 1,
 '{"description":"每日50次调用，每月1500次调用","tokens":"单次最多8192 tokens","priority":"优先响应"}', 1, 2),

('premium', '专业版', '适合专业开发者和小型团队', '尊享会员', 500, 99.9, 30, 200, 6000, 16384, 2,
 '{"description":"每日200次调用，每月6000次调用","tokens":"单次最多16384 tokens","priority":"高优先级响应","support":"技术支持"}', 1, 3),

('ultimate', '旗舰版', '适合企业和重度使用者', '至尊会员', 2000, 299.9, 30, 1000, 30000, 32768, 3,
 '{"description":"每日1000次调用，每月30000次调用","tokens":"单次最多32768 tokens","priority":"最高优先级","support":"专属技术支持","api":"API专属通道"}', 1, 4);
