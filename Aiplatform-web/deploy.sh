#!/bin/bash
# ============================================
# 凌点 AI 前端 - 独立部署脚本（仅前端）
# ============================================
# 前置条件: Docker 已安装
# 使用场景: 后端已部署在远程服务器，只需部署前端
#
# 使用方法:
#   ./deploy.sh                   默认连接 10.211.55.10:8080
#   BACKEND_HOST=192.168.1.100:8080 ./deploy.sh
#   ./deploy.sh build             仅构建镜像，不启动
#   ./deploy.sh export            导出镜像 tar 包
# ============================================
set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "  凌点 AI 前端 - Docker 部署"
echo "========================================="
echo ""

ARCH=$(uname -m)
case "$ARCH" in
  arm64|aarch64) DOCKER_PLATFORM="linux/arm64" ;;
  x86_64|amd64)  DOCKER_PLATFORM="linux/amd64" ;;
  *) echo -e "${RED}不支持的架构: $ARCH${NC}"; exit 1 ;;
esac
echo "平台: $(uname -s) / $ARCH → Docker ${DOCKER_PLATFORM}"

if ! command -v docker &>/dev/null; then
  echo -e "${RED}❌ Docker 未安装${NC}"
  echo "  macOS: brew install --cask docker"
  echo "  Linux: curl -fsSL https://get.docker.com | sh"
  exit 1
fi

echo -e "${YELLOW}[1/3] 构建前端镜像 (${DOCKER_PLATFORM})...${NC}"
docker build --platform "$DOCKER_PLATFORM" -t lingdian-ai-frontend:latest -f docker/Dockerfile .
echo -e "${GREEN}✅ 镜像构建完成${NC}"

if [ "$1" = "build" ]; then
  echo -e "${GREEN}✅ 镜像已构建: lingdian-ai-frontend:latest${NC}"
  exit 0
fi

if [ "$1" = "export" ]; then
  echo -e "${YELLOW}[2/3] 导出镜像包...${NC}"
  docker save lingdian-ai-frontend:latest -o lingdian-ai-frontend.tar
  SIZE=$(du -h lingdian-ai-frontend.tar | cut -f1)
  echo -e "${GREEN}✅ lingdian-ai-frontend.tar (${SIZE})${NC}"
  echo ""
  echo "传输到服务器:"
  echo "  scp lingdian-ai-frontend.tar user@server:/opt/lingdian-ai/"
  echo "  ssh user@server 'docker load -i /opt/lingdian-ai/lingdian-ai-frontend.tar'"
  exit 0
fi

echo -e "${YELLOW}[2/3] 停止旧容器...${NC}"
docker stop lingdian-frontend 2>/dev/null || true
docker rm lingdian-frontend 2>/dev/null || true

BACKEND_HOST="${BACKEND_HOST:-10.211.55.10:8080}"
echo -e "${YELLOW}[3/3] 启动前端容器...${NC}"
echo "  后端代理目标: ${BACKEND_HOST}"

docker run -d \
  --name lingdian-frontend \
  -p 80:80 \
  -e BACKEND_HOST="${BACKEND_HOST}" \
  --restart unless-stopped \
  lingdian-ai-frontend:latest

sleep 2

if docker ps | grep -q lingdian-frontend; then
  echo ""
  echo -e "${GREEN}=========================================${NC}"
  echo -e "${GREEN}  ✅ 凌点 AI 前端启动成功！${NC}"
  echo -e "${GREEN}=========================================${NC}"
  echo ""
  echo "  访问地址: http://localhost"
  echo "  后端代理: ${BACKEND_HOST}"
  echo ""
  echo "  查看日志: docker logs -f lingdian-frontend"
  echo "  停止服务: docker stop lingdian-frontend"
  echo ""
  echo "  如需切换后端地址:"
  echo "    docker stop lingdian-frontend && docker rm lingdian-frontend"
  echo "    BACKEND_HOST=YOUR_HOST:PORT ./deploy.sh"
  echo ""
else
  echo -e "${RED}❌ 启动失败，查看日志: docker logs lingdian-frontend${NC}"
  exit 1
fi
