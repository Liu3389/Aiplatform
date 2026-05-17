-- ============================================
-- AI Gateway Platform - 海量测试数据脚本
-- 版本: v5.0 (2026-05-16) - 修复表名和列名、配置真实 DeepSeek API 密钥
-- 说明: 插入足够多的测试数据覆盖所有业务场景
-- 密码: Test@123456 (BCrypt: $2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK)
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- 修复 P0-3: 增加 DELETE 逻辑，防止主键冲突
DELETE FROM `platform_model_config`;
DELETE FROM `package_template`;
DELETE FROM `user`;
DELETE FROM `conversation`;
DELETE FROM `chat_message`;

-- ============================================
-- 1. 模型配置 (只配置 deepseek-v4-flash)
-- ============================================
INSERT INTO `platform_model_config` (`id`, `display_name`, `actual_model`, `fixed_points`, `provider`, `base_url`, `api_key`, `supports_vision`, `access_level`, `status`, `sort_order`) VALUES
(1, 'DeepSeek-V4-Flash', 'deepseek-v4-flash', 1.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 0, 0, 1, 1),
(2, 'DeepSeek-V4-Pro',   'deepseek-v4-pro',   3.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 0, 1, 1, 2);

-- ============================================
-- 2. 套餐模板
-- ============================================
INSERT INTO `package_template` (`id`, `package_code`, `package_name`, `description`, `identity_label`, `points_granted`, `daily_points`, `price_monthly`, `price_quarterly`, `price_yearly`, `daily_call_limit`, `monthly_call_limit`, `max_tokens_per_call`, `max_input_tokens`, `max_output_tokens`, `model_access_level`, `priority_level`, `features`, `status`, `sort_order`) VALUES
(1, 'free',     '免费普通用户', '基础免费套餐，适合体验用户',       '普通用户', 0.000,   30.000,  0.00,   0.00,   0.00,   0,    0,    6000,  6000,  6000,  0, 0, '{"desc":"每日30限时点数"}',              1, 1),
(2, 'silver',   '白银月卡会员', '适合个人开发者日常使用',          '白银会员', 600.000, 80.000, 15.00,  40.00, 100.00,  0,    0,    12000, 12000, 12000, 1, 1, '{"desc":"每日30限时+80永久点数"}',       1, 2),
(3, 'gold',     '黄金进阶会员', '适合专业开发者和小型团队',        '黄金会员', 1800.000,150.000,30.00,  80.00, 200.00,  0,    0,    18000, 18000, 18000, 2, 2, '{"desc":"每日30限时+150永久点数"}',      1, 3),
(4, 'diamond',  '钻石旗舰会员', '适合企业和重度使用者',            '钻石会员', 5000.000,300.000,60.00, 160.00, 420.00,  0,    0,    32000, 32000, 32000, 3, 3, '{"desc":"每日30限时+300永久点数"}',      1, 4);

