-- ============================================
-- 凌点AI平台 - 测试数据种子脚本
-- 说明: 包含虚拟模型、测试用户、会话、消息、账单等测试数据
-- 前置: 先执行 rebuild.sql（建表 + 基础数据），再执行本脚本
-- 用法: mysql -h HOST -u USER -p < seed-test.sql
-- 安全: 本脚本含 mock-* 假密钥与测试账户，仅供开发/测试环境使用，禁止用于生产
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 第一部分：虚拟模型配置（mock 密钥，仅供 UI 展示，不可实际调用）
-- ============================================

INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, base_url, api_key,
                                   supports_vision, status, sort_order, access_level)
VALUES ('GPT-4o (虚拟)', 'gpt-4o', 5.000, 'openai', 'https://api.openai.com/v1/chat/completions', 'mock-openai-key', 1,
        1, 3, 2),
       ('Claude-3.5-Sonnet (虚拟)', 'claude-3-5-sonnet', 4.000, 'anthropic', 'https://api.anthropic.com/v1/messages',
        'mock-anthropic-key', 1, 1, 4, 2),
       ('Gemini-2.0-Pro (虚拟)', 'gemini-2.0-pro', 4.000, 'google',
        'https://generativelanguage.googleapis.com/v1beta/models', 'mock-google-key', 1, 1, 5, 2),
       ('Qwen-Max (虚拟)', 'qwen-max', 2.000, 'qwen',
        'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation', 'mock-qwen-key', 0, 1, 6, 1),
       ('Qwen-Plus (虚拟)', 'qwen-plus', 1.000, 'qwen',
        'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation', 'mock-qwen-key', 0, 1, 7, 0),
       ('Claude-3-Opus (虚拟)', 'claude-3-opus', 5.000, 'anthropic', 'https://api.anthropic.com/v1/messages',
        'mock-anthropic-key', 1, 1, 8, 3),
       ('GPT-4-Turbo (虚拟)', 'gpt-4-turbo', 4.000, 'openai', 'https://api.openai.com/v1/chat/completions',
        'mock-openai-key', 1, 1, 9, 2),
       ('Gemini-2.0-Flash (虚拟)', 'gemini-2.0-flash', 1.000, 'google',
        'https://generativelanguage.googleapis.com/v1beta/models', 'mock-google-key', 1, 1, 10, 1);

-- 虚拟厂商配置（mock 密钥）
INSERT INTO marketplace_provider (provider_name, base_url, platform_api_key, input_price, output_price, status) VALUES
('OpenAI', 'https://api.openai.com/v1', 'mock-openai-key', 0.150000, 0.600000, 1),
('Anthropic', 'https://api.anthropic.com/v1', 'mock-anthropic-key', 0.300000, 1.500000, 1);

-- ============================================
-- 第二部分：测试用户（密码均为: Test@123456）
-- ============================================

INSERT INTO user (username, password, email, balance, points, status, role, free_api_strategy, free_quota,
                  daily_call_limit, monthly_call_limit)
VALUES ('testuser',
        '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK',
        'test@example.com',
        1000.000,
        1000.000,
        1,
        'USER',
        'QUOTA_BASED',
        100,
        100,
        3000);

