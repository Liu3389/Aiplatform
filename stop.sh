#!/bin/bash
set -e

# ============================================
# 凌点 AI 网关 - 停止脚本
# 停止后端 Spring Boot + 前端 Vite 开发服务器
# ============================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$PROJECT_DIR/.runtime"

CYAN='\033[0;36m'; GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${CYAN}停止服务...${NC}"

STOPPED=0

# 停止后端
if [ -f "$PID_DIR/backend.pid" ]; then
  BACKEND_PID=$(cat "$PID_DIR/backend.pid")
  if kill -0 "$BACKEND_PID" 2>/dev/null; then
    # 先杀 mvn 主进程，再清所有 java 子进程
    kill "$BACKEND_PID" 2>/dev/null || true
    sleep 1
    # 杀掉所有相关的 java 进程
    pkill -f "spring-boot:run" 2>/dev/null || true
    pkill -f "AiGatewayApplication" 2>/dev/null || true
    echo -e "  ${GREEN}后端已停止 (PID: $BACKEND_PID)${NC}"
    STOPPED=1
  fi
  rm -f "$PID_DIR/backend.pid"
else
  # 尝试通过进程名杀
  pkill -f "spring-boot:run" 2>/dev/null && echo -e "  ${GREEN}后端已停止 (按进程名)${NC}" && STOPPED=1 || true
  pkill -f "AiGatewayApplication" 2>/dev/null || true
fi

# 停止前端 Vite (端口 3000)
FRONTEND_PIDS=$(lsof -ti :3000 2>/dev/null || true)
if [ -n "$FRONTEND_PIDS" ]; then
  for pid in $FRONTEND_PIDS; do
    kill "$pid" 2>/dev/null || true
  done
  echo -e "  ${GREEN}前端已停止${NC}"
  STOPPED=1
fi

# 释放 8080 端口
BACKEND_PORT_PIDS=$(lsof -ti :8080 2>/dev/null || true)
if [ -n "$BACKEND_PORT_PIDS" ]; then
  for pid in $BACKEND_PORT_PIDS; do
    kill "$pid" 2>/dev/null || true
  done
  echo -e "  ${GREEN}已释放 8080 端口${NC}"
  STOPPED=1
fi

if [ "$STOPPED" -eq 0 ]; then
  echo -e "  ${CYAN}没有运行中的服务${NC}"
fi

# 停止 Docker 基础设施 (传 --with-docker 参数时)
if [ "$1" = "--with-docker" ]; then
  echo -e "${CYAN}停止 Docker 基础设施 (MySQL + Redis)...${NC}"
  cd "$PROJECT_DIR"
  docker-compose down 2>&1 | sed 's/^/  /'
  echo -e "  ${GREEN}Docker 基础设施已停止${NC}"
fi

echo -e "${GREEN}所有服务已停止${NC}"
echo ""
echo -e "重新启动: ${CYAN}./start.sh${NC}"
echo -e "停止并关闭 Docker: ${CYAN}./stop.sh --with-docker${NC}"