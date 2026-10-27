#!/bin/bash
set -e

# ============================================
# 凌点 AI 平台 - 停止前端服务
# 仅停止前端容器, 不影响 MySQL/Redis/Backend
# ============================================

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   停止前端服务${NC}"
echo -e "${CYAN}============================================${NC}"

is_running() {
    docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q true
}

if is_running lingdian-frontend; then
    echo -e "${YELLOW}→ 停止 lingdian-frontend...${NC}"
    docker stop lingdian-frontend >/dev/null 2>&1
    echo -e "${GREEN}✓ 前端已停止${NC}"
else
    echo -e "${GREEN}✓ 前端未在运行${NC}"
fi

echo -e "\n${GREEN}============================================${NC}"
echo -e "${GREEN}   前端服务已停止${NC}"
echo -e "${GREEN}============================================${NC}"
echo -e "  MySQL/Redis/后端 不受影响, 继续运行"
echo -e "  重新启动: ./scripts/start-frontend.sh"
echo ""
