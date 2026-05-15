#!/bin/bash

# AI Gateway Platform 快速启动脚本

echo "========================================="
echo "AI Gateway Platform - 快速启动脚本"
echo "========================================="
echo ""

# 检查Java版本
echo "1. 检查Java环境..."
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
if [[ $JAVA_VERSION < "17" ]]; then
    echo "❌ 错误: 需要JDK 17或更高版本，当前版本: $JAVA_VERSION"
    exit 1
fi
echo "✅ Java版本: $JAVA_VERSION"

# 检查Maven
echo ""
echo "2. 检查Maven环境..."
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: Maven未安装"
    exit 1
fi
MVN_VERSION=$(mvn -version 2>&1 | head -n 1)
echo "✅ $MVN_VERSION"

# 清理并编译
echo ""
echo "3. 清理并编译项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi
echo "✅ 编译成功"

# 检查配置文件
echo ""
echo "4. 检查配置文件..."
if [ ! -f "src/main/resources/application.yml" ]; then
    echo "❌ 配置文件不存在"
    exit 1
fi
echo "✅ 配置文件存在"
echo "⚠️  请确保已修改application.yml中的数据库和Redis配置"

# 询问是否启动
echo ""
read -p "是否现在启动应用? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "已取消启动"
    echo "你可以稍后运行: java -jar target/ai-gateway-platform-1.0.0.jar"
    exit 0
fi

# 启动应用
echo ""
echo "5. 启动应用..."
echo "日志文件: app.log"
nohup java -jar target/ai-gateway-platform-1.0.0.jar > app.log 2>&1 &
APP_PID=$!
echo "✅ 应用已启动，PID: $APP_PID"

# 等待应用启动
echo ""
echo "等待应用启动..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/actuator/health > /dev/null 2>&1 || \
       curl -s http://localhost:8080/api/auth/userinfo?userId=1 > /dev/null 2>&1; then
        echo "✅ 应用启动成功！"
        echo ""
        echo "========================================="
        echo "访问地址: http://localhost:8080/api"
        echo "查看日志: tail -f app.log"
        echo "停止应用: kill $APP_PID"
        echo "========================================="
        exit 0
    fi
    echo -n "."
    sleep 1
done

echo ""
echo "⚠️  应用启动超时，请检查日志: tail -f app.log"
exit 1