-- ============================================
-- 3. 用户数据 (50个用户: 2管理员 + 48普通用户)
-- ============================================
INSERT INTO `user` (`id`, `username`, `password`, `email`, `balance`, `points`, `status`, `role`, `membership`, `daily_call_limit`, `monthly_call_limit`, `create_time`) VALUES
-- 管理员
(1,  'superadmin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'superadmin@lingdian.ai', 10000.000, 50000.000, 1, 'SUPER_ADMIN', 'ultimate', 10000, 300000, '2026-01-15 08:00:00'),
(2,  'admin',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@lingdian.ai',       5000.000, 20000.000, 1, 'ADMIN',       'premium',   2000,  60000, '2026-01-15 08:05:00'),

-- 高级会员 (premium/ultimate)
(3,  'zhangsan',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'zhangsan@qq.com',          2000.0000,  8000.000, 1, 'USER', 'ultimate', 1000, 30000, '2026-02-01 10:00:00'),
(4,  'lisi',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'lisi@qq.com',              1500.0000,  5000.000, 1, 'USER', 'premium',   200,  6000, '2026-02-02 11:00:00'),
(5,  'wangwu',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'wangwu@163.com',           1000.0000,  3000.000, 1, 'USER', 'premium',   200,  6000, '2026-02-03 12:00:00'),

-- 基础会员 (basic)
(6,  'zhaoliu',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'zhaoliu@qq.com',            500.0000,  2000.000, 1, 'USER', 'basic',      50,  1500, '2026-03-01 09:00:00'),
(7,  'sunqi',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'sunqi@163.com',             300.0000,  1500.000, 1, 'USER', 'basic',      50,  1500, '2026-03-05 10:00:00'),
(8,  'zhouba',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'zhouba@qq.com',             200.0000,  1000.000, 1, 'USER', 'basic',      50,  1500, '2026-03-10 14:00:00'),
(9,  'wujiu',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'wujiu@163.com',             150.0000,   800.000, 1, 'USER', 'basic',      50,  1500, '2026-03-15 16:00:00'),
(10, 'zhengshi',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'zhengshi@qq.com',           100.0000,   500.000, 1, 'USER', 'basic',      50,  1500, '2026-03-20 08:00:00'),

-- 免费用户 (有余额，部分有会员)
(11, 'dev_alice',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'alice@dev.com',             200.0000,  1000.000, 1, 'USER', 'free',       10,   300, '2026-04-01 09:00:00'),
(12, 'dev_bob',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'bob@dev.com',               150.0000,   800.000, 1, 'USER', 'free',       10,   300, '2026-04-02 10:00:00'),
(13, 'dev_charlie','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'charlie@dev.com',           100.0000,   600.000, 1, 'USER', 'free',       10,   300, '2026-04-03 11:00:00'),
(14, 'dev_david',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'david@dev.com',              80.0000,   400.000, 1, 'USER', 'free',       10,   300, '2026-04-04 12:00:00'),
(15, 'dev_eve',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'eve@dev.com',                50.0000,   300.000, 1, 'USER', 'free',       10,   300, '2026-04-05 13:00:00'),

-- 低余额/濒临消耗完的用户
(16, 'user_low1',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'low1@test.com',              10.0000,    50.000, 1, 'USER', 'free',       10,   300, '2026-04-10 08:00:00'),
(17, 'user_low2',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'low2@test.com',               5.0000,    30.000, 1, 'USER', 'free',       10,   300, '2026-04-11 09:00:00'),
(18, 'user_low3',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'low3@test.com',               3.0000,    15.000, 1, 'USER', 'free',       10,   300, '2026-04-12 10:00:00'),
(19, 'user_low4',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'low4@test.com',               1.0000,     5.000, 1, 'USER', 'free',       10,   300, '2026-04-13 11:00:00'),

-- 新增用户（最近7天）
(20, 'new_user1',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new1@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-09 08:00:00'),
(21, 'new_user2',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new2@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-10 09:00:00'),
(22, 'new_user3',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new3@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-11 10:00:00'),
(23, 'new_user4',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new4@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-12 11:00:00'),
(24, 'new_user5',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new5@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-13 12:00:00'),
(25, 'new_user6',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new6@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-14 13:00:00'),
(26, 'new_user7',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new7@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-15 08:00:00'),
(27, 'new_user8',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'new8@test.com',               0.0000,   100.000, 1, 'USER', 'free',       10,   300, '2026-05-15 09:00:00'),

-- 禁用用户
(28, 'disabled01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'disabled01@test.com',         0.0000,   100.000, 0, 'USER', 'free',       10,   300, '2026-03-01 08:00:00'),
(29, 'disabled02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'disabled02@test.com',         0.0000,   100.000, 0, 'USER', 'free',       10,   300, '2026-03-05 09:00:00'),

-- 更多普通用户（批量编号）
(30, 'cus001',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus001@test.com',             50.0000,   300.000, 1, 'USER', 'free',       10,   300, '2026-04-15 08:00:00'),
(31, 'cus002',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus002@test.com',             60.0000,   350.000, 1, 'USER', 'free',       10,   300, '2026-04-16 09:00:00'),
(32, 'cus003',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus003@test.com',             70.0000,   400.000, 1, 'USER', 'free',       10,   300, '2026-04-17 10:00:00'),
(33, 'cus004',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus004@test.com',             80.0000,   450.000, 1, 'USER', 'free',       10,   300, '2026-04-18 11:00:00'),
(34, 'cus005',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus005@test.com',             90.0000,   500.000, 1, 'USER', 'basic',      50,  1500, '2026-04-19 12:00:00'),
(35, 'cus006',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus006@test.com',             100.0000,  550.000, 1, 'USER', 'basic',      50,  1500, '2026-04-20 13:00:00'),
(36, 'cus007',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus007@test.com',             110.0000,  600.000, 1, 'USER', 'premium',   200,  6000, '2026-04-21 14:00:00'),
(37, 'cus008',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus008@test.com',             120.0000,  650.000, 1, 'USER', 'premium',   200,  6000, '2026-04-22 15:00:00'),
(38, 'cus009',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus009@test.com',             130.0000,  700.000, 1, 'USER', 'ultimate', 1000, 30000, '2026-04-23 16:00:00'),
(39, 'cus010',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus010@test.com',             140.0000,  750.000, 1, 'USER', 'ultimate', 1000, 30000, '2026-04-24 17:00:00'),
(40, 'cus011',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus011@test.com',             150.0000,  800.000, 1, 'USER', 'free',       10,   300, '2026-04-25 08:00:00'),
(41, 'cus012',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus012@test.com',             160.0000,  850.000, 1, 'USER', 'free',       10,   300, '2026-04-26 09:00:00'),
(42, 'cus013',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus013@test.com',             170.0000,  900.000, 1, 'USER', 'basic',      50,  1500, '2026-04-27 10:00:00'),
(43, 'cus014',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus014@test.com',             180.0000,  950.000, 1, 'USER', 'premium',   200,  6000, '2026-04-28 11:00:00'),
(44, 'cus015',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus015@test.com',             190.0000, 1000.000, 1, 'USER', 'premium',   200,  6000, '2026-04-29 12:00:00'),
(45, 'cus016',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus016@test.com',             200.0000, 1050.000, 1, 'USER', 'ultimate', 1000, 30000, '2026-04-30 13:00:00'),
(46, 'cus017',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus017@test.com',             210.0000, 1100.000, 1, 'USER', 'ultimate', 1000, 30000, '2026-05-01 14:00:00'),
(47, 'cus018',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus018@test.com',             220.0000, 1150.000, 1, 'USER', 'free',       10,   300, '2026-05-02 15:00:00'),
(48, 'cus019',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus019@test.com',             230.0000, 1200.000, 1, 'USER', 'free',       10,   300, '2026-05-03 16:00:00'),
(49, 'cus020',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus020@test.com',             240.0000, 1250.000, 1, 'USER', 'basic',      50,  1500, '2026-05-04 17:00:00'),
(50, 'cus021',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cus021@test.com',             250.0000, 1300.000, 1, 'USER', 'premium',   200,  6000, '2026-05-05 08:00:00');

-- ============================================
-- 5. 平台统一 API Key（由 Java ApiKeyTestDataInitializer 加密插入）
--    启动时设置 app.init-test-data=true 即可自动插入
-- ============================================

-- ============================================
-- 6. 会话数据
-- ============================================
INSERT INTO `conversation` (`id`, `user_id`, `title`, `model`, `message_count`, `last_message_time`, `create_time`) VALUES
(1,  3,  'Python代码优化讨论',        'deepseek-v4-flash', 12, '2026-05-14 18:30:00', '2026-05-14 10:00:00'),
(2,  3,  'Spring Boot项目架构咨询',   'deepseek-v4-pro',    8, '2026-05-15 09:00:00', '2026-05-15 08:00:00'),
(3,  4,  '数据库设计方案',            'deepseek-v4-flash', 15, '2026-05-13 16:00:00', '2026-05-12 14:00:00'),
(4,  5,  'AI模型选型建议',            'deepseek-v4-pro',    6, '2026-05-15 10:00:00', '2026-05-15 09:30:00'),
(5,  11, 'React组件写法',            'deepseek-v4-flash',  5, '2026-05-14 12:00:00', '2026-05-14 11:00:00'),
(6,  12, 'Redis缓存策略',            'deepseek-v4-flash',  7, '2026-05-13 09:00:00', '2026-05-12 10:00:00'),
(7,  13, 'Linux常用命令',            'deepseek-v4-flash',  4, '2026-05-15 08:00:00', '2026-05-15 07:30:00'),
(8,  36, '微服务拆分策略',           'deepseek-v4-pro',   10, '2026-05-14 20:00:00', '2026-05-14 14:00:00'),
(9,  38, 'API网关设计',              'deepseek-v4-flash',  9, '2026-05-15 07:00:00', '2026-05-14 20:00:00'),
(10, 45, '高并发解决方案',           'deepseek-v4-pro',   11, '2026-05-15 09:00:00', '2026-05-14 16:00:00'),
(11, 20, '新手入门问题',             'deepseek-v4-flash',  3, '2026-05-12 10:00:00', '2026-05-11 09:00:00'),
(12, 21, '基础语法学习',             'deepseek-v4-flash',  4, '2026-05-13 11:00:00', '2026-05-12 10:00:00'),
(13, 6,  'MySQL优化问题',            'deepseek-v4-flash',  6, '2026-05-14 16:00:00', '2026-05-13 15:00:00'),
(14, 7,  'Docker部署指南',           'deepseek-v4-flash',  5, '2026-05-15 08:00:00', '2026-05-14 10:00:00'),
(15, 50, 'Nginx反向代理配置',        'deepseek-v4-pro',    7, '2026-05-15 09:00:00', '2026-05-14 11:00:00'),
(16, 1,  '系统运维监控',             'deepseek-v4-flash',  2, '2026-05-15 09:00:00', '2026-05-15 08:50:00'),
(17, 2,  '用户管理后台讨论',         'deepseek-v4-flash',  3, '2026-05-14 17:00:00', '2026-05-14 15:00:00'),
(18, 30, 'Git版本控制技巧',          'deepseek-v4-flash',  4, '2026-05-13 14:00:00', '2026-05-12 13:00:00'),
(19, 31, '前端性能优化',             'deepseek-v4-flash',  5, '2026-05-14 10:00:00', '2026-05-13 09:00:00'),
(20, 35, '后端安全防护',             'deepseek-v4-pro',    6, '2026-05-15 09:00:00', '2026-05-14 10:00:00'),

-- 为 cus021 (user_id=50) 创建多个会话（模拟活跃用户，用于测试对话列表加载）
(101, 50, 'Java并发编程学习',          'deepseek-v4-flash', 8,  '2026-05-15 10:00:00', '2026-05-15 09:00:00'),
(102, 50, 'Spring Security配置',       'deepseek-v4-pro',    6,  '2026-05-15 11:00:00', '2026-05-15 10:30:00'),
(103, 50, 'MyBatis Plus使用技巧',      'deepseek-v4-flash', 10, '2026-05-15 12:00:00', '2026-05-15 11:30:00'),
(104, 50, 'Docker容器化部署',          'deepseek-v4-flash', 5,  '2026-05-15 13:00:00', '2026-05-15 12:30:00'),
(105, 50, 'Redis缓存最佳实践',         'deepseek-v4-pro',    7,  '2026-05-15 14:00:00', '2026-05-15 13:30:00'),
(106, 50, '微服务架构设计',            'deepseek-v4-pro',    5,  '2026-05-15 15:00:00', '2026-05-15 14:00:00'),
(107, 50, 'Kubernetes入门',            'deepseek-v4-flash', 4,  '2026-05-15 16:00:00', '2026-05-15 15:30:00'),
(108, 50, 'CI/CD流水线搭建',           'deepseek-v4-flash', 6,  '2026-05-15 17:00:00', '2026-05-15 16:00:00'),
(109, 50, 'API设计规范讨论',           'deepseek-v4-pro',    7,  '2026-05-15 18:00:00', '2026-05-15 17:00:00'),
(110, 50, '性能优化方案',              'deepseek-v4-pro',    7,  '2026-05-15 19:00:00', '2026-05-15 18:00:00');

-- ============================================
-- 7. 聊天消息（游标分页测试用，每个会话多条）
-- ============================================
INSERT INTO `chat_message` (`conversation_id`, `user_id`, `role`, `content`, `model`, `tokens`, `create_time`) VALUES
-- 会话1 (zhangsan - Python)
(1, 3, 'user',      'Python中如何实现异步编程？请给我几个例子',                         'deepseek-v4-flash', 25,  '2026-05-14 10:00:00'),
(1, 3, 'assistant', 'Python异步编程主要通过asyncio库实现。核心概念包括coroutine、event loop、await/async关键字……', 'deepseek-v4-flash', 180, '2026-05-14 10:00:10'),
(1, 3, 'user',      'asyncio和threading有什么区别？什么时候用哪个？',                   'deepseek-v4-flash', 22,  '2026-05-14 10:05:00'),
(1, 3, 'assistant', 'asyncio是单线程的协程模型，适合IO密集型任务；threading是多线程模型，适合CPU密集型或需要真正并行执行的场景……', 'deepseek-v4-flash', 200, '2026-05-14 10:05:15'),
(1, 3, 'user',      '能给我一个实际项目中用asyncio的完整例子吗？比如一个Web爬虫',         'deepseek-v4-flash', 28,  '2026-05-14 10:10:00'),
(1, 3, 'assistant', '当然可以。下面是一个基于aiohttp和asyncio的异步Web爬虫完整实现：\n\n```python\nimport asyncio\nimport aiohttp\n...', 'deepseek-v4-flash', 350, '2026-05-14 10:10:20'),
(1, 3, 'user',      '这个爬虫如何加上错误重试和速率限制？',                             'deepseek-v4-flash', 20,  '2026-05-14 18:20:00'),
(1, 3, 'assistant', '可以在请求函数中添加retry装饰器，配合asyncio.Semaphore来控制并发数。示例代码如下……', 'deepseek-v4-flash', 250, '2026-05-14 18:20:15'),
(1, 3, 'user',      '很好，再帮我看看这段代码有什么问题：\n\ndef fetch_data():\n    response = requests.get(url)\n    return response.json()', 'deepseek-v4-flash', 40, '2026-05-14 18:25:00'),
(1, 3, 'assistant', '这段代码有几个问题：1. requests是同步库会阻塞；2. 没有异常处理；3. 没有超时设置。修改如下……', 'deepseek-v4-flash', 150, '2026-05-14 18:25:12'),
(1, 3, 'user',      '谢谢！Python3.12有什么新特性值得关注？',                           'deepseek-v4-flash', 18,  '2026-05-14 18:28:00'),
(1, 3, 'assistant', 'Python 3.12的主要新特性包括：1. 更详细的错误信息；2. f-string改进；3. 性能提升约5%；4. 新的type语句……', 'deepseek-v4-flash', 160, '2026-05-14 18:28:10'),

-- 会话2 (zhangsan - Spring Boot)
(2, 3, 'user',      'Spring Boot 3.x相比2.x有哪些重大变化？',                         'deepseek-v4-pro', 18,  '2026-05-15 08:00:00'),
(2, 3, 'assistant', 'Spring Boot 3.x主要变化：1. 最低Java17；2. Jakarta EE迁移；3. GraalVM原生镜像支持；4. 可观测性增强……', 'deepseek-v4-pro', 180, '2026-05-15 08:00:12'),
(2, 3, 'user',      '如何迁移一个2.x项目到3.x？',                                      'deepseek-v4-pro', 16,  '2026-05-15 08:10:00'),
(2, 3, 'assistant', '迁移步骤：1. 升级Java到17+；2. 替换javax为jakarta；3. 更新配置文件；4. 测试并修复兼容问题……', 'deepseek-v4-pro', 200, '2026-05-15 08:10:15'),
(2, 3, 'user',      '帮我设计一个RESTful API的项目结构，包括controller/service/repository层', 'deepseek-v4-pro', 25, '2026-05-15 08:30:00'),
(2, 3, 'assistant', '推荐的分层结构如下：\n```\ncom.example.project/\n├── controller/\n├── service/\n│   ├── impl/\n├── repository/\n├── entity/\n├── dto/\n├── config/\n└── exception/\n```', 'deepseek-v4-pro', 300, '2026-05-15 08:30:15'),
(2, 3, 'user',      'Controller层怎么处理全局异常？',                                   'deepseek-v4-pro', 16,  '2026-05-15 09:00:00'),
(2, 3, 'assistant', '使用@ControllerAdvice进行全局异常处理，结合自定义异常类和统一返回格式……', 'deepseek-v4-pro', 220, '2026-05-15 09:00:12'),

-- 会话3 (lisi - 数据库)
(3, 4, 'user',      'MySQL和PostgreSQL怎么选？',                                       'deepseek-v4-flash', 14, '2026-05-12 14:00:00'),
(3, 4, 'assistant', '选择取决于具体需求：MySQL适合简单读多写少场景、PostgreSQL适合复杂查询和数据分析……', 'deepseek-v4-flash', 150, '2026-05-12 14:00:10'),
(3, 4, 'user',      '电商系统订单表怎么设计？',                                         'deepseek-v4-flash', 14, '2026-05-12 14:30:00'),
(3, 4, 'assistant', '订单表设计建议：\n\n```sql\nCREATE TABLE orders (\n  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n  order_no VARCHAR(32) UNIQUE NOT NULL,\n  user_id BIGINT NOT NULL,\n  total_amount DECIMAL(10,2),\n  status TINYINT DEFAULT 0,\n  ...\n);\n```', 'deepseek-v4-flash', 350, '2026-05-12 14:30:12'),
(3, 4, 'user',      '分库分表怎么做？',                                                 'deepseek-v4-flash', 10, '2026-05-12 15:00:00'),
(3, 4, 'assistant', '分库分表策略：1. 垂直拆分按业务；2. 水平拆分按用户ID取模或一致性哈希。推荐使用ShardingSphere……', 'deepseek-v4-flash', 200, '2026-05-12 15:00:12'),
(3, 4, 'user',      '索引优化有什么最佳实践？',                                         'deepseek-v4-flash', 14, '2026-05-13 10:00:00'),
(3, 4, 'assistant', '索引最佳实践：1. 最左前缀原则；2. 覆盖索引减少回表；3. 避免在索引列上使用函数；4. 定期分析慢查询……', 'deepseek-v4-flash', 180, '2026-05-13 10:00:12'),
(3, 4, 'user',      '事务隔离级别有哪些？',                                             'deepseek-v4-flash', 12, '2026-05-13 10:30:00'),
(3, 4, 'assistant', 'MySQL支持四种隔离级别：READ UNCOMMITTED（读未提交）、READ COMMITTED（读已提交）、REPEATABLE READ（可重复读，InnoDB默认）、SERIALIZABLE（串行化）……', 'deepseek-v4-flash', 200, '2026-05-13 10:30:12'),
(3, 4, 'user',      'InnoDB的MVCC是怎么实现的？',                                      'deepseek-v4-flash', 14, '2026-05-13 11:00:00'),
(3, 4, 'assistant', 'MVCC通过undo log和read view实现。每行记录有DB_TRX_ID和DB_ROLL_PTR两个隐藏字段……', 'deepseek-v4-flash', 220, '2026-05-13 11:00:12'),
(3, 4, 'user',      '如何定位慢SQL？',                                                 'deepseek-v4-flash', 10, '2026-05-13 16:00:00'),
(3, 4, 'assistant', '定位慢SQL的方法：1. 开启slow_query_log；2. 使用EXPLAIN分析执行计划；3. 使用pt-query-digest工具；4. 检查是否缺少索引或索引失效……', 'deepseek-v4-flash', 200, '2026-05-13 16:00:10'),
(3, 4, 'assistant', '补充一点：也可以用Performance Schema来分析SQL执行各阶段的耗时分布',    'deepseek-v4-flash', 30,  '2026-05-13 16:00:15'),

-- 会话5 (alice)
(5, 11, 'user',      'React中useEffect和useLayoutEffect的区别？',                    'deepseek-v4-flash', 15, '2026-05-14 11:00:00'),
(5, 11, 'assistant', 'useEffect是异步执行的，在浏览器渲染完成后运行；useLayoutEffect是同步执行的，在DOM更新后浏览器绘制前运行……', 'deepseek-v4-flash', 100, '2026-05-14 11:00:10'),
(5, 11, 'user',      '什么时候用useMemo和useCallback？',                              'deepseek-v4-flash', 18, '2026-05-14 11:10:00'),
(5, 11, 'assistant', 'useMemo缓存计算值，useCallback缓存函数引用。当子组件使用React.memo时，父组件传入的回调需要用useCallback包裹避免不必要渲染……', 'deepseek-v4-flash', 150, '2026-05-14 11:10:12'),
(5, 11, 'user',      '帮我写一个自定义Hook useDebounce',                               'deepseek-v4-flash', 18, '2026-05-14 12:00:00'),
(5, 11, 'assistant', '```typescript\nfunction useDebounce<T>(value: T, delay: number): T {\n  const [debouncedValue, setDebouncedValue] = useState<T>(value);\n  useEffect(() => {\n    const timer = setTimeout(() => setDebouncedValue(value), delay);\n    return () => clearTimeout(timer);\n  }, [value]);\n  return debouncedValue;\n}\n```', 'deepseek-v4-flash', 180, '2026-05-14 12:00:12'),

-- ============================================
-- 为 cus021 (user_id=50) 的会话添加消息数据（用于测试对话加载和分页）
-- ============================================

-- 会话101: Java并发编程学习 (8条消息)
(101, 50, 'user',      'Java中如何实现线程池？',                                           'deepseek-v4-flash', 15, '2026-05-15 09:00:00'),
(101, 50, 'assistant', 'Java线程池可以通过Executors工厂类或ThreadPoolExecutor创建。推荐使用ThreadPoolExecutor以便更好地控制参数……', 'deepseek-v4-flash', 150, '2026-05-15 09:00:10'),
(101, 50, 'user',      '核心线程数和最大线程数有什么区别？',                                 'deepseek-v4-flash', 18, '2026-05-15 09:05:00'),
(101, 50, 'assistant', '核心线程数是线程池保持的最小线程数，即使空闲也不会被回收；最大线程数是线程池允许创建的最大线程数。当任务队列满时会创建新线程直到达到最大值……', 'deepseek-v4-flash', 180, '2026-05-15 09:05:12'),
(101, 50, 'user',      '如何选择合适的拒绝策略？',                                         'deepseek-v4-flash', 14, '2026-05-15 09:10:00'),
(101, 50, 'assistant', 'JDK提供四种拒绝策略：AbortPolicy（默认，抛异常）、CallerRunsPolicy（调用者运行）、DiscardPolicy（静默丢弃）、DiscardOldestPolicy（丢弃最老任务）。选择取决于业务场景……', 'deepseek-v4-flash', 200, '2026-05-15 09:10:15'),
(101, 50, 'user',      'CompletableFuture怎么用？',                                       'deepseek-v4-flash', 16, '2026-05-15 09:30:00'),
(101, 50, 'assistant', 'CompletableFuture是Java 8引入的异步编程工具，支持链式调用和组合多个异步任务。示例：\n\n```java\nCompletableFuture.supplyAsync(() -> fetchData())\n    .thenApply(data -> processData(data))\n    .thenAccept(result -> System.out.println(result));\n```', 'deepseek-v4-flash', 250, '2026-05-15 09:30:15'),
(101, 50, 'user',      '线程安全的集合有哪些？',                                           'deepseek-v4-flash', 14, '2026-05-15 09:40:00'),
(101, 50, 'assistant', '常用线程安全集合：ConcurrentHashMap、CopyOnWriteArrayList、ConcurrentLinkedQueue等。它们通过不同的机制保证线程安全，如分段锁、写时复制、CAS操作等……', 'deepseek-v4-flash', 180, '2026-05-15 09:40:12'),
(101, 50, 'user',      'synchronized和ReentrantLock的区别？',                             'deepseek-v4-flash', 20, '2026-05-15 09:50:00'),
(101, 50, 'assistant', '主要区别：1.synchronized是关键字，ReentrantLock是类；2.ReentrantLock可中断、可超时、可公平；3.synchronized自动释放锁，ReentrantLock需手动释放；4.ReentrantLock支持Condition……', 'deepseek-v4-flash', 220, '2026-05-15 09:50:15'),
(101, 50, 'user',      'volatile关键字的作用是什么？',                                     'deepseek-v4-flash', 16, '2026-05-15 10:00:00'),
(101, 50, 'assistant', 'volatile保证可见性和有序性，但不保证原子性。它确保一个线程修改了变量，其他线程立即可见；同时禁止指令重排序。适用于状态标志等场景……', 'deepseek-v4-flash', 160, '2026-05-15 10:00:12'),

-- 会话102: Spring Security配置 (6条消息)
(102, 50, 'user',      'Spring Security如何配置JWT认证？',                                'deepseek-v4-pro', 18, '2026-05-15 10:30:00'),
(102, 50, 'assistant', '配置JWT认证需要：1.添加依赖；2.创建JwtTokenProvider；3.配置SecurityFilterChain；4.实现UserDetailsService；5.创建登录接口生成token……', 'deepseek-v4-pro', 200, '2026-05-15 10:30:15'),
(102, 50, 'user',      '如何自定义权限注解？',                                            'deepseek-v4-pro', 14, '2026-05-15 10:40:00'),
(102, 50, 'assistant', '可以创建自定义注解如@RequireAdmin，然后使用AOP切面在方法执行前检查用户角色。也可以使用Spring Security的@PreAuthorize注解配合SpEL表达式……', 'deepseek-v4-pro', 180, '2026-05-15 10:40:12'),
(102, 50, 'user',      '如何处理跨域问题？',                                              'deepseek-v4-pro', 12, '2026-05-15 10:50:00'),
(102, 50, 'assistant', '在SecurityConfig中配置CorsConfigurationSource，允许特定域名、方法和Header。或者使用@CrossOrigin注解在Controller级别配置……', 'deepseek-v4-pro', 150, '2026-05-15 10:50:10'),
(102, 50, 'user',      'remember-me功能怎么实现？',                                       'deepseek-v4-pro', 16, '2026-05-15 11:00:00'),
(102, 50, 'assistant', 'Spring Security提供PersistentTokenBasedRememberMeServices，需要将token持久化到数据库。配置时需要指定UserDetailsService和TokenRepository……', 'deepseek-v4-pro', 180, '2026-05-15 11:00:12'),
(102, 50, 'user',      '如何实现多因素认证(MFA)？',                                       'deepseek-v4-pro', 16, '2026-05-15 11:10:00'),
(102, 50, 'assistant', 'MFA实现步骤：1.用户登录后生成TOTP密钥；2.展示二维码让用户绑定；3.后续登录时验证TOTP码；4.可以使用Google Authenticator或类似应用……', 'deepseek-v4-pro', 160, '2026-05-15 11:10:15'),
(102, 50, 'user',      'OAuth2和OIDC有什么区别？',                                        'deepseek-v4-pro', 16, '2026-05-15 11:20:00'),
(102, 50, 'assistant', 'OAuth2是授权框架，OIDC是基于OAuth2的身份认证层。OIDC增加了ID Token和用户信息端点，专门用于身份认证场景……', 'deepseek-v4-pro', 140, '2026-05-15 11:20:12'),

-- 会话103: MyBatis Plus使用技巧 (10条消息)
(103, 50, 'user',      'MyBatis Plus的分页怎么用？',                                      'deepseek-v4-flash', 16, '2026-05-15 11:30:00'),
(103, 50, 'assistant', '配置PaginationInnerInterceptor插件后，使用Page对象进行分页查询：\n\n```java\nPage<User> page = new Page<>(1, 10);\nuserMapper.selectPage(page, wrapper);\n```', 'deepseek-v4-flash', 180, '2026-05-15 11:30:12'),
(103, 50, 'user',      '如何自定义SQL？',                                                 'deepseek-v4-flash', 12, '2026-05-15 11:35:00'),
(103, 50, 'assistant', '可以在Mapper接口中使用@Select注解写SQL，或在XML文件中定义。MP也支持Wrapper构造复杂查询条件……', 'deepseek-v4-flash', 120, '2026-05-15 11:35:10'),
(103, 50, 'user',      '逻辑删除怎么配置？',                                              'deepseek-v4-flash', 14, '2026-05-15 11:40:00'),
(103, 50, 'assistant', '在实体类字段上加@TableLogic注解，配置application.yml中的logic-delete-field和logic-delete-value……', 'deepseek-v4-flash', 100, '2026-05-15 11:40:10'),
(103, 50, 'user',      '自动填充createTime和updateTime？',                                'deepseek-v4-flash', 20, '2026-05-15 11:45:00'),
(103, 50, 'assistant', '实现MetaObjectHandler接口，重写insertFill和updateFill方法，在字段上加@TableField(fill = FieldFill.INSERT)注解……', 'deepseek-v4-flash', 140, '2026-05-15 11:45:12'),
(103, 50, 'user',      '乐观锁怎么实现？',                                                'deepseek-v4-flash', 12, '2026-05-15 11:50:00'),
(103, 50, 'assistant', '在version字段上加@Version注解，MP会自动在更新时增加版本号并加入WHERE条件……', 'deepseek-v4-flash', 100, '2026-05-15 11:50:10'),
(103, 50, 'user',      '批量插入性能怎么样？',                                            'deepseek-v4-flash', 14, '2026-05-15 11:55:00'),
(103, 50, 'assistant', 'MP提供saveBatch方法，底层使用JDBC批处理。也可以自定义批量插入SQL，一次性插入多条记录，性能更好……', 'deepseek-v4-flash', 120, '2026-05-15 11:55:12'),
(103, 50, 'user',      '如何防止SQL注入？',                                               'deepseek-v4-flash', 12, '2026-05-15 12:00:00'),
(103, 50, 'assistant', 'MP使用预编译SQL和参数化查询，天然防止SQL注入。避免使用${}拼接，改用#{}占位符……', 'deepseek-v4-flash', 100, '2026-05-15 12:00:10'),
(103, 50, 'user',      '多租户怎么实现？',                                                'deepseek-v4-flash', 12, '2026-05-15 12:05:00'),
(103, 50, 'assistant', '配置TenantLineInnerInterceptor插件，实现TenantLineHandler接口提供tenantId，MP会自动在所有SQL中添加租户条件……', 'deepseek-v4-flash', 130, '2026-05-15 12:05:12'),
(103, 50, 'user',      '动态表名怎么处理？',                                              'deepseek-v4-flash', 14, '2026-05-15 12:10:00'),
(103, 50, 'assistant', '使用DynamicTableNameInnerInterceptor，实现DynamicTableNameHandler根据上下文动态替换表名，适合分表场景……', 'deepseek-v4-flash', 120, '2026-05-15 12:10:12'),
(103, 50, 'user',      '性能分析插件怎么用？',                                            'deepseek-v4-flash', 14, '2026-05-15 12:15:00'),
(103, 50, 'assistant', '配置PerformanceInterceptor（3.x已移除，建议使用p6spy），可以监控SQL执行时间和慢查询……', 'deepseek-v4-flash', 110, '2026-05-15 12:15:10'),

-- 会话104-110 简化消息（每个会话5-7条）
(104, 50, 'user',      'Dockerfile怎么写？',                                             'deepseek-v4-flash', 14, '2026-05-15 12:30:00'),
(104, 50, 'assistant', '基础Dockerfile结构：FROM指定基础镜像，COPY复制文件，RUN执行命令，EXPOSE暴露端口，CMD启动应用……', 'deepseek-v4-flash', 120, '2026-05-15 12:30:10'),
(104, 50, 'user',      '如何减小镜像体积？',                                             'deepseek-v4-flash', 14, '2026-05-15 12:35:00'),
(104, 50, 'assistant', '使用多阶段构建、alpine基础镜像、合并RUN指令、清理缓存、使用.dockerignore排除不必要文件……', 'deepseek-v4-flash', 130, '2026-05-15 12:35:12'),
(104, 50, 'user',      'docker-compose怎么用？',                                         'deepseek-v4-flash', 16, '2026-05-15 12:40:00'),
(104, 50, 'assistant', '编写docker-compose.yml定义services、networks、volumes，使用docker-compose up启动多容器应用……', 'deepseek-v4-flash', 120, '2026-05-15 12:40:10'),
(104, 50, 'user',      '容器间如何通信？',                                               'deepseek-v4-flash', 12, '2026-05-15 12:45:00'),
(104, 50, 'assistant', '同一compose网络内可通过服务名访问，或使用links、external_links。也可使用host网络模式……', 'deepseek-v4-flash', 110, '2026-05-15 12:45:12'),
(104, 50, 'user',      '如何持久化数据？',                                               'deepseek-v4-flash', 12, '2026-05-15 13:00:00'),
(104, 50, 'assistant', '使用volumes挂载宿主机目录或命名卷，数据会持久化保存，容器删除后数据不丢失……', 'deepseek-v4-flash', 100, '2026-05-15 13:00:10'),

(105, 50, 'user',      'Redis有哪些数据结构？',                                          'deepseek-v4-pro', 14, '2026-05-15 13:30:00'),
(105, 50, 'assistant', 'String、List、Set、Hash、ZSet、Bitmap、HyperLogLog、Geo、Stream等……', 'deepseek-v4-pro', 100, '2026-05-15 13:30:10'),
(105, 50, 'user',      '缓存穿透怎么解决？',                                             'deepseek-v4-pro', 14, '2026-05-15 13:35:00'),
(105, 50, 'assistant', '使用布隆过滤器、缓存空值、设置热点数据永不过期、限流降级等策略……', 'deepseek-v4-pro', 100, '2026-05-15 13:35:12'),
(105, 50, 'user',      '缓存雪崩怎么办？',                                               'deepseek-v4-pro', 12, '2026-05-15 13:40:00'),
(105, 50, 'assistant', '设置随机过期时间、使用多级缓存、Redis集群高可用、限流熔断……', 'deepseek-v4-pro', 90, '2026-05-15 13:40:10'),
(105, 50, 'user',      '缓存击穿如何处理？',                                             'deepseek-v4-pro', 12, '2026-05-15 13:45:00'),
(105, 50, 'assistant', '使用互斥锁（分布式锁）、热点数据永不过期、逻辑过期方案……', 'deepseek-v4-pro', 90, '2026-05-15 13:45:12'),
(105, 50, 'user',      'Redis持久化机制？',                                              'deepseek-v4-pro', 14, '2026-05-15 13:50:00'),
(105, 50, 'assistant', 'RDB快照和AOF日志两种方式。RDB适合备份，AOF更安全但体积大。推荐两者结合使用……', 'deepseek-v4-pro', 110, '2026-05-15 13:50:12'),
(105, 50, 'user',      'Redis集群怎么搭建？',                                            'deepseek-v4-pro', 14, '2026-05-15 14:00:00'),
(105, 50, 'assistant', '至少6个节点（3主3从），使用redis-cli --cluster create命令创建，数据分片存储……', 'deepseek-v4-pro', 100, '2026-05-15 14:00:12'),

(106, 50, 'user',      '微服务如何拆分？',                                               'deepseek-v4-pro', 12, '2026-05-15 14:00:00'),
(106, 50, 'assistant', '按业务领域拆分，遵循单一职责原则，考虑团队结构和技术栈……', 'deepseek-v4-pro', 90, '2026-05-15 14:00:10'),
(106, 50, 'user',      '服务间如何通信？',                                               'deepseek-v4-pro', 12, '2026-05-15 14:10:00'),
(106, 50, 'assistant', '同步用REST/gRPC，异步用消息队列（Kafka/RabbitMQ）……', 'deepseek-v4-pro', 80, '2026-05-15 14:10:10'),
(106, 50, 'user',      '如何保证数据一致性？',                                           'deepseek-v4-pro', 14, '2026-05-15 14:20:00'),
(106, 50, 'assistant', '使用Saga模式、TCC、本地消息表、可靠消息最终一致性等方案……', 'deepseek-v4-pro', 90, '2026-05-15 14:20:12'),
(106, 50, 'user',      '服务发现怎么做？',                                               'deepseek-v4-pro', 12, '2026-05-15 14:30:00'),
(106, 50, 'assistant', '使用Nacos、Consul、Eureka等服务注册中心……', 'deepseek-v4-pro', 70, '2026-05-15 14:30:10'),
(106, 50, 'user',      '链路追踪如何实现？',                                             'deepseek-v4-pro', 12, '2026-05-15 15:00:00'),
(106, 50, 'assistant', '使用SkyWalking、Zipkin、Jaeger等工具，集成OpenTelemetry标准……', 'deepseek-v4-pro', 90, '2026-05-15 15:00:12'),

(107, 50, 'user',      'Kubernetes基本概念？',                                           'deepseek-v4-flash', 14, '2026-05-15 15:30:00'),
(107, 50, 'assistant', 'Pod、Deployment、Service、Ingress、ConfigMap、Secret等核心资源……', 'deepseek-v4-flash', 90, '2026-05-15 15:30:10'),
(107, 50, 'user',      '如何部署应用到K8s？',                                            'deepseek-v4-flash', 14, '2026-05-15 15:40:00'),
(107, 50, 'assistant', '编写YAML定义Deployment和Service，使用kubectl apply部署……', 'deepseek-v4-flash', 80, '2026-05-15 15:40:10'),
(107, 50, 'user',      'Helm是什么？',                                                   'deepseek-v4-flash', 10, '2026-05-15 15:50:00'),
(107, 50, 'assistant', 'K8s的包管理工具，使用Chart模板化管理应用……', 'deepseek-v4-flash', 60, '2026-05-15 15:50:10'),
(107, 50, 'user',      '如何实现滚动更新？',                                             'deepseek-v4-flash', 12, '2026-05-15 16:00:00'),
(107, 50, 'assistant', 'Deployment默认就是滚动更新策略，可配置maxSurge和maxUnavailable……', 'deepseek-v4-flash', 90, '2026-05-15 16:00:10'),

(108, 50, 'user',      'CI/CD流程怎么设计？',                                            'deepseek-v4-flash', 14, '2026-05-15 16:00:00'),
(108, 50, 'assistant', '代码提交触发构建→单元测试→代码扫描→构建镜像→推送到仓库→部署到测试环境→自动化测试→部署到生产……', 'deepseek-v4-flash', 120, '2026-05-15 16:00:12'),
(108, 50, 'user',      'Jenkins和GitLab CI选哪个？',                                     'deepseek-v4-flash', 16, '2026-05-15 16:10:00'),
(108, 50, 'assistant', 'Jenkins插件丰富但配置复杂，GitLab CI集成度高配置简单。小团队推荐GitLab CI……', 'deepseek-v4-flash', 100, '2026-05-15 16:10:12'),
(108, 50, 'user',      '如何管理环境变量？',                                             'deepseek-v4-flash', 14, '2026-05-15 16:20:00'),
(108, 50, 'assistant', '使用CI/CD平台的Variables功能，敏感信息用Secrets管理……', 'deepseek-v4-flash', 80, '2026-05-15 16:20:10'),
(108, 50, 'user',      '如何实现蓝绿部署？',                                             'deepseek-v4-flash', 14, '2026-05-15 16:30:00'),
(108, 50, 'assistant', '维护两套相同环境，通过切换流量实现零停机部署……', 'deepseek-v4-flash', 70, '2026-05-15 16:30:10'),
(108, 50, 'user',      '部署失败如何回滚？',                                             'deepseek-v4-flash', 14, '2026-05-15 17:00:00'),
(108, 50, 'assistant', '保留历史版本镜像，使用kubectl rollout undo或CI/CD平台的一键回滚功能……', 'deepseek-v4-flash', 90, '2026-05-15 17:00:10'),

(109, 50, 'user',      'RESTful API设计原则？',                                          'deepseek-v4-pro', 16, '2026-05-15 17:00:00'),
(109, 50, 'assistant', '使用名词复数、HTTP动词表达操作、状态码规范、版本控制、统一响应格式……', 'deepseek-v4-pro', 100, '2026-05-15 17:00:12'),
(109, 50, 'user',      '如何设计统一的响应格式？',                                       'deepseek-v4-pro', 16, '2026-05-15 17:10:00'),
(109, 50, 'assistant', '包含code、message、data三个字段，成功code=200，错误使用对应HTTP状态码……', 'deepseek-v4-pro', 90, '2026-05-15 17:10:12'),
(109, 50, 'user',      'API版本怎么管理？',                                              'deepseek-v4-pro', 14, '2026-05-15 17:20:00'),
(109, 50, 'assistant', 'URL路径版本(/v1/)、请求头版本(Accept)、查询参数版本(?v=1)等方式……', 'deepseek-v4-pro', 90, '2026-05-15 17:20:10'),
(109, 50, 'user',      '如何做API限流？',                                                'deepseek-v4-pro', 12, '2026-05-15 17:30:00'),
(109, 50, 'assistant', '使用令牌桶、漏桶算法，通过网关层或中间件实现，基于IP或用户ID限流……', 'deepseek-v4-pro', 90, '2026-05-15 17:30:12'),
(109, 50, 'user',      'API文档用什么工具？',                                            'deepseek-v4-pro', 14, '2026-05-15 17:40:00'),
(109, 50, 'assistant', 'Swagger/OpenAPI、Postman、Apifox等，推荐Swagger自动生成文档……', 'deepseek-v4-pro', 80, '2026-05-15 17:40:10'),
(109, 50, 'user',      '如何处理API幂等性？',                                            'deepseek-v4-pro', 14, '2026-05-15 17:50:00'),
(109, 50, 'assistant', '使用唯一请求ID、数据库唯一约束、Redis防重表等机制……', 'deepseek-v4-pro', 80, '2026-05-15 17:50:12'),
(109, 50, 'user',      'API安全性怎么保障？',                                            'deepseek-v4-pro', 14, '2026-05-15 18:00:00'),
(109, 50, 'assistant', 'HTTPS、认证授权、输入验证、SQL注入防护、XSS防护、CSRF防护等……', 'deepseek-v4-pro', 90, '2026-05-15 18:00:12'),

(110, 50, 'user',      '如何优化数据库查询性能？',                                       'deepseek-v4-pro', 16, '2026-05-15 18:00:00'),
(110, 50, 'assistant', '添加合适索引、优化SQL语句、避免N+1查询、使用缓存、读写分离……', 'deepseek-v4-pro', 90, '2026-05-15 18:00:12'),
(110, 50, 'user',      'JVM调优怎么做？',                                                'deepseek-v4-pro', 12, '2026-05-15 18:10:00'),
(110, 50, 'assistant', '调整堆大小、选择合适的GC算法、监控GC日志、使用JVisualVM分析……', 'deepseek-v4-pro', 90, '2026-05-15 18:10:12'),
(110, 50, 'user',      '如何减少接口响应时间？',                                         'deepseek-v4-pro', 14, '2026-05-15 18:20:00'),
(110, 50, 'assistant', '异步处理、并行调用、缓存结果、压缩传输、CDN加速、数据库优化……', 'deepseek-v4-pro', 90, '2026-05-15 18:20:12'),
(110, 50, 'user',      '如何监控系统性能？',                                             'deepseek-v4-pro', 14, '2026-05-15 18:30:00'),
(110, 50, 'assistant', '使用Prometheus+Grafana监控指标，ELK收集日志，SkyWalking链路追踪……', 'deepseek-v4-pro', 90, '2026-05-15 18:30:12'),
(110, 50, 'user',      '如何应对高并发场景？',                                           'deepseek-v4-pro', 14, '2026-05-15 18:40:00'),
(110, 50, 'assistant', '负载均衡、缓存、异步处理、限流降级、数据库分库分表、CDN等……', 'deepseek-v4-pro', 90, '2026-05-15 18:40:12'),
(110, 50, 'user',      '如何进行压力测试？',                                             'deepseek-v4-pro', 14, '2026-05-15 18:50:00'),
(110, 50, 'assistant', '使用JMeter、wrk、ab等工具，模拟真实场景，逐步增加并发量观察系统表现……', 'deepseek-v4-pro', 100, '2026-05-15 18:50:12'),
(110, 50, 'user',      '性能瓶颈如何定位？',                                             'deepseek-v4-pro', 14, '2026-05-15 19:00:00'),
(110, 50, 'assistant', '使用APM工具、火焰图分析、慢查询日志、GC日志、系统监控等多维度排查……', 'deepseek-v4-pro', 100, '2026-05-15 19:00:12');