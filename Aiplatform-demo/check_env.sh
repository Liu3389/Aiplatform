#!/bin/bash

# AI Gateway Platform - 完整环境检查脚本

VM_IP="10.211.55.10"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="ai_gateway"

echo "========================================="
echo "AI Gateway Platform - 环境检查"
echo "========================================="

ERROR_COUNT=0

# 1. 检查网络连通性
echo ""
echo "1. 检查网络连通性..."
if ping -c 1 -W 1 $VM_IP > /dev/null 2>&1; then
    echo "✅ 可以ping通虚拟机 ($VM_IP)"
else
    echo "❌ 无法ping通虚拟机 ($VM_IP)"
    echo "   请检查："
    echo "   - 虚拟机是否启动"
    echo "   - 网络连接是否正常"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

# 2. 检查MySQL
echo ""
echo "2. 检查MySQL连接..."
if mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -e "SELECT 1;" > /dev/null 2>&1; then
    echo "✅ MySQL连接正常"
    
    # 检查数据库
    if mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME;" > /dev/null 2>&1; then
        echo "✅ 数据库 $DB_NAME 存在"
        
        # 检查表
        TABLE_COUNT=$(mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SHOW TABLES;" 2>/dev/null | wc -l)
        if [ "$TABLE_COUNT" -eq 5 ]; then
            echo "✅ 所有表已创建（5个表）"
            
            # 显示表名
            echo "   表列表："
            mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -e "USE $DB_NAME; SHOW TABLES;" 2>/dev/null | grep -v "Tables_in" | sed 's/^/   - /'
        else
            echo "⚠️  表数量不正确：$TABLE_COUNT（应该是5个）"
            echo "   建议执行: ./init_db.sh 初始化数据库"
            ERROR_COUNT=$((ERROR_COUNT + 1))
        fi
        
        # 检查初始数据
        MODEL_COUNT=$(mysql -h $VM_IP -u $MYSQL_USER -p$MYSQL_PASS -N -e "USE $DB_NAME; SELECT COUNT(*) FROM model_config;" 2>/dev/null)
        if [ "$MODEL_COUNT" -eq 2 ]; then
            echo "✅ 模型配置数据正常（2条记录）"
        else
            echo "⚠️  模型配置数据不正确：$MODEL_COUNT（应该是2条）"
        fi
    else
        echo "❌ 数据库 $DB_NAME 不存在"
        echo "   建议执行: ./init_db.sh 初始化数据库"
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
else
    echo "❌ MySQL连接失败"
    echo "   请检查："
    echo "   1. MySQL服务是否运行：sudo systemctl status mysql"
    echo "   2. 防火墙是否开放3306端口"
    echo "   3. MySQL是否允许远程访问"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

# 3. 检查Redis
echo ""
echo "3. 检查Redis连接..."
REDIS_PING=$(redis-cli -h $VM_IP -p 6379 -a 123321 ping 2>/dev/null)
if [ "$REDIS_PING" == "PONG" ]; then
    echo "✅ Redis连接正常"
    
    # 检查Redis版本
    REDIS_VERSION=$(redis-cli -h $VM_IP -p 6379 -a 123321 info server 2>/dev/null | grep redis_version | cut -d: -f2 | tr -d '\r')
    echo "   Redis版本: $REDIS_VERSION"
else
    echo "❌ Redis连接失败"
    echo "   请检查："
    echo "   1. Redis服务是否运行：sudo systemctl status redis"
    echo "   2. 防火墙是否开放6379端口"
    echo "   3. Redis配置是否允许远程访问"
    echo "   4. 密码是否正确（当前配置: 123321）"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

# 4. 检查Java
echo ""
echo "4. 检查Java环境..."
if command -v java > /dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo "✅ Java已安装: $JAVA_VERSION"
    
    # 检查版本是否>=17
    JAVA_MAJOR=$(echo $JAVA_VERSION | cut -d. -f1)
    if [ "$JAVA_MAJOR" -ge 17 ]; then
        echo "✅ Java版本满足要求（>=17）"
    else
        echo "⚠️  Java版本过低：$JAVA_VERSION（需要>=17）"
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
else
    echo "❌ Java未安装"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

# 5. 检查Maven
echo ""
echo "5. 检查Maven环境..."
if command -v mvn > /dev/null 2>&1; then
    MVN_VERSION=$(mvn -version 2>&1 | head -n 1)
    echo "✅ Maven已安装"
    echo "   $MVN_VERSION"
else
    echo "❌ Maven未安装"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

# 6. 检查配置文件
echo ""
echo "6. 检查配置文件..."
CONFIG_FILE="src/main/resources/application.yml"
if [ -f "$CONFIG_FILE" ]; then
    echo "✅ 配置文件存在: $CONFIG_FILE"
    
    # 检查关键配置
    if grep -q "10.211.55.10" "$CONFIG_FILE"; then
        echo "✅ 数据库和Redis地址配置正确"
    else
        echo "⚠️  配置文件中的IP地址可能不正确"
    fi
    
    if grep -q "sk-your-openai-api-key-here" "$CONFIG_FILE"; then
        echo "⚠️  OpenAI API Key未配置（使用占位符）"
        echo "   如需测试对话功能，请修改 application.yml 中的 openai.api-key"
    else
        echo "✅ OpenAI API Key已配置"
    fi
else
    echo "❌ 配置文件不存在: $CONFIG_FILE"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

# 总结
echo ""
echo "========================================="
if [ $ERROR_COUNT -eq 0 ]; then
    echo "✅ 环境检查通过！可以启动应用"
    echo "========================================="
    echo ""
    echo "启动方式："
    echo "  1. IntelliJ IDEA: 点击 Debug 按钮"
    echo "  2. 命令行: ./start.sh"
    echo "  3. Maven: mvn spring-boot:run"
    echo ""
    echo "访问地址: http://localhost:8080/api"
else
    echo "❌ 发现 $ERROR_COUNT 个问题，请修复后重试"
    echo "========================================="
    exit 1
fi
