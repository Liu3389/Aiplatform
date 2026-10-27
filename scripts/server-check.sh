#!/bin/bash
# 服务器状态检查脚本
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 1. 端口占用情况 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ss -tlnp 2>/dev/null | grep -E ':80|:8080|:3306|:6379' || echo '  (无相关端口监听)'"

echo ""
echo "===== 2. Docker 容器状态 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || echo 'docker 未安装或未运行'"

echo ""
echo "===== 3. Nginx 服务状态 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "systemctl is-active nginx 2>/dev/null && echo '-> nginx 正在运行(占用80)' || echo '-> nginx 未运行'"

echo ""
echo "===== 4. Docker 镜像列表 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}' 2>/dev/null | head -20"

echo ""
echo "===== 5. 服务器架构 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "uname -m && docker version --format '{{.Server.Os}}/{{.Server.Arch}}' 2>/dev/null"

echo ""
echo "===== 6. 磁盘/内存 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "df -h / && echo '---' && free -h"
