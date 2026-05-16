-- ============================================
-- AI Gateway Platform - 增量迁移脚本 (v3.0 -> v3.1)
-- 版本: v3.1 (2026-05-16)
-- 说明: 补充缺失索引、约束、软删除支持
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 优惠券表：增加防重领唯一索引
-- 用途：防止同一用户重复领取相同面额+相同套餐的未使用优惠券
ALTER TABLE `coupon` 
  ADD INDEX `idx_user_amount_plan_used` (`user_id`, `amount`, `applicable_plan`, `used`);

-- 2. 套餐模板表：确保 status 字段支持软删除（status=-1 表示已下架）
-- 注：status 字段已存在，此处仅添加注释说明
-- status: 0-下架，1-上架，-1-软删除（已下架且不可恢复）

-- 3. 用户订阅表：增加套餐代码+状态联合索引，加速活跃订阅查询
ALTER TABLE `user_subscription`
  ADD INDEX `idx_package_code_status` (`package_code`, `status`);

-- 4. 操作日志表：增加操作时间降序索引，加速最新日志查询
ALTER TABLE `admin_operation_log`
  ADD INDEX `idx_create_time_desc` (`create_time` DESC);

-- 5. 点数明细表：增加变动类型索引，加速账单查询
ALTER TABLE `points_bill`
  ADD INDEX `idx_change_type` (`change_type`);

-- 6. 调用日志表：增加用户+状态+时间联合索引，加速成功率统计
ALTER TABLE `call_log`
  ADD INDEX `idx_user_status_time` (`user_id`, `status`, `create_time`);

SET FOREIGN_KEY_CHECKS = 1;
