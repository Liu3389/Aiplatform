#!/bin/bash

# AI Gateway Platform - 完整数据库初始化脚本（包含测试数据）

MYSQL_HOST="localhost"
MYSQL_PORT="3307"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="ai_gateway"

echo "========================================="
echo "AI Gateway Platform - 完整数据库初始化"
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

# 删除并重建数据库
echo ""
echo "2. 重建数据库..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "DROP DATABASE IF EXISTS $DB_NAME;" 2>/dev/null
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "CREATE DATABASE $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 数据库 $DB_NAME 重建成功"
else
    echo "❌ 数据库重建失败"
    exit 1
fi

# 导入表结构
echo ""
echo "3. 创建表结构..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/schema.sql 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 表结构创建成功"
else
    echo "❌ 表结构创建失败"
    exit 1
fi

# 执行迁移脚本
echo ""
echo "4. 执行数据库迁移脚本..."

for migration_file in migration_admin_enhanced.sql migration_chat_optimization.sql migration_notification.sql migration_p1_optimization.sql; do
    if [ -f "./src/main/resources/sql/$migration_file" ]; then
        mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < "./src/main/resources/sql/$migration_file" 2>/dev/null
        if [ $? -eq 0 ]; then
            echo "✅ $migration_file 执行成功"
        else
            echo "⚠️  $migration_file 执行失败（可能已存在）"
        fi
    fi
done

# 插入基础数据
echo ""
echo "5. 插入基础数据..."
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/init_data.sql 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ 基础数据插入成功"
else
    echo "❌ 基础数据插入失败"
    exit 1
fi

# 插入测试数据
echo ""
echo "6. 插入测试数据..."
if [ -f "./src/main/resources/sql/testdata_large.sql" ]; then
    mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < ./src/main/resources/sql/testdata_large.sql 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo "✅ 测试数据插入成功"
    else
        echo "⚠️  测试数据插入失败"
    fi
else
    echo "⚠️  未找到测试数据文件，跳过"
fi

# 验证数据
echo ""
echo "7. 验证数据..."
echo ""

# 检查表数量
TABLE_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SHOW TABLES;" 2>/dev/null | wc -l)
echo "📊 表数量: $TABLE_COUNT"

# 检查模型配置
MODEL_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM platform_model_config;" 2>/dev/null)
if [ "$MODEL_COUNT" -gt 0 ] 2>/dev/null; then
    echo "✅ 模型配置: $MODEL_COUNT 条"
else
    echo "❌ 模型配置: 0 条"
fi

# 检查管理员账号
ADMIN_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM user WHERE role IN ('ADMIN', 'SUPER_ADMIN');" 2>/dev/null)
if [ "$ADMIN_COUNT" -gt 0 ] 2>/dev/null; then
    echo "✅ 管理员账号: $ADMIN_COUNT 个"
else
    echo "❌ 管理员账号: 0 个"
fi

# 检查用户数量
USER_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM user;" 2>/dev/null)
echo "👥 用户总数: $USER_COUNT"

# 检查对话数量
CONV_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM conversation;" 2>/dev/null)
echo "💬 对话总数: $CONV_COUNT"

# 检查消息数量
MSG_COUNT=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM chat_message;" 2>/dev/null)
echo "📝 消息总数: $MSG_COUNT"

# 显示可用模型
echo ""
echo "📋 可用模型列表："
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SELECT display_name AS '模型名称', actual_model AS '实际模型', CASE status WHEN 1 THEN '启用' ELSE '禁用' END AS '状态' FROM platform_model_config ORDER BY id;" 2>/dev/null

# 显示管理员账号
echo ""
echo "🔐 管理员账号："
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SELECT username AS '用户名', role AS '角色', CASE status WHEN 1 THEN '启用' ELSE '禁用' END AS '状态' FROM user WHERE role IN ('ADMIN', 'SUPER_ADMIN');" 2>/dev/null

echo ""
echo "========================================="
echo "✅ 数据库完整初始化完成！"
echo "========================================="
echo ""
echo "📌 下一步操作："
echo "1. 编译项目: mvn clean package -DskipTests"
echo "2. 启动应用: mvn spring-boot:run"
echo "3. 访问地址: http://localhost:8080"
echo "4. 管理员登录: superadmin / Test@123456"
echo ""
echo "📚 SQL脚本说明："
echo "- rebuild.sql: 一键重建数据库（删表+建表+基础数据）"
echo "- testdata_large.sql: 插入大量测试数据（50用户、110对话）"
echo "- clean.sql: 清空所有数据（保留表结构）"
echo ""
