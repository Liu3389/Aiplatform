-- ============================================
-- 测试数据脚本
-- 密码使用 BCrypt 加密 (admin123)
-- ============================================

-- 超级管理员（密码: admin123）
INSERT IGNORE INTO `user` (`username`, `password`, `email`, `balance`, `status`, `role`, `free_api_strategy`,
                           `free_quota`, `daily_call_limit`, `monthly_call_limit`)
VALUES ('superadmin', '$2a$10$QoHqCA4kTYceRwiAkZRHeuC8K8KV4/SV49G2EIx0WsEgz6dMw0xdO', 'superadmin@aiplatform.com',
        1500.0000, 1, 'SUPER_ADMIN', 'UNLIMITED', 0, 0, 0);

-- 管理员（密码: admin123）
INSERT IGNORE INTO `user` (`username`, `password`, `email`, `balance`, `status`, `role`, `free_api_strategy`,
                           `free_quota`, `daily_call_limit`, `monthly_call_limit`)
VALUES ('admin', '$2a$10$XmBMRd5J..yGd9Xu6I9k7.BKr1zwDA15c/GmVssU3lDZNefnT3TNa', 'admin@aiplatform.com', 500.0000, 1,
        'ADMIN', 'QUOTA_BASED', 1000, 100, 3000);

-- 普通用户（密码: test123）
INSERT IGNORE INTO `user` (`username`, `password`, `email`, `balance`, `status`, `role`, `free_api_strategy`,
                           `free_quota`, `daily_call_limit`, `monthly_call_limit`)
VALUES ('testuser', '$2a$10$fMymDBB7vssPsdCL6IqHfeTFLECSCg08i1DlQwEmIEe3dXoBGq5MS', 'test@aiplatform.com', 10.0000, 1,
        'USER', 'COUNT_LIMITED', 0, 50, 500);
