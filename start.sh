#!/bin/bash
set -e
cd "$(dirname "$0")"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; NC='\033[0m'

# ============================================
# 凌点 AI 网关 - 一键启动脚本
# 自动检测 .env 中的 DEPLOY_MODE，启动对应服务栈
# ============================================

source .env 2>/dev/null || true

BUILD=false; TUNNEL=false; COMPOSE_FILE=""; MODE=""

for arg in "$@"; do
  case $arg in
    --build)    BUILD=true ;;
    --tunnel)   TUNNEL=true ;;
    --frontend) MODE="frontend" ;;
    --backend)  MODE="backend" ;;
    --allinone) MODE="allinone" ;;
    -h|--help)
      echo "用法: ./start.sh [选项]"
      echo ""
      echo "选项:"
      echo "  --build     强制重新构建镜像（代码修改后需要）"
      echo "  --tunnel    同时启动 Cloudflare Tunnel（需配置 CF_TUNNEL_TOKEN）"
      echo ""
      echo "  --allinone  一键部署全部服务（默认）"
      echo "  --backend   仅部署后端 + MySQL + Redis"
      echo "  --frontend  仅部署前端（连接 BACKEND_HOST 远程后端）"
      echo ""
      echo "示例:"
      echo "  ./start.sh                    # 全栈启动"
      echo "  ./start.sh --build            # 修改代码后重建"
      echo "  ./start.sh --build --tunnel   # 重建 + 公网隧道"
      echo "  ./start.sh --backend          # 仅后端"
      echo "  ./start.sh --frontend         # 仅前端"
      exit 0
      ;;
  esac
done

# --- 确定部署模式 ---
MODE="${MODE:-${DEPLOY_MODE:-allinone}}"
case "$MODE" in
  frontend) COMPOSE_FILE="docker-compose.frontend.yml" ;;
  backend)  COMPOSE_FILE="docker-compose.backend.yml" ;;
  allinone) COMPOSE_FILE="docker-compose.allinone.yml" ;;
  *) echo -e "${RED}未知部署模式: $MODE${NC}"; exit 1 ;;
esac

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   凌点 AI 网关 - 部署启动${NC}"
echo -e "${CYAN}   模式: ${MODE}${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# --- 检查 Docker ---
if ! docker compose version &> /dev/null; then
  echo -e "${RED}[错误] 需要 Docker Compose V2${NC}"
  exit 1
fi

# --- 显示当前配置 ---
echo -e "${CYAN}当前配置:${NC}"
echo -e "  前端端口:  ${YELLOW}${FRONTEND_PORT:-80}${NC}"
echo -e "  后端端口:  ${YELLOW}${BACKEND_PORT:-8080}${NC}"
[ "$MODE" = "frontend" ] && echo -e "  后端地址:  ${YELLOW}${BACKEND_HOST:-localhost:8080}${NC}"
[ "$MODE" != "frontend" ] && echo -e "  MySQL端口: ${YELLOW}${MYSQL_PORT:-3307}${NC}  Redis端口: ${YELLOW}${REDIS_PORT:-6380}${NC}"
[ "$TUNNEL" = true ] && echo -e "  Cloudflare: ${GREEN}已启用${NC}"
echo ""

# --- 停止旧容器 ---
echo -e "${CYAN}[1/4] 清理旧容器...${NC}"
docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true

# --- 构建启动 ---
CMD=(docker compose -f "$COMPOSE_FILE")
if [ "$TUNNEL" = true ] && [ "$MODE" = "allinone" ]; then
  if [ -z "${CF_TUNNEL_TOKEN}" ]; then
    echo -e "${RED}[错误] 请在 .env 中设置 CF_TUNNEL_TOKEN${NC}"
    exit 1
  fi
  CMD+=(--profile tunnel)
fi
CMD+=(up)
[ "$BUILD" = true ] && CMD+=(--build)
CMD+=(-d --remove-orphans)

echo -e "${CYAN}[2/4] 构建并启动服务...${NC}"
"${CMD[@]}"

# --- 等待健康检查 ---
echo -e "${CYAN}[3/4] 等待服务就绪...${NC}"
MAX_WAIT=180; ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
  FRONTEND_OK=$(docker inspect lingdian-frontend --format='{{.State.Health.Status}}' 2>/dev/null || echo "")
  BACKEND_OK=$(docker inspect lingdian-backend --format='{{.State.Health.Status}}' 2>/dev/null || echo "")

  if [ "$MODE" = "frontend" ]; then
    [ "$FRONTEND_OK" = "healthy" ] && break
    echo -ne "\r  前端:${FRONTEND_OK:-...} (${ELAPSED}s)"
  elif [ "$MODE" = "backend" ]; then
    [ "$BACKEND_OK" = "healthy" ] && break
    echo -ne "\r  后端:${BACKEND_OK:-...} (${ELAPSED}s)"
  else
    [ "$FRONTEND_OK" = "healthy" ] && [ "$BACKEND_OK" = "healthy" ] && break
    echo -ne "\r  前端:${FRONTEND_OK:-...} 后端:${BACKEND_OK:-...} (${ELAPSED}s)"
  fi
  sleep 5; ELAPSED=$((ELAPSED + 5))
done
echo ""

# --- 验证 ---
echo -e "${CYAN}[4/4] 验证服务...${NC}"
FRONTEND_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT:-80}/" 2>/dev/null || echo "---")
BACKEND_HEALTH=$(curl -s "http://localhost:${BACKEND_PORT:-8080}/api/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}   部署完成！${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "  前端页面:  ${CYAN}http://localhost:${FRONTEND_PORT:-80}${NC}  (HTTP ${FRONTEND_HTTP})"
echo -e "  后端 API:  ${CYAN}http://localhost:${BACKEND_PORT:-8080}/api${NC}  (${BACKEND_HEALTH})"
if [ "$MODE" != "frontend" ]; then
  echo -e "  MySQL:     ${CYAN}localhost:${MYSQL_PORT:-3307}${NC}  (root)"
  echo -e "  Redis:     ${CYAN}localhost:${REDIS_PORT:-6380}${NC}"
fi
echo ""
echo -e "  测试账号:  ${YELLOW}superadmin / admin123${NC}"
echo ""
echo -e "  管理命令:"
echo -e "    ${CYAN}./stop.sh${NC}        停止所有服务"
echo -e "    ${CYAN}./backup.sh${NC}      备份数据"
echo -e "    ${CYAN}./restore.sh${NC}     恢复数据"
echo -e "    ${CYAN}./migrate.sh${NC}     跨设备迁移"
echo ""
