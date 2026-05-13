#!/bin/bash
# 阿里云服务器部署脚本

set -e

echo "=========================================="
echo "  凌点 AI 网关 - 阿里云部署脚本"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查是否为root用户
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}请使用 root 用户运行此脚本${NC}"
    exit 1
fi

echo -e "${GREEN}[1/6] 检查 Docker 环境...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}Docker 未安装，正在安装...${NC}"
    curl -fsSL https://get.docker.com | sh
    systemctl start docker
    systemctl enable docker
fi
docker --version
docker compose version

echo -e "${GREEN}[2/6] 创建项目目录...${NC}"
mkdir -p /opt/lingdian-ai
cd /opt/lingdian-ai

echo -e "${GREEN}[3/6] 上传项目文件...${NC}"
echo -e "${YELLOW}请将项目文件上传到 /opt/lingdian-ai 目录${NC}"
echo -e "${YELLOW}方式: 在本地Mac执行: scp -r $HOME/Documents/Aiplatform/* root@YOUR_SERVER_IP:/opt/lingdian-ai/${NC}"
echo -e "${YELLOW}或者使用 git clone${NC}"

echo -e "${GREEN}[4/6] 配置环境变量...${NC}"
cat > .env << EOF
MYSQL_ROOT_PASSWORD=root123
MYSQL_DATABASE=ai_gateway
REDIS_PASSWORD=redis123
TZ=Asia/Shanghai
EOF

echo -e "${GREEN}[5/6] 启动服务...${NC}"
docker compose -f docker-compose.aliyun.yml down 2>/dev/null || true
docker compose -f docker-compose.aliyun.yml build --no-cache
docker compose -f docker-compose.aliyun.yml up -d

echo -e "${GREEN}[6/6] 检查服务状态...${NC}"
sleep 10
docker compose -f docker-compose.aliyun.yml ps

echo ""
echo -e "${GREEN}==========================================${NC}"
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${GREEN}==========================================${NC}"
echo -e "前端地址: http://YOUR_SERVER_IP"
echo -e "后端API: http://YOUR_SERVER_IP:8080"
echo ""
echo -e "${YELLOW}请确保阿里云安全组已开放 80 和 443 端口${NC}"
echo -e "${YELLOW}域名解析请设置为: A记录 -> YOUR_SERVER_IP${NC}"