-- 白银会员（月卡，30天后过期）
INSERT INTO user (username, password, email, balance, points, status, role, membership, membership_expire_time,
                  free_api_strategy, daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('silver_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'silver@test.com', 500.000,
        1200.000, 1, 'USER', 'silver', DATE_ADD(NOW(), INTERVAL 30 DAY), 'DAILY_FREE', 100, 3000, 80);

-- 黄金会员（季卡，90天后过期）
INSERT INTO user (username, password, email, balance, points, status, role, membership, membership_expire_time,
                  free_api_strategy, daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('gold_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'gold@test.com', 1000.000,
        3000.000, 1, 'USER', 'gold', DATE_ADD(NOW(), INTERVAL 90 DAY), 'DAILY_FREE', 200, 6000, 150);

-- 钻石会员（年卡，365天后过期）
INSERT INTO user (username, password, email, balance, points, status, role, membership, membership_expire_time,
                  free_api_strategy, daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('diamond_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'diamond@test.com', 5000.000,
        10000.000, 1, 'USER', 'diamond', DATE_ADD(NOW(), INTERVAL 365 DAY), 'DAILY_FREE', 500, 15000, 300);

-- 普通会员（无会员，仅免费额度）
INSERT INTO user (username, password, email, balance, points, status, role, membership, free_api_strategy,
                  daily_call_limit, monthly_call_limit, daily_free_count)
VALUES ('free_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'free@test.com', 100.000, 300.000,
        1, 'USER', NULL, 'QUOTA_BASED', 50, 1500, 30);

-- 禁用用户
INSERT INTO user (username, password, email, balance, points, status, role, membership)
VALUES ('disabled_user', '$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK', 'disabled@test.com', 0.000,
        0.000, 0, 'USER', 'silver');

-- ============================================
-- 第三部分：测试 API Key
-- ============================================

INSERT INTO api_key (api_key, user_id, name, status, rate_limit)
VALUES ('sk-test-silver-user-key-001', (SELECT id FROM user WHERE username = 'silver_user'), 'Silver User Key', 1, 100),
       ('sk-test-gold-user-key-001', (SELECT id FROM user WHERE username = 'gold_user'), 'Gold User Key', 1, 200),
       ('sk-test-diamond-user-key-001', (SELECT id FROM user WHERE username = 'diamond_user'), 'Diamond User Key', 1,
        500),
       ('sk-test-free-user-key-001', (SELECT id FROM user WHERE username = 'free_user'), 'Free User Key', 1, 50),
       ('sk-test-testuser-key-001', (SELECT id FROM user WHERE username = 'testuser'), 'Test User Key', 1, 100);

-- ============================================
-- 第四部分：测试会话
-- ============================================

INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'silver_user'), 'Python学习助手', 'deepseek-v4-flash', 6,
        DATE_SUB(NOW(), INTERVAL 2 HOUR)),
       ((SELECT id FROM user WHERE username = 'silver_user'), '数据分析讨论', 'deepseek-v4-pro', 4,
        DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'gold_user'), 'Java项目架构设计', 'gpt-4o', 8,
        DATE_SUB(NOW(), INTERVAL 3 HOUR)),
       ((SELECT id FROM user WHERE username = 'gold_user'), '微服务最佳实践', 'deepseek-v4-pro', 5,
        DATE_SUB(NOW(), INTERVAL 2 DAY)),
       ((SELECT id FROM user WHERE username = 'gold_user'), '数据库优化方案', 'qwen-max', 3,
        DATE_SUB(NOW(), INTERVAL 5 DAY));

INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'diamond_user'), '企业级系统设计', 'claude-3-opus', 10,
        DATE_SUB(NOW(), INTERVAL 1 HOUR)),
       ((SELECT id FROM user WHERE username = 'diamond_user'), 'AI模型选型分析', 'gpt-4-turbo', 7,
        DATE_SUB(NOW(), INTERVAL 6 HOUR)),
       ((SELECT id FROM user WHERE username = 'diamond_user'), '性能调优指南', 'deepseek-v4-pro', 12,
        DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'free_user'), 'Python基础问题', 'deepseek-v4-flash', 3,
        DATE_SUB(NOW(), INTERVAL 4 HOUR)),
       ((SELECT id FROM user WHERE username = 'free_user'), 'JavaScript入门', 'deepseek-v4-flash', 2,
        DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO conversation (user_id, title, model, message_count, last_message_time)
VALUES ((SELECT id FROM user WHERE username = 'testuser'), '测试会话1', 'deepseek-v4-flash', 4,
        DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
       ((SELECT id FROM user WHERE username = 'testuser'), '测试会话2', 'deepseek-v4-pro', 2,
        DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- ============================================
-- 第五部分：测试聊天消息
-- ============================================

SET @conv_silver_1 = (SELECT id
                      FROM conversation
                      WHERE user_id = (SELECT id FROM user WHERE username = 'silver_user')
                      ORDER BY create_time DESC
                      LIMIT 1);
SET @user_silver = (SELECT id
                    FROM user
                    WHERE username = 'silver_user');

INSERT INTO chat_message (conversation_id, user_id, role, content, model, tokens, file_ids)
VALUES (@conv_silver_1, @user_silver, 'user', '如何用Python实现快速排序？', 'deepseek-v4-flash', 15, NULL),
       (@conv_silver_1, @user_silver, 'assistant',
        '快速排序是一种高效的排序算法。基本思路是选择一个基准元素，将数组分为两部分，然后递归排序。', 'deepseek-v4-flash',
        180, NULL),
       (@conv_silver_1, @user_silver, 'user', '这个算法的空间复杂度是多少？', 'deepseek-v4-flash', 12, NULL),
       (@conv_silver_1, @user_silver, 'assistant',
        '上述实现的空间复杂度是 O(n)，因为每次递归都会创建新的列表。原地排序的版本可以将空间复杂度降低到 O(log n)。',
        'deepseek-v4-flash', 85, NULL),
       (@conv_silver_1, @user_silver, 'user', '能否给出原地排序的实现？', 'deepseek-v4-flash', 10, '[
         "test-file-001-uuid"
       ]'),
       (@conv_silver_1, @user_silver, 'assistant', '原地快速排序实现不需要额外的存储空间，通过交换元素完成排序。',
        'deepseek-v4-flash', 220, '[
         "test-file-001-uuid"
       ]');

SET @conv_gold_1 = (SELECT id
                    FROM conversation
                    WHERE user_id = (SELECT id FROM user WHERE username = 'gold_user')
                      AND model = 'gpt-4o'
                    ORDER BY create_time DESC
                    LIMIT 1);
SET @user_gold = (SELECT id
                  FROM user
                  WHERE username = 'gold_user');

INSERT INTO chat_message (conversation_id, user_id, role, content, model, tokens)
VALUES (@conv_gold_1, @user_gold, 'user', '如何设计一个高可用的微服务架构？', 'gpt-4o', 12),
       (@conv_gold_1, @user_gold, 'assistant',
        '设计高可用微服务架构需要考虑以下关键点：\n\n1. **服务发现与注册**：使用 Consul、Eureka 或 Nacos\n2. **负载均衡**：客户端或服务端负载均衡\n3. **熔断降级**：Hystrix 或 Resilience4j\n4. **分布式配置中心**：Spring Cloud Config\n5. **链路追踪**：Zipkin 或 SkyWalking',
        'gpt-4o', 280),
       (@conv_gold_1, @user_gold, 'user', '服务间通信应该用REST还是gRPC？', 'gpt-4o', 10),
       (@conv_gold_1, @user_gold, 'assistant',
        '选择建议：\n\n**REST API 适用场景：**对外暴露的API、跨语言调用、调试友好\n\n**gRPC 适用场景：**内部服务间通信、高性能要求、流式数据传输\n\n混合方案：外部用REST，内部用gRPC。',
        'gpt-4o', 165);

SET @conv_diamond_1 = (SELECT id
                       FROM conversation
                       WHERE user_id = (SELECT id FROM user WHERE username = 'diamond_user')
                         AND model = 'claude-3-opus'
                       ORDER BY create_time DESC
                       LIMIT 1);
SET @user_diamond = (SELECT id
                     FROM user
                     WHERE username = 'diamond_user');

INSERT INTO chat_message (conversation_id, user_id, role, content, model, tokens)
VALUES (@conv_diamond_1, @user_diamond, 'user', '请帮我设计一个电商平台的订单系统架构', 'claude-3-opus', 15),
       (@conv_diamond_1, @user_diamond, 'assistant',
        '电商平台订单系统架构设计：\n\n## 核心模块\n\n### 1. 订单服务\n- 订单创建、查询、修改、取消\n- 订单状态机管理\n- 分布式事务保证一致性\n\n### 2. 库存服务\n- 库存预占与释放\n- 库存扣减\n- 库存回滚\n\n### 3. 支付服务\n- 多渠道支付接入\n- 支付回调处理\n- 退款流程',
        'claude-3-opus', 450);

-- ============================================
-- 第六部分：点数账单记录（消费和赠送）
-- ============================================

INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_silver, 'DEDUCT', -1.000, 1200.000, 1199.000, @conv_silver_1, 'AI对话消耗 - deepseek-v4-flash', 0),
       (@user_silver, 'DEDUCT', -1.000, 1199.000, 1198.000, @conv_silver_1, 'AI对话消耗 - deepseek-v4-flash', 0),
       (@user_silver, 'GRANT', 80.000, 1198.000, 1278.000, NULL, '每日赠送点数 - 白银会员', 1),
       (@user_silver, 'GRANT', 80.000, 1278.000, 1358.000, NULL, '每日赠送点数 - 白银会员', 1);

INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_gold, 'DEDUCT', -5.000, 3000.000, 2995.000, @conv_gold_1, 'AI对话消耗 - gpt-4o', 0),
       (@user_gold, 'DEDUCT', -5.000, 2995.000, 2990.000, @conv_gold_1, 'AI对话消耗 - gpt-4o', 0),
       (@user_gold, 'GRANT', 150.000, 2990.000, 3140.000, NULL, '每日赠送点数 - 黄金会员', 1),
       (@user_gold, 'GRANT', 150.000, 3140.000, 3290.000, NULL, '每日赠送点数 - 黄金会员', 1);

INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_diamond, 'DEDUCT', -5.000, 10000.000, 9995.000, @conv_diamond_1, 'AI对话消耗 - claude-3-opus', 0),
       (@user_diamond, 'GRANT', 300.000, 9995.000, 10295.000, NULL, '每日赠送点数 - 钻石会员', 1);

