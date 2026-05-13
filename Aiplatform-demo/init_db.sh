#!/bin/bash

# AI Gateway Platform - 数据库初始化脚本

VM_IP="10.211.55.10"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="ai_gateway"
SQL_FILE="$HOME/Documents/Aiplatform/Aiplatform-demo/src/main/resources/sql/schema.sql"

echo "========================================="
echo "AI Gateway Platform - 数据库初始化"
echo "========================================="

# 1. 创建数据库
echo ""
echo "1. 创建数据库..."
mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -e "CREATE DATABASE IF NOT EXISTS $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 数据库 $DB_NAME 创建成功"
else
    echo "❌ 数据库创建失败"
    exit 1
fi

# 2. 导入SQL文件
echo ""
echo "2. 导入建表脚本..."
mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < $SQL_FILE 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ SQL脚本导入成功"
else
    echo "❌ SQL脚本导入失败"
    exit 1
fi

# 3. 验证表
echo ""
echo "3. 验证表结构..."
TABLE_COUNT=$(mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SHOW TABLES;" 2>/dev/null | wc -l)

if [ "$TABLE_COUNT" -eq 5 ]; then
    echo "✅ 所有表已创建（5个表）"
    echo ""
    echo "表列表："
    mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SHOW TABLES;" 2>/dev/null
else
    echo "⚠️  表数量不正确：$TABLE_COUNT（应该是5个）"
    exit 1
fi

# 4. 验证初始数据
echo ""
echo "4. 验证初始数据..."
MODEL_COUNT=$(mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM model_config;" 2>/dev/null)

if [ "$MODEL_COUNT" -eq 2 ]; then
    echo "✅ 模型配置数据已插入（2条记录）"
    echo ""
    mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SELECT model_name, provider, status FROM model_config;" 2>/dev/null
else
    echo "⚠️  模型配置数据不正确：$MODEL_COUNT（应该是2条）"
fi

echo ""
echo "========================================="
echo "✅ 数据库初始化完成！"
echo "========================================="
echo ""
echo "可以启动应用了！"
