#!/bin/bash
# 查询管理员账号 (从服务器mysql容器)
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

ssh $SSH_OPTS -i "$KEY" "$HOST" 'cd /root/lingdian && set -a && . ./.env.prod && set +a && \
docker exec lingdian-mysql mysql -uroot -p"$MYSQL_PASSWORD" -N -e "\
SELECT id,username,email,role,status FROM ai_gateway.user WHERE role IN ('"'"'ADMIN'"'"','"'"'SUPER_ADMIN'"'"') LIMIT 10;" 2>/dev/null'
