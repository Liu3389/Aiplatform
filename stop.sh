#!/bin/bash
set -e
cd "$(dirname "$0")"

CYAN='\033[0;36m'; GREEN='\033[0;32m'; NC='\033[0m'

source .env 2>/dev/null || true
MODE="${1:-${DEPLOY_MODE:-allinone}}"

case "$MODE" in
  frontend) COMPOSE_FILE="docker-compose.frontend.yml" ;;
  backend)  COMPOSE_FILE="docker-compose.backend.yml" ;;
  allinone) COMPOSE_FILE="docker-compose.allinone.yml" ;;
  *)        COMPOSE_FILE="docker-compose.allinone.yml" ;;
esac

echo -e "${CYAN}停止服务 (模式: ${MODE})...${NC}"
docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true

echo -e "${GREEN}所有服务已停止${NC}"
echo ""
echo -e "重新启动: ${CYAN}./start.sh${NC}"
echo -e "清除数据: ${CYAN}docker compose -f ${COMPOSE_FILE} down -v${NC} ${RED}(谨慎操作!)${NC}"
