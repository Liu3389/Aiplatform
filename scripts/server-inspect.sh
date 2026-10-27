#!/bin/bash
# 服务器部署目录检查
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 1. /root 目录 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ls -la /root/ 2>/dev/null | head -25"

echo ""
echo "===== 2. 找 compose / env / sql ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "find /root -maxdepth 4 \\( -name 'docker-compose*.yml' -o -name '.env.prod' -o -name 'rebuild.sql' -o -name '*.tar' \\) 2>/dev/null"

echo ""
echo "===== 3. docker compose 项目 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker compose ls 2>/dev/null; echo '---mysql项目标签---'; docker inspect lingdian-mysql --format '{{.Config.Labels}}' 2>/dev/null | tr ',' '\n' | grep -i project"

echo ""
echo "===== 4. .env.prod 内容(脱敏) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "f=\$(find /root -maxdepth 3 -name .env.prod 2>/dev/null | head -1); if [ -n \"\$f\" ]; then echo \"文件: \$f\"; sed 's/=.*/=***/' \"\$f\"; else echo '未找到.env.prod'; fi"