SET @user_free = (SELECT id
                  FROM user
                  WHERE username = 'free_user');
SET @conv_free_1 = (SELECT id
                    FROM conversation
                    WHERE user_id = @user_free
                    LIMIT 1);

INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_free, 'DEDUCT', -1.000, 300.000, 299.000, @conv_free_1, 'AI对话消耗 - deepseek-v4-flash', 0),
       (@user_free, 'GRANT', 30.000, 299.000, 329.000, NULL, '每日赠送点数 - 免费用户', 1);

SET @user_test = (SELECT id
                  FROM user
                  WHERE username = 'testuser');
SET @conv_test_1 = (SELECT id
                    FROM conversation
                    WHERE user_id = @user_test
                    LIMIT 1);

INSERT INTO points_bill (user_id, change_type, points_change, balance_before, balance_after, business_id, description,
                         is_temporary)
VALUES (@user_test, 'DEDUCT', -1.000, 1000.000, 999.000, @conv_test_1, 'AI对话消耗 - deepseek-v4-flash', 0);

-- ============================================
-- 第七部分：用户订阅记录
-- ============================================

SET @pkg_silver = (SELECT id
                   FROM package_template
                   WHERE package_code = 'silver');
SET @pkg_gold = (SELECT id
                 FROM package_template
                 WHERE package_code = 'gold');
SET @pkg_diamond = (SELECT id
                    FROM package_template
                    WHERE package_code = 'diamond');

INSERT INTO user_subscription (user_id, package_id, package_code, package_name, identity_label, points_granted,
                               price_paid, start_time, expire_time, status, auto_renew, order_id, coupon_id,
                               discount_amount)
VALUES ((SELECT id FROM user WHERE username = 'silver_user'), @pkg_silver, 'silver', '白银月卡会员', '白银会员', 600,
        15.00, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), 'ACTIVE', 1,
        CONCAT('SUB_', UNIX_TIMESTAMP() * 1000 + 1), NULL, 0),
       ((SELECT id FROM user WHERE username = 'gold_user'), @pkg_gold, 'gold', '黄金进阶会员', '黄金会员', 1800, 80.00,
        DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY), 'ACTIVE', 0,
        CONCAT('SUB_', UNIX_TIMESTAMP() * 1000 + 2), NULL, 0),
       ((SELECT id FROM user WHERE username = 'diamond_user'), @pkg_diamond, 'diamond', '钻石旗舰会员', '钻石会员',
        5000, 420.00, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_ADD(NOW(), INTERVAL 305 DAY), 'ACTIVE', 1,
        CONCAT('SUB_', UNIX_TIMESTAMP() * 1000 + 3), NULL, 0);

-- ============================================
-- 第八部分：优惠券
-- ============================================

SET @user_silver_id = (SELECT id
                       FROM user
                       WHERE username = 'silver_user');
SET @user_gold_id = (SELECT id
                     FROM user
                     WHERE username = 'gold_user');

INSERT INTO coupon (user_id, coupon_code, amount, threshold_amount, applicable_plan, expire_time, used)
VALUES (@user_silver_id, 'WELCOME2024', 30.000, 50.000, 'silver', DATE_ADD(NOW(), INTERVAL 30 DAY), 0),
       (@user_gold_id, 'NEWYEAR2025', 20.000, 100.000, NULL, DATE_ADD(NOW(), INTERVAL 15 DAY), 0);

-- ============================================
-- 第九部分：系统通知（测试）
-- ============================================

SET @user_all = (SELECT id
                 FROM user
                 WHERE username = 'testuser');

INSERT INTO system_notification (user_id, title, content, type, is_read)
VALUES (@user_all, '系统维护通知', '系统将于今晚23:00-01:00进行例行维护，期间可能无法访问。', 'SYSTEM', 0),
       (@user_all, '新功能上线', 'AI文档助手功能已上线，支持PDF、Word、Excel等多种格式！', 'FEATURE', 0),
       (@user_all, '优惠活动', '春节特惠活动开启，所有套餐8折优惠！', 'PROMOTION', 1);

