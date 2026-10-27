#!/bin/bash
set -e

# ============================================
# 凌点 AI 平台 - 一键停止全部服务
# 停止顺序: Frontend → Backend → Redis → MySQL
# ============================================

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# 自动检测 compose 文件: 优先 server.yml(服务器预构建镜像), 否则 prod.yml(本地源码构建)
if [ -f "$PROJECT_DIR/docker-compose.server.yml" ]; then
    COMPOSE_FILE="$PROJECT_DIR/docker-compose.server.yml"
else
    COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"
fi
ENV_FILE="$PROJECT_DIR/.env.prod"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   凌点 AI 平台 - 停止全部服务${NC}"
echo -e "${CYAN}============================================${NC}"

is_running() {
    docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q true
}

for svc in frontend backend redis mysql; do
    container="lingdian-$svc"
    echo -e "\n${CYAN}检查 $container...${NC}"
    if is_running "$container"; then
        echo -e "  ${YELLOW}→ 停止 $container...${NC}"
        docker stop "$container" >/dev/null 2>&1
        echo -e "  ${GREEN}✓ $container 已停止${NC}"
    else
        echo -e "  ${GREEN}✓ $container 未在运行, 跳过${NC}"
    fi
done

echo -e "\n${GREEN}============================================${NC}"
echo -e "${GREEN}   全部服务已停止${NC}"
echo -e "${GREEN}============================================${NC}"
echo -e "  数据卷保留, 重新启动: ./scripts/start-all.sh"
echo ""
