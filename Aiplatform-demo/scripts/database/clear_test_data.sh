#!/bin/bash

# AI Gateway Platform - 清除测试数据脚本
# 用于快速清除所有测试数据，保留表结构和基础配置

MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="ai_gateway"

echo "========================================="
echo "AI Gateway Platform - 清除测试数据"
echo "========================================="

# 检查 MySQL 连接
echo ""
echo "1. 检查 MySQL 连接..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "SELECT 1;" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "❌ 无法连接到 MySQL"
    exit 1
fi
echo "✅ MySQL 连接成功"

# 确认操作
echo ""
echo "⚠️  警告: 此操作将清除所有测试数据!"
echo "   - 用户数据 (除基础模型配置外)"
echo "   - API Keys"
echo "   - 调用日志"
echo "   - 计费记录"
echo "   - 点数账单"
echo "   - 会话和消息数据"
echo "   - 通知数据"
echo ""
read -p "确定要继续吗? (y/N): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ 操作已取消"
    exit 0
fi

# 清除测试数据
echo ""
echo "2. 清除测试数据..."

# 按依赖关系逆序清除数据
echo "   - 清除计费记录..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE billing_record;" 2>/dev/null

echo "   - 清除调用日志..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE call_log;" 2>/dev/null

echo "   - 清除API Keys..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE api_key;" 2>/dev/null

echo "   - 清除用户数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE user;" 2>/dev/null

echo "   - 清除点数账单..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE points_bill;" 2>/dev/null

echo "   - 清除会话数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE conversation;" 2>/dev/null

echo "   - 清除聊天消息..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE chat_message;" 2>/dev/null

echo "   - 清除聊天历史..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE chat_message_history;" 2>/dev/null

echo "   - 清除通知数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE user_notification;" 2>/dev/null

echo "   - 清除系统公告..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE system_announcement;" 2>/dev/null

echo "   - 清除操作日志..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE admin_operation_log;" 2>/dev/null

echo "   - 清除统计数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE platform_statistics;" 2>/dev/null

echo "   - 清除代金券..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE coupon;" 2>/dev/null

echo "   - 清除系统通知..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE system_notification;" 2>/dev/null

echo "   - 清除用户API Key..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; TRUNCATE TABLE user_api_key;" 2>/dev/null

# 验证清除结果
echo ""
echo "3. 验证清除结果..."
echo ""
echo "=== 剩余数据统计 ==="
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; 
SELECT 'user' as table_name, COUNT(*) as record_count FROM user
UNION ALL
SELECT 'api_key', COUNT(*) FROM api_key
UNION ALL
SELECT 'call_log', COUNT(*) FROM call_log
UNION ALL
SELECT 'billing_record', COUNT(*) FROM billing_record
UNION ALL
SELECT 'points_bill', COUNT(*) FROM points_bill
UNION ALL
SELECT 'conversation', COUNT(*) FROM conversation
UNION ALL
SELECT 'chat_message', COUNT(*) FROM chat_message
UNION ALL
SELECT 'model_config', COUNT(*) FROM model_config;" 2>/dev/null

echo ""
echo "========================================="
echo "✅ 测试数据清除完成！"
echo "========================================="
echo ""
echo "说明:"
echo "- 所有测试用户和关联数据已被清除"
echo "- 模型配置等基础数据保持不变"
echo "- 可以重新运行 init_test_data.sh 来生成新的测试数据"
echo ""
