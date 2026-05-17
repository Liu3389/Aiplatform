-- ============================================
-- 文件上传与解析功能 - 测试数据脚本
-- 用于测试新实现的3个核心接口
-- ============================================

SET NAMES utf8mb4;

-- 1. 查看file_parse_task表结构
DESCRIBE file_parse_task;

-- 2. 插入测试数据（模拟已上传的文件）
INSERT INTO file_parse_task (file_id, user_id, original_name, oss_url, file_size, file_type, parse_status, parsed_text_length, upload_time, parse_start_time, parse_end_time, is_used) VALUES
('test-file-001-uuid', 
 (SELECT id FROM user WHERE username='testuser' LIMIT 1),
 'test_report.pdf',
 'https://oss.example.com/documents/1/test_report.pdf',
 1024000,
 'pdf',
 'SUCCESS',
 15234,
 NOW(),
 DATE_ADD(NOW(), INTERVAL 1 SECOND),
 DATE_ADD(NOW(), INTERVAL 5 SECOND),
 0),

('test-file-002-uuid',
 (SELECT id FROM user WHERE username='testuser' LIMIT 1),
 'data_analysis.xlsx',
 'https://oss.example.com/documents/1/data_analysis.xlsx',
 512000,
 'xlsx',
 'SUCCESS',
 8956,
 NOW(),
 DATE_ADD(NOW(), INTERVAL 1 SECOND),
 DATE_ADD(NOW(), INTERVAL 3 SECOND),
 0),

('test-file-003-uuid',
 (SELECT id FROM user WHERE username='silver_user' LIMIT 1),
 'python_tutorial.md',
 'https://oss.example.com/documents/2/python_tutorial.md',
 256000,
 'md',
 'PARSING',
 NULL,
 NOW(),
 DATE_ADD(NOW(), INTERVAL 1 SECOND),
 NULL,
 0),

('test-file-004-uuid',
 (SELECT id FROM user WHERE username='gold_user' LIMIT 1),
 'corrupted_file.docx',
 'https://oss.example.com/documents/3/corrupted_file.docx',
 128000,
 'docx',
 'FAILED',
 NULL,
 NOW(),
 DATE_ADD(NOW(), INTERVAL 1 SECOND),
 DATE_ADD(NOW(), INTERVAL 2 SECOND),
 0);

-- 3. 查询测试数据
SELECT 
    id,
    file_id,
    user_id,
    original_name,
    file_type,
    parse_status,
    parsed_text_length,
    is_used,
    upload_time
FROM file_parse_task
ORDER BY upload_time DESC;

-- 4. 按用户统计文件数量
SELECT 
    u.username,
    COUNT(fpt.id) as file_count,
    SUM(CASE WHEN fpt.parse_status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN fpt.parse_status = 'FAILED' THEN 1 ELSE 0 END) as failed_count,
    SUM(CASE WHEN fpt.parse_status = 'PARSING' THEN 1 ELSE 0 END) as parsing_count
FROM file_parse_task fpt
JOIN user u ON fpt.user_id = u.id
GROUP BY u.username;

-- 5. 清理测试数据（执行完测试后运行）
-- DELETE FROM file_parse_task WHERE file_id LIKE 'test-file-%';

-- ============================================
-- 测试说明
-- ============================================
-- 
-- 测试步骤：
-- 1. 执行此脚本插入测试数据
-- 2. 使用Postman或curl测试以下接口：
--    a. GET /v1/file/parse-status/test-file-001-uuid
--       预期：返回SUCCESS状态，parsedTextLength=15234
--    
--    b. GET /v1/file/parse-status/test-file-003-uuid
--       预期：返回PARSING状态
--    
--    c. GET /v1/file/parse-status/test-file-004-uuid
--       预期：返回FAILED状态，包含errorMessage
--    
--    d. DELETE /v1/file/test-file-001-uuid
--       预期：返回true（未使用的文件可以删除）
--    
--    e. POST /chat/stream-with-files
--       Body: {
--         "question": "请总结这些文档",
--         "fileIds": ["test-file-001-uuid", "test-file-002-uuid"],
--         "model": "deepseek-v4-pro"
--       }
--       预期：SSE流式输出AI回答
-- 
-- 3. 测试完成后，取消注释第5步的DELETE语句清理数据
-- ============================================
