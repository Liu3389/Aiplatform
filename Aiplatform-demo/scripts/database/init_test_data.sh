#!/bin/bash

# AI Gateway Platform - 测试数据初始化脚本
# 用于快速生成测试用户、API Key 等数据

MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="ai_gateway"

echo "========================================="
echo "AI Gateway Platform - 测试数据初始化"
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

# 重新初始化数据库
echo ""
echo "2. 重新初始化数据库..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "DROP DATABASE IF EXISTS $DB_NAME; CREATE DATABASE $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 数据库重建成功"
else
    echo "❌ 数据库重建失败"
    exit 1
fi

# 导入 schema
echo ""
echo "3. 导入表结构和初始数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/schema.sql 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 表结构导入成功"
else
    echo "❌ 表结构导入失败"
    exit 1
fi

# 执行其他迁移脚本
echo ""
echo "4. 执行数据库迁移脚本..."

# 执行 admin 增强迁移
if [ -f "./src/main/resources/sql/migration_admin_enhanced.sql" ]; then
    mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/migration_admin_enhanced.sql 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ Admin 增强迁移成功"
    else
        echo "⚠️  Admin 增强迁移失败（可能已存在）"
    fi
fi

# 执行聊天优化迁移
if [ -f "./src/main/resources/sql/migration_chat_optimization.sql" ]; then
    mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/migration_chat_optimization.sql 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ 聊天优化迁移成功"
    else
        echo "⚠️  聊天优化迁移失败（可能已存在）"
    fi
fi

# 执行通知迁移
if [ -f "./src/main/resources/sql/migration_notification.sql" ]; then
    mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/migration_notification.sql 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ 通知迁移成功"
    else
        echo "⚠️  通知迁移失败（可能已存在）"
    fi
fi

# 执行 P1 优化迁移
if [ -f "./src/main/resources/sql/migration_p1_optimization.sql" ]; then
    mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/migration_p1_optimization.sql 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ P1 优化迁移成功"
    else
        echo "⚠️  P1 优化迁移失败（可能已存在）"
    fi
fi

# 插入测试数据
echo ""
echo "5. 插入测试数据..."
# 使用force模式执行SQL，忽略表不存在的错误
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS --force $DB_NAME < ./src/main/resources/sql/init_test_data.sql > /tmp/init_test_data.log 2>&1

if [ $? -eq 0 ]; then
    echo "✅ 测试数据插入成功"
else
    echo "⚠️  测试数据插入有警告（可能某些表不存在）"
    echo "   查看日志: cat /tmp/init_test_data.log"
fi

# 验证数据
echo ""
echo "6. 验证数据..."
echo ""
echo "=== 模型配置 ==="
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SELECT model_name, provider, points_mode, points_per_request FROM model_config;" 2>/dev/null

echo ""
echo "=== 用户统计 ==="
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SELECT role, COUNT(*) as count FROM user GROUP BY role;" 2>/dev/null

echo ""
echo "=== API Key 统计 ==="
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SELECT COUNT(*) as total_api_keys FROM api_key;" 2>/dev/null

echo ""
echo "========================================="
echo "✅ 测试数据初始化完成！"
echo "========================================="
echo ""
echo "测试账号信息："
echo "- 超级管理员: superadmin01~05 / Test@123456 (点数: 10000, 余额: \$10000)"
echo "- 管理员: admin01~10 / Test@123456 (点数: 5000, 余额: \$5000)"
echo "- 普通用户: testuser01~20 / Test@123456 (不同余额等级)"
echo ""
echo "下一步："
echo "1. 重启应用: mvn spring-boot:run"
echo "2. 使用 Apifox 测试接口"
echo ""
