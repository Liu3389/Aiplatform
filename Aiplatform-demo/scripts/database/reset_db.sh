#!/bin/bash

# AI Gateway Platform - 快速重置数据库脚本
# 用途：清空数据后重新初始化（比重建更快）

MYSQL_HOST="localhost"
MYSQL_PORT="3307"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="ai_gateway"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_DIR="$SCRIPT_DIR/../../src/main/resources/sql"

echo "========================================="
echo "AI Gateway Platform - 快速重置数据库"
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

# 清空所有数据
echo ""
echo "2. 清空所有数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < "$SQL_DIR/clean.sql" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 数据清空成功"
else
    echo "❌ 数据清空失败"
    exit 1
fi

# 重新插入基础数据
echo ""
echo "3. 重新插入基础数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < "$SQL_DIR/rebuild.sql" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 基础数据插入成功"
else
    echo "❌ 基础数据插入失败"
    exit 1
fi

# 验证数据
echo ""
echo "4. 验证数据..."
USER_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM user;" 2>/dev/null)
MODEL_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM platform_model_config;" 2>/dev/null)

echo "👥 用户数: $USER_COUNT"
echo "📋 模型数: $MODEL_COUNT"

if [ "$MODEL_COUNT" -gt 0 ] 2>/dev/null; then
    echo "✅ 重置成功！"
else
    echo "❌ 重置失败"
    exit 1
fi

echo ""
echo "========================================="
echo "✅ 数据库重置完成！"
echo "========================================="
echo ""
echo "提示："
echo "- 如需添加测试数据，请执行: ./init_full.sh"
echo "- 管理员账号: superadmin / Test@123456"
echo ""