-- ============================================
-- 第十部分：统计数据（模拟）
-- ============================================

INSERT INTO daily_statistics (stat_date, new_users, total_users, active_users, new_conversations, total_conversations,
                              total_messages, total_input_tokens, total_output_tokens, total_tokens,
                              total_points_consumed, api_call_success, api_call_failed, api_call_total)
VALUES (DATE_SUB(CURDATE(), INTERVAL 1 DAY), 5, 156, 89, 45, 1234, 5678, 125000, 234000, 359000, 456.789, 2345, 23,
        2368),
       (DATE_SUB(CURDATE(), INTERVAL 2 DAY), 3, 151, 76, 38, 1189, 4892, 108000, 198000, 306000, 389.456, 2012, 18,
        2030),
       (DATE_SUB(CURDATE(), INTERVAL 3 DAY), 8, 148, 92, 52, 1151, 6234, 142000, 267000, 409000, 523.123, 2567, 31,
        2598);

INSERT INTO platform_statistics (stat_date, total_users, new_users, active_users, retained_users, total_calls,
                                 total_tokens, total_points_consumed, total_revenue, recharge_amount, package_sales,
                                 avg_order_value)
VALUES (DATE_SUB(CURDATE(), INTERVAL 1 DAY), 156, 5, 89, 78, 2368, 359000, 456.789, 1250.500, 850.000, 23, 36.957),
       (DATE_SUB(CURDATE(), INTERVAL 2 DAY), 151, 3, 76, 65, 2030, 306000, 389.456, 980.300, 650.000, 18, 36.111);

-- ============================================
-- 第十一部分：文件解析任务测试数据
-- ============================================

INSERT INTO file_parse_task (file_id, user_id, original_name, oss_url, file_size, file_type, parse_status,
                             parsed_text_length, text_summary, upload_time, parse_start_time, parse_end_time, is_used)
VALUES ('test-file-001-uuid',
        (SELECT id FROM user WHERE username = 'testuser' LIMIT 1),
        'test_report.pdf',
        'https://oss.example.com/documents/1/test_report.pdf',
        1024000,
        'pdf',
        'SUCCESS',
        15234,
        '这是一份关于系统架构设计的PDF文档，包含了微服务设计、数据库优化、性能调优等核心内容。',
        NOW(),
        DATE_ADD(NOW(), INTERVAL 1 SECOND),
        DATE_ADD(NOW(), INTERVAL 5 SECOND),
        0),

       ('test-file-002-uuid',
        (SELECT id FROM user WHERE username = 'testuser' LIMIT 1),
        'data_analysis.xlsx',
        'https://oss.example.com/documents/1/data_analysis.xlsx',
        512000,
        'xlsx',
        'SUCCESS',
        8956,
        'Excel数据分析表格，包含销售数据、用户增长趋势、月度报表等关键指标统计信息。',
        NOW(),
        DATE_ADD(NOW(), INTERVAL 1 SECOND),
        DATE_ADD(NOW(), INTERVAL 3 SECOND),
        0);

SET FOREIGN_KEY_CHECKS = 1;

SELECT '============================================' AS '';
SELECT '  ✅ 测试数据导入完成！' AS result;
SELECT '  ' AS '';
SELECT '  📝 测试账户列表（密码均为: Test@123456）：' AS accounts;
SELECT '  - testuser (测试用户)' AS account2;
SELECT '  - silver_user (白银会员)' AS account3;
SELECT '  - gold_user (黄金会员)' AS account4;
SELECT '  - diamond_user (钻石会员)' AS account5;
SELECT '  - free_user (免费用户)' AS account6;
SELECT '  - disabled_user (禁用用户)' AS account7;
SELECT '  ' AS '';
SELECT '  📊 包含测试数据：' AS data_info;
SELECT '  - 6个测试用户' AS data1;
SELECT '  - 5个API Key' AS data2;
SELECT '  - 11个会话记录' AS data3;
SELECT '  - 12条聊天消息' AS data4;
SELECT '  - 12条点数账单' AS data5;
SELECT '  - 3条用户订阅' AS data6;
SELECT '  - 2张优惠券' AS data7;
SELECT '  - 3条系统通知' AS data8;
SELECT '  - 3天统计数据' AS data9;
SELECT '  - 2个文件解析任务' AS data10;
SELECT '  - 8个虚拟模型配置（mock密钥）' AS data11;
SELECT '============================================' AS '';
