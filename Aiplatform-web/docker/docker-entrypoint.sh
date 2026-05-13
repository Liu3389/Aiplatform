#!/bin/sh
set -e

BACKEND_HOST="${BACKEND_HOST:-ai-gateway-app:8080}"

echo "========================================"
echo "  凌点 AI 前端 - Docker 启动"
echo "  后端上游: ${BACKEND_HOST}"
echo "========================================"

sed -i "s|__BACKEND_HOST__|${BACKEND_HOST}|g" /etc/nginx/conf.d/default.conf

exec nginx -g "daemon off;"
