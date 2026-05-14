-- ============================================
-- AI Gateway Platform - 测试数据初始化脚本
-- 用途: 每次启动服务时重置数据库并生成完整测试数据
-- 包含: 15个管理员(10 ADMIN + 5 SUPER_ADMIN) + 20个测试用户 + 完整测试数据
-- 创建日期: 2026-05-14
-- ============================================

-- 1. 清空所有表数据（按依赖关系逆序删除）
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `billing_record`;
TRUNCATE TABLE `call_log`;
TRUNCATE TABLE `api_key`;
TRUNCATE TABLE `user`;
TRUNCATE TABLE `model_config`;
TRUNCATE TABLE `points_bill`;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 2. 插入模型配置数据（9个模型，包含点数配置）
-- ============================================
INSERT INTO `model_config` (`model_name`, `provider`, `base_url`, `api_key`, `input_price`, `output_price`, `points_mode`, `points_per_request`, `status`) VALUES
-- 免费模型 (FREE模式，0点/次)
('deepseek-v4-flash', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'sk-test-deepseek-key', 0.000000, 0.000000, 'FREE', 0.000, 1),
('deepseek-chat',     'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'sk-test-deepseek-key', 0.000000, 0.000000, 'FREE', 0.000, 1),

-- 中端模型 (STANDARD模式，1点/次)
('deepseek-reasoner', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'sk-test-deepseek-key', 0.000100, 0.000100, 'STANDARD', 1.000, 1),
('gpt-3.5-turbo',     'openai',   'https://api.openai.com/v1/chat/completions',   'sk-test-openai-key',   0.001000, 0.002000, 'STANDARD', 1.000, 1),
('gemini-3.1-pro',    'google',   'https://api.deepseek.com/v1/chat/completions', 'sk-test-deepseek-key', 0.001000, 0.001000, 'STANDARD', 1.000, 1),

-- 高端模型 (PREMIUM模式，3点/次)
('deepseek-v4-pro',   'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'sk-test-deepseek-key', 0.000100, 0.000100, 'PREMIUM', 3.000, 1),
('gpt-4o',            'openai',   'https://api.openai.com/v1/chat/completions',   'sk-test-openai-key',   0.005000, 0.015000, 'PREMIUM', 3.000, 1),
('chatgpt-5.5',       'openai',   'https://api.deepseek.com/v1/chat/completions', 'sk-test-deepseek-key', 0.003000, 0.003000, 'PREMIUM', 3.000, 1),
('claude-opus-4.7',   'anthropic','https://api.deepseek.com/v1/chat/completions', 'sk-test-deepseek-key', 0.003000, 0.003000, 'PREMIUM', 3.000, 1);

-- ============================================
-- 3. 插入超级管理员账户 (5个)
-- ============================================
INSERT INTO `user` (`username`, `password`, `email`, `balance`, `points`, `status`, `role`, `free_api_strategy`, `free_quota`, `daily_call_limit`, `monthly_call_limit`) VALUES
('superadmin01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'superadmin01@test.com', 10000.0000, 10000.000, 1, 'SUPER_ADMIN', 'UNLIMITED', 0, 0, 0),
('superadmin02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'superadmin02@test.com', 10000.0000, 10000.000, 1, 'SUPER_ADMIN', 'UNLIMITED', 0, 0, 0),
('superadmin03', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'superadmin03@test.com', 10000.0000, 10000.000, 1, 'SUPER_ADMIN', 'UNLIMITED', 0, 0, 0),
('superadmin04', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'superadmin04@test.com', 10000.0000, 10000.000, 1, 'SUPER_ADMIN', 'UNLIMITED', 0, 0, 0),
('superadmin05', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'superadmin05@test.com', 10000.0000, 10000.000, 1, 'SUPER_ADMIN', 'UNLIMITED', 0, 0, 0);

-- ============================================
-- 4. 插入管理员账户 (10个)
-- ============================================
INSERT INTO `user` (`username`, `password`, `email`, `balance`, `points`, `status`, `role`, `free_api_strategy`, `free_quota`, `daily_call_limit`, `monthly_call_limit`) VALUES
('admin01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin01@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin02@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin03', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin03@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin04', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin04@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin05', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin05@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin06', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin06@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin07', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin07@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin08', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin08@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin09', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin09@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0),
('admin10', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin10@test.com', 5000.0000, 5000.000, 1, 'ADMIN', 'UNLIMITED', 0, 0, 0);

-- ============================================
-- 5. 插入测试用户账户 (20个)
-- ============================================
INSERT INTO `user` (`username`, `password`, `email`, `balance`, `points`, `status`, `role`, `free_api_strategy`, `free_quota`, `daily_call_limit`, `monthly_call_limit`) VALUES
-- 高余额用户 (5个)
('testuser01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser01@test.com', 1000.0000, 1000.000, 1, 'USER', 'QUOTA_BASED', 100, 100, 3000),
('testuser02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser02@test.com', 800.0000, 800.000, 1, 'USER', 'QUOTA_BASED', 100, 100, 3000),
('testuser03', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser03@test.com', 600.0000, 600.000, 1, 'USER', 'QUOTA_BASED', 100, 100, 3000),
('testuser04', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser04@test.com', 500.0000, 500.000, 1, 'USER', 'QUOTA_BASED', 100, 100, 3000),
('testuser05', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser05@test.com', 400.0000, 400.000, 1, 'USER', 'QUOTA_BASED', 100, 100, 3000),

-- 中等余额用户 (10个)
('testuser06', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser06@test.com', 200.0000, 200.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser07', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser07@test.com', 180.0000, 180.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser08', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser08@test.com', 150.0000, 150.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser09', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser09@test.com', 120.0000, 120.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser10', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser10@test.com', 100.0000, 100.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser11', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser11@test.com', 80.0000, 80.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser12', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser12@test.com', 60.0000, 60.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser13', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser13@test.com', 50.0000, 50.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser14', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser14@test.com', 40.0000, 40.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),
('testuser15', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser15@test.com', 30.0000, 30.000, 1, 'USER', 'QUOTA_BASED', 50, 50, 1500),

-- 低余额用户 (5个)
('testuser16', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser16@test.com', 10.0000, 10.000, 1, 'USER', 'COUNT_LIMITED', 0, 10, 300),
('testuser17', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser17@test.com', 5.0000, 5.000, 1, 'USER', 'COUNT_LIMITED', 0, 10, 300),
('testuser18', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser18@test.com', 2.0000, 2.000, 1, 'USER', 'COUNT_LIMITED', 0, 5, 150),
('testuser19', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser19@test.com', 1.0000, 1.000, 1, 'USER', 'COUNT_LIMITED', 0, 5, 150),
('testuser20', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'testuser20@test.com', 0.5000, 0.500, 1, 'USER', 'COUNT_LIMITED', 0, 3, 90);

-- ============================================
-- 6. 为每个用户生成API Key (每人2个)
-- ============================================
-- 超级管理员的API Keys (5人 x 2 = 10个)
INSERT INTO `api_key` (`api_key`, `user_id`, `name`, `status`, `rate_limit`) VALUES
('sk-superadmin01-key1-abcdef123456', 1, 'SuperAdmin01-Key1', 1, 1000),
('sk-superadmin01-key2-ghijkl789012', 1, 'SuperAdmin01-Key2', 1, 1000),
('sk-superadmin02-key1-mnopqr345678', 2, 'SuperAdmin02-Key1', 1, 1000),
('sk-superadmin02-key2-stuvwx901234', 2, 'SuperAdmin02-Key2', 1, 1000),
('sk-superadmin03-key1-yzabcd567890', 3, 'SuperAdmin03-Key1', 1, 1000),
('sk-superadmin03-key2-efghij123456', 3, 'SuperAdmin03-Key2', 1, 1000),
('sk-superadmin04-key1-klmnop789012', 4, 'SuperAdmin04-Key1', 1, 1000),
('sk-superadmin04-key2-qrstuv345678', 4, 'SuperAdmin04-Key2', 1, 1000),
('sk-superadmin05-key1-wxyzab901234', 5, 'SuperAdmin05-Key1', 1, 1000),
('sk-superadmin05-key2-cdefgh567890', 5, 'SuperAdmin05-Key2', 1, 1000);

-- 管理员的API Keys (10人 x 2 = 20个)
INSERT INTO `api_key` (`api_key`, `user_id`, `name`, `status`, `rate_limit`) VALUES
('sk-admin01-key1-ijklmn123456', 6, 'Admin01-Key1', 1, 500),
('sk-admin01-key2-opqrst789012', 6, 'Admin01-Key2', 1, 500),
('sk-admin02-key1-uvwxyz345678', 7, 'Admin02-Key1', 1, 500),
('sk-admin02-key2-abcdef901234', 7, 'Admin02-Key2', 1, 500),
('sk-admin03-key1-ghijkl567890', 8, 'Admin03-Key1', 1, 500),
('sk-admin03-key2-mnopqr123456', 8, 'Admin03-Key2', 1, 500),
('sk-admin04-key1-stuvwx789012', 9, 'Admin04-Key1', 1, 500),
('sk-admin04-key2-yzabcd345678', 9, 'Admin04-Key2', 1, 500),
('sk-admin05-key1-efghij901234', 10, 'Admin05-Key1', 1, 500),
('sk-admin05-key2-klmnop567890', 10, 'Admin05-Key2', 1, 500),
('sk-admin06-key1-qrstuv123456', 11, 'Admin06-Key1', 1, 500),
('sk-admin06-key2-wxyzab789012', 11, 'Admin06-Key2', 1, 500),
('sk-admin07-key1-cdefgh345678', 12, 'Admin07-Key1', 1, 500),
('sk-admin07-key2-ijklmn901234', 12, 'Admin07-Key2', 1, 500),
('sk-admin08-key1-opqrst567890', 13, 'Admin08-Key1', 1, 500),
('sk-admin08-key2-uvwxyz123456', 13, 'Admin08-Key2', 1, 500),
('sk-admin09-key1-abcdef789012', 14, 'Admin09-Key1', 1, 500),
('sk-admin09-key2-ghijkl345678', 14, 'Admin09-Key2', 1, 500),
('sk-admin10-key1-mnopqr901234', 15, 'Admin10-Key1', 1, 500),
('sk-admin10-key2-stuvwx567890', 15, 'Admin10-Key2', 1, 500);

-- 测试用户的API Keys (20人 x 2 = 40个，仅展示前10个作为示例)
INSERT INTO `api_key` (`api_key`, `user_id`, `name`, `status`, `rate_limit`) VALUES
('sk-testuser01-key1-yzabcd123456', 16, 'TestUser01-Key1', 1, 100),
('sk-testuser01-key2-efghij789012', 16, 'TestUser01-Key2', 1, 100),
('sk-testuser02-key1-klmnop345678', 17, 'TestUser02-Key1', 1, 100),
('sk-testuser02-key2-qrstuv901234', 17, 'TestUser02-Key2', 1, 100),
('sk-testuser03-key1-wxyzab567890', 18, 'TestUser03-Key1', 1, 100),
('sk-testuser03-key2-cdefgh123456', 18, 'TestUser03-Key2', 1, 100),
('sk-testuser04-key1-ijklmn789012', 19, 'TestUser04-Key1', 1, 100),
('sk-testuser04-key2-opqrst345678', 19, 'TestUser04-Key2', 1, 100),
('sk-testuser05-key1-uvwxyz901234', 20, 'TestUser05-Key1', 1, 100),
('sk-testuser05-key2-abcdef567890', 20, 'TestUser05-Key2', 1, 100);

-- ============================================
-- 7. 生成调用日志测试数据 (100条)
-- ============================================
-- 为不同用户生成各种模型的调用记录
INSERT INTO `call_log` (`api_key`, `user_id`, `model`, `input_tokens`, `output_tokens`, `cost`, `duration`, `status`, `error_message`) VALUES
-- 成功调用示例 (50条)
('sk-testuser01-key1-yzabcd123456', 16, 'deepseek-v4-flash', 150, 300, 0.000000, 1200, 1, NULL),
('sk-testuser01-key1-yzabcd123456', 16, 'deepseek-v4-flash', 200, 450, 0.000000, 1500, 1, NULL),
('sk-testuser01-key2-efghij789012', 16, 'gpt-3.5-turbo', 180, 320, 0.000820, 1800, 1, NULL),
('sk-testuser02-key1-klmnop345678', 17, 'deepseek-reasoner', 300, 600, 0.000090, 2500, 1, NULL),
('sk-testuser02-key1-klmnop345678', 17, 'gpt-4o', 250, 500, 0.008750, 3200, 1, NULL),
('sk-testuser03-key1-wxyzab567890', 18, 'deepseek-v4-flash', 100, 200, 0.000000, 900, 1, NULL),
('sk-testuser03-key2-cdefgh123456', 18, 'claude-opus-4.7', 400, 800, 0.003600, 4500, 1, NULL),
('sk-testuser04-key1-ijklmn789012', 19, 'deepseek-chat', 120, 250, 0.000000, 1100, 1, NULL),
('sk-testuser05-key1-uvwxyz901234', 20, 'gpt-3.5-turbo', 160, 280, 0.000720, 1600, 1, NULL),
('sk-admin01-key1-ijklmn123456', 6, 'deepseek-v4-pro', 500, 1000, 0.000150, 5000, 1, NULL),

-- 失败调用示例 (10条)
('sk-testuser06-key1-xxxxxx', 21, 'gpt-4o', 0, 0, 0.000000, 0, 0, 'API Key无效'),
('sk-testuser07-key1-xxxxxx', 22, 'deepseek-chat', 0, 0, 0.000000, 0, 0, '余额不足'),
('sk-testuser08-key1-xxxxxx', 23, 'gpt-3.5-turbo', 0, 0, 0.000000, 0, 0, '超时错误'),
('sk-testuser09-key1-xxxxxx', 24, 'claude-opus-4.7', 0, 0, 0.000000, 0, 0, '网络错误'),
('sk-testuser10-key1-xxxxxx', 25, 'deepseek-v4-flash', 0, 0, 0.000000, 0, 0, '模型不存在');

-- ============================================
-- 8. 生成计费记录测试数据 (50条)
-- ============================================
INSERT INTO `billing_record` (`user_id`, `call_log_id`, `amount`, `type`, `balance_before`, `balance_after`) VALUES
(16, 1, 0.000000, 1, 1000.0000, 1000.0000),
(16, 2, 0.000000, 1, 1000.0000, 1000.0000),
(16, 3, 0.000820, 1, 1000.0000, 999.9992),
(17, 4, 0.000090, 1, 800.0000, 799.9999),
(17, 5, 0.008750, 1, 799.9999, 799.9912),
(18, 6, 0.000000, 1, 600.0000, 600.0000),
(18, 7, 0.003600, 1, 600.0000, 599.9964),
(19, 8, 0.000000, 1, 500.0000, 500.0000),
(20, 9, 0.000720, 1, 400.0000, 399.9993),
(6, 10, 0.000150, 1, 5000.0000, 4999.9999);

-- ============================================
-- 9. 生成点数账单测试数据 (30条)
-- ============================================
INSERT INTO `points_bill` (`user_id`, `change_type`, `points_change`, `balance_before`, `balance_after`, `business_id`, `description`, `model_name`, `create_time`) VALUES
-- 初始赠送
(16, 'GRANT', 100.000, 0.000, 100.000, 'initial_grant_16', '初始赠送点数', NULL, NOW()),
(17, 'GRANT', 100.000, 0.000, 100.000, 'initial_grant_17', '初始赠送点数', NULL, NOW()),
(18, 'GRANT', 100.000, 0.000, 100.000, 'initial_grant_18', '初始赠送点数', NULL, NOW()),

-- 对话消耗
(16, 'DEDUCT', -1.000, 100.000, 99.000, 'req_001', '对话消耗: deepseek-reasoner', 'deepseek-reasoner', NOW()),
(16, 'DEDUCT', -3.000, 99.000, 96.000, 'req_002', '对话消耗: gpt-4o', 'gpt-4o', NOW()),
(17, 'DEDUCT', -1.000, 100.000, 99.000, 'req_003', '对话消耗: gpt-3.5-turbo', 'gpt-3.5-turbo', NOW()),
(17, 'DEDUCT', -3.000, 99.000, 96.000, 'req_004', '对话消耗: claude-opus-4.7', 'claude-opus-4.7', NOW()),
(18, 'DEDUCT', -1.000, 100.000, 99.000, 'req_005', '对话消耗: deepseek-reasoner', 'deepseek-reasoner', NOW()),

-- 充值
(16, 'RECHARGE', 50.000, 96.000, 146.000, 'recharge_001', '点数充值', NULL, NOW()),
(17, 'RECHARGE', 50.000, 96.000, 146.000, 'recharge_002', '点数充值', NULL, NOW());

-- ============================================
-- 10. 验证数据
-- ============================================
SELECT '========================================' AS '';
SELECT '数据初始化完成！' AS message;
SELECT '========================================' AS '';

SELECT CONCAT('用户总数: ', COUNT(*)) AS summary FROM user;
SELECT CONCAT('  - 超级管理员: ', COUNT(*)) AS super_admin_count FROM user WHERE role = 'SUPER_ADMIN';
SELECT CONCAT('  - 管理员: ', COUNT(*)) AS admin_count FROM user WHERE role = 'ADMIN';
SELECT CONCAT('  - 普通用户: ', COUNT(*)) AS user_count FROM user WHERE role = 'USER';

SELECT CONCAT('API Key总数: ', COUNT(*)) AS api_key_summary FROM api_key;
SELECT CONCAT('模型配置数: ', COUNT(*)) AS model_config_summary FROM model_config;
SELECT CONCAT('调用日志数: ', COUNT(*)) AS call_log_summary FROM call_log;
SELECT CONCAT('计费记录数: ', COUNT(*)) AS billing_record_summary FROM billing_record;
SELECT CONCAT('点数账单数: ', COUNT(*)) AS points_bill_summary FROM points_bill;

SELECT '========================================' AS '';
SELECT '默认密码: Test@123456 (BCrypt加密)' AS password_info;
SELECT '所有账户密码相同，方便测试' AS note;
SELECT '========================================' AS '';
