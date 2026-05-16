-- ============================================
-- AI Gateway Platform - 海量测试数据脚本
-- 版本: v4.1 (2026-05-16) - 修复表名和列名、移除硬编码密钥
-- 说明: 插入足够多的测试数据覆盖所有业务场景
-- 密码: Test@123456 (BCrypt: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy)
-- API Key 使用环境变量占位符: ${DEEPSEEK_API_KEY}
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
INSERT INTO `platform_model_config` (`id`, `display_name`, `actual_model`, `fixed_points`, `provider`, `base_url`, `api_key`, `status`, `sort_order`) VALUES
(1, 'DeepSeek-V4-Flash', 'deepseek-v4-flash', 1.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 1),
(2, 'DeepSeek-V4-Pro',   'deepseek-v4-pro',   3.000, 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '${DEEPSEEK_API_KEY:your-api-key-here}', 1, 2);

-- ============================================
-- 2. 套餐模板
-- ============================================
INSERT INTO `package_template` (`id`, `package_code`, `package_name`, `description`, `identity_label`, `points`, `price`, `duration_days`, `daily_call_limit`, `monthly_call_limit`, `max_tokens_per_call`, `priority_level`, `features`, `status`, `sort_order`) VALUES
(1, 'free',     '免费版', '基础免费套餐，适合体验用户',       '普通用户', 0.000,   0.00, NULL, 10,   300,   4096,  0, '{"desc":"每日10次调用"}',     1, 1),
(2, 'basic',    '基础版', '适合个人开发者日常使用',          '畅享会员', 100.000, 29.90, 30,  50,   1500,  8192,  1, '{"desc":"每日50次调用"}',     1, 2),
(3, 'premium',  '专业版', '适合专业开发者和小型团队',        '尊享会员', 500.000, 99.90, 30,  200,  6000,  16384, 2, '{"desc":"每日200次调用"}',    1, 3),
(4, 'ultimate', '旗舰版', '适合企业和重度使用者',            '至尊会员', 2000.000,299.90,30,  1000, 30000, 32768, 3, '{"desc":"每日1000次调用"}',   1, 4),
(5, 'starter',  '新手套餐','7天试用套餐，自动过期',           '体验会员', 50.000,  9.90, 7,   30,   900,   4096,  0, '{"desc":"7天试用"}',           1, 5);

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
(20, 35, '后端安全防护',             'deepseek-v4-pro',    6, '2026-05-15 09:00:00', '2026-05-14 10:00:00');

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
(5, 11, 'assistant', '```typescript\nfunction useDebounce<T>(value: T, delay: number): T {\n  const [debouncedValue, setDebouncedValue] = useState<T>(value);\n  useEffect(() => {\n    const timer = setTimeout(() => set