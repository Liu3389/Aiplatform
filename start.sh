#!/bin/bash
set -e

# ============================================
# 凌点 AI 网关 - 本地一键启动脚本
# 1. Docker 启动 MySQL + Redis (基础设施)
# 2. 原生启动后端 Spring Boot (端口 8080, 热重载)
# 3. 原生启动前端 Vite 开发服务器 (端口 3000, 热重载)
# ============================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/Aiplatform-demo"
FRONTEND_DIR="$PROJECT_DIR/Aiplatform-web"
PID_DIR="$PROJECT_DIR/.runtime"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; NC='\033[0m'

mkdir -p "$PID_DIR"

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   凌点 AI 网关 - 本地启动${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

check_cmd() {
  if ! command -v "$1" &>/dev/null; then
    echo -e "${RED}[错误] 未找到 $1，请先安装${NC}"
    exit 1
  fi
}

check_cmd java
check_cmd npm
check_cmd docker

# 检查 Java 版本 >= 21
JAVA_VER=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"' || echo "0")
if [ "$JAVA_VER" -lt 21 ]; then
  echo -e "${RED}[错误] 需要 Java 21+，当前: $(java -version 2>&1 | head -1)${NC}"
  exit 1
fi

# ============================================
# [1/5] Docker 启动 MySQL + Redis
# ============================================
echo -e "${CYAN}[1/5] 启动基础设施 (Docker: MySQL + Redis)...${NC}"

cd "$PROJECT_DIR"
docker-compose up -d mysql redis 2>&1 | sed 's/^/  /'

# 等待 MySQL 就绪
echo -e "  等待 MySQL 就绪..."
MAX_WAIT=60; ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
  if docker exec lingdian-mysql mysqladmin ping -h localhost -uroot -p123456 &>/dev/null 2>&1; then
    echo -e "  ${GREEN}MySQL 已就绪 (${ELAPSED}s)${NC}"
    break
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
done
if [ $ELAPSED -ge $MAX_WAIT ]; then
  echo -e "  ${RED}MySQL 启动超时, 请检查: docker logs lingdian-mysql${NC}"
  exit 1
fi

# 等待 Redis 就绪
if docker exec lingdian-redis redis-cli ping &>/dev/null 2>&1; then
  echo -e "  ${GREEN}Redis 已就绪${NC}"
else
  echo -e "  ${YELLOW}Redis 未就绪, 稍后重试...${NC}"
  sleep 3
fi
echo ""

# ============================================
# [2/5] 停止旧进程
# ============================================
echo -e "${CYAN}[2/5] 停止旧后端/前端进程...${NC}"

if [ -f "$PID_DIR/backend.pid" ]; then
  OLD_PID=$(cat "$PID_DIR/backend.pid")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    kill "$OLD_PID" 2>/dev/null || true
    sleep 2
    kill -9 "$OLD_PID" 2>/dev/null || true
    echo "  已停止旧后端 (PID: $OLD_PID)"
  fi
  rm -f "$PID_DIR/backend.pid"
fi

FRONTEND_PIDS=$(lsof -ti :3000 2>/dev/null || true)
if [ -n "$FRONTEND_PIDS" ]; then
  for pid in $FRONTEND_PIDS; do kill "$pid" 2>/dev/null || true; done
  echo "  已停止旧前端"
fi

# 清理可能残留的 8080 端口
BACKEND_PIDS=$(lsof -ti :8080 2>/dev/null || true)
if [ -n "$BACKEND_PIDS" ]; then
  for pid in $BACKEND_PIDS; do kill "$pid" 2>/dev/null || true; done
  echo "  已清理 8080 端口"
fi
sleep 1
echo ""

# ============================================
# [3/5] 启动后端 Spring Boot
# ============================================
echo -e "${CYAN}[3/5] 启动后端 Spring Boot (端口 8080)...${NC}"

cd "$BACKEND_DIR"
nohup mvn spring-boot:run -q > "$PID_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$PID_DIR/backend.pid"
echo "  后端 PID: $BACKEND_PID"

# ============================================
# [4/5] 等待后端就绪
# ============================================
echo -e "${CYAN}[4/5] 等待后端就绪...${NC}"
MAX_WAIT=120; ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
  # 用 Knife4j/OpenAPI 端点做健康检查
  if curl -s "http://localhost:8080/api/v3/api-docs" 2>/dev/null | grep -q "openapi"; then
    echo -e "  ${GREEN}后端已就绪 (${ELAPSED}s)${NC}"
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
  echo -e "  ${YELLOW}后端启动超时, 请检查日志: tail -f $PID_DIR/backend.log${NC}"
fi

# ============================================
# [5/5] 启动前端 Vite
# ============================================
echo -e "${CYAN}[5/5] 启动前端 Vite 开发服务器 (端口 3000)...${NC}"

cd "$FRONTEND_DIR"
if [ ! -d "node_modules" ]; then
  echo "  首次启动, 安装依赖..."
  npm install --silent 2>/dev/null
fi
nohup npx vite --host 0.0.0.0 > "$PID_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "  前端 PID: $FRONTEND_PID"

sleep 4
if curl -s -o /dev/null -w "%{http_code}" "http://localhost:3000" 2>/dev/null | grep -q "200\|302\|304"; then
  echo -e "  ${GREEN}前端已就绪${NC}"
else
  echo -e "  ${YELLOW}前端可能尚未完全就绪, 请稍候...${NC}"
fi

# ============================================
# 完成
# ============================================
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}   启动完成！${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "  前端页面:  ${CYAN}http://localhost:3000${NC}"
echo -e "  后端 API:  ${CYAN}http://localhost:8080/api${NC}"
echo -e "  接口文档:  ${CYAN}http://localhost:8080/api/doc.html${NC}"
echo ""
echo -e "  ${YELLOW}基础设施 (Docker):${NC}"
echo -e "    MySQL:  localhost:3306  (root/123456, 库 ai_gateway)"
echo -e "    Redis:  localhost:6380"
echo -e "    查看:   docker-compose ps"
echo -e "    停止:   docker-compose down"
echo ""
echo -e "  ${YELLOW}日志文件:${NC}"
echo -e "    后端: tail -f .runtime/backend.log"
echo -e "    前端: tail -f .runtime/frontend.log"
echo ""
echo -e "  停止全部: ${CYAN}./stop.sh${NC}"
