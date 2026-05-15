-- 优惠券使用记录表
-- 创建日期: 2026-05-15

CREATE TABLE IF NOT EXISTS `coupon_usage_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `coupon_id` BIGINT NOT NULL COMMENT '优惠券ID',
  `coupon_code` VARCHAR(32) NOT NULL COMMENT '优惠券码',
  `coupon_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '优惠券面额',
  `order_type` VARCHAR(32) NOT NULL COMMENT '订单类型：RECHARGE-充值, SUBSCRIPTION-订阅, PACKAGE-套餐',
  `order_id` VARCHAR(100) DEFAULT NULL COMMENT '关联订单ID（如充值订单号、订阅ID等）',
  `order_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '订单原始金额',
  `discount_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '优惠金额',
  `actual_amount` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '实际支付金额',
  `usage_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_coupon_id` (`coupon_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_usage_time` (`usage_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券使用记录表';
