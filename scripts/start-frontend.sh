#!/bin/bash
set -e

# ============================================
# 凌点 AI 平台 - 启动前端服务
# 若环境容器(MySQL/Redis/Backend)未运行则先启动
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
echo -e "${CYAN}   启动前端服务${NC}"
echo -e "${CYAN}============================================${NC}"

is_running() {
    docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q true
}

# 检查并启动依赖环境
for dep in mysql redis backend; do
    container="lingdian-$dep"
    if ! is_running "$container"; then
        echo -e "${YELLOW}→ 依赖 $container 未运行, 正在启动...${NC}"
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" start "$dep"
        echo -e "${GREEN}✓ $container 已启动${NC}"
        if [ "$dep" = "mysql" ]; then sleep 3; fi
    else
        echo -e "${GREEN}✓ 依赖 $container 已在运行${NC}"
    fi
done

# 启动前端
echo -e "\n${CYAN}启动前端...${NC}"
if is_running lingdian-frontend; then
    echo -e "  ${GREEN}✓ 前端已在运行${NC}"
else
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" start frontend
    echo -e "  ${GREEN}✓ 前端已启动${NC}"
fi

echo -e "\n${GREEN}============================================${NC}"
echo -e "${GREEN}   前端服务已就绪${NC}"
echo -e "${GREEN}============================================${NC}"
echo -e "  访问: http://localhost"
echo ""
