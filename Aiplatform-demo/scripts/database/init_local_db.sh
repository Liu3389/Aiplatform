#!/bin/bash

# AI Gateway Platform - 本地数据库初始化脚本

MYSQL_HOST="localhost"
MYSQL_PORT="3307"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="ai_gateway"
SQL_FILE="./src/main/resources/sql/schema.sql"

echo "========================================="
echo "AI Gateway Platform - 本地数据库初始化"
echo "========================================="

# 检查 MySQL 连接
echo ""
echo "1. 检查 MySQL 连接..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "SELECT 1;" > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "❌ 无法连接到 MySQL，请检查："
    echo "   - MySQL 是否正在运行"
    echo "   - 端口是否正确 (3307)"
    echo "   - 用户名和密码是否正确"
    exit 1
fi
echo "✅ MySQL 连接成功"

# 创建数据库
echo ""
echo "2. 创建数据库..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "CREATE DATABASE IF NOT EXISTS $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 数据库 $DB_NAME 创建成功"
else
    echo "❌ 数据库创建失败"
    exit 1
fi

# 导入 SQL 文件
echo ""
echo "3. 导入建表脚本..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < $SQL_FILE 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ SQL 脚本导入成功"
else
    echo "❌ SQL 脚本导入失败"
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

# 验证表
echo ""
echo "5. 验证表结构..."
TABLE_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SHOW TABLES;" 2>/dev/null | wc -l)

echo "✅ 已创建 $TABLE_COUNT 个表"
echo ""
echo "表列表："
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SHOW TABLES;" 2>/dev/null

# 验证初始数据
echo ""
echo "6. 验证初始数据..."
MODEL_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM model_config;" 2>/dev/null)

if [ "$MODEL_COUNT" -gt 0 ]; then
    echo "✅ 模型配置数据已插入（$MODEL_COUNT 条记录）"
    echo ""
    echo "可用模型："
    mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SELECT model_name, provider, points_mode, status FROM model_config;" 2>/dev/null
else
    echo "⚠️  模型配置数据为空"
fi

echo ""
echo "========================================="
echo "✅ 数据库初始化完成！"
echo "========================================="
echo ""
echo "下一步："
echo "1. 如需插入测试数据，请运行: ./init_test_data.sh"
echo "2. 编译项目: mvn clean package -DskipTests"
echo "3. 启动应用: ./start.sh 或 java -jar target/ai-gateway-platform-1.0.0.jar"
echo "4. 查看文档: cat QUICK_START.md"
echo ""
