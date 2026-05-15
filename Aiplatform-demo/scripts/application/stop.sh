#!/bin/bash

# AI Gateway Platform 停止脚本

echo "========================================="
echo "AI Gateway Platform - 停止应用"
echo "========================================="

# 查找进程
PID=$(ps aux | grep 'ai-gateway-platform' | grep -v grep | awk '{print $2}')

if [ -z "$PID" ]; then
    echo "⚠️  应用未运行"
    exit 0
fi

echo "找到应用进程: PID=$PID"
read -p "是否停止应用? (y/n): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "正在停止应用..."
    kill $PID
    
    # 等待进程结束
    for i in {1..10}; do
        if ! ps -p $PID > /dev/null; then
            echo "✅ 应用已停止"
            exit 0
        fi
        echo -n "."
        sleep 1
    done
    
    # 强制停止
    echo ""
    echo "正常停止失败，强制停止..."
    kill -9 $PID
    echo "✅ 应用已强制停止"
else
    echo "已取消"
fi
