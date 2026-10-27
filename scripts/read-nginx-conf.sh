#!/bin/bash
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== lingdian-ai.conf 完整内容 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cat /etc/nginx/conf.d/lingdian-ai.conf"

echo ""
echo "===== nginx.conf 主配置(去注释) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "grep -vE '^\s*#|^\s*$' /etc/nginx/nginx.conf"

echo ""
echo "===== 证书有效性 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "openssl x509 -in /etc/nginx/ssl/lingdian-ai.top_bundle.crt -noout -subject -dates 2>/dev/null"
