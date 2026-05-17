#!/bin/bash
# 凌点 AI 前端重启脚本
# 用法: ./start.sh
# 首次使用需 chmod +x start.sh

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "🛑 停止旧服务..."
# 杀掉 vite dev server (端口 3000-3005, 5173-5178)
for port in 3000 3001 3002 3003 3004 3005 5173 5174 5175 5176 5177 5178; do
  lsof -ti :$port 2>/dev/null | xargs kill 2>/dev/null
done
sleep 0.5

# 清理 Vite 缓存 (可选，偶尔能解决诡异问题)
# rm -rf "$PROJECT_DIR/node_modules/.vite" 2>/dev/null

echo "🚀 启动前端服务..."
cd "$PROJECT_DIR" && npm run dev