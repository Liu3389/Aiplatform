#!/bin/bash
set -e

# ============================================
# 凌点 AI 平台 - 一键启动全部服务
# 启动顺序: MySQL → Redis → Backend → Frontend
# 若容器已运行则跳过
# ============================================

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# 自动检测 compose 文件: 优先 server.yml(服务器预构建镜像), 否则 prod.yml(本地源码构建)
if [ -f "$PROJECT_DIR/docker-compose.server.yml" ]; then
    COMPOSE_FILE="$PROJECT_DIR/docker-compose.server.yml"
else
    COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"
fi
ENV_FILE="$PROJECT_DIR/.env.prod"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   凌点 AI 平台 - 启动全部服务${NC}"
echo -e "${CYAN}============================================${NC}"

is_running() {
    docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q true
}

echo -e "\n${CYAN}[1/4] 检查 MySQL...${NC}"
if is_running lingdian-mysql; then
    echo -e "  ${GREEN}✓ MySQL 已在运行${NC}"
else
    echo -e "  ${YELLOW}→ 启动 MySQL...${NC}"
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" start mysql
    echo -e "  ${GREEN}✓ MySQL 已启动${NC}"
fi

echo -e "\n${CYAN}[2/4] 检查 Redis...${NC}"
if is_running lingdian-redis; then
    echo -e "  ${GREEN}✓ Redis 已在运行${NC}"
else
    echo -e "  ${YELLOW}→ 启动 Redis...${NC}"
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" start redis
    echo -e "  ${GREEN}✓ Redis 已启动${NC}"
fi

echo -e "\n${CYAN}[3/4] 检查后端...${NC}"
if is_running lingdian-backend; then
    echo -e "  ${GREEN}✓ 后端已在运行${NC}"
else
    echo -e "  ${YELLOW}→ 启动后端...${NC}"
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" start backend
    echo -e "  ${GREEN}✓ 后端已启动, 等待健康检查...${NC}"
    sleep 5
fi

echo -e "\n${CYAN}[4/4] 检查前端...${NC}"
if is_running lingdian-frontend; then
    echo -e "  ${GREEN}✓ 前端已在运行${NC}"
else
    echo -e "  ${YELLOW}→ 启动前端...${NC}"
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" start frontend
    echo -e "  ${GREEN}✓ 前端已启动${NC}"
fi

echo -e "\n${GREEN}============================================${NC}"
echo -e "${GREEN}   全部服务已启动！${NC}"
echo -e "${GREEN}============================================${NC}"
echo -e "  前端: http://localhost"
echo -e "  后端: http://localhost:8080/api"
echo -e "  查看状态: docker compose -f $COMPOSE_FILE ps"
echo ""
