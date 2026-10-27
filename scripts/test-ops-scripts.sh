#!/bin/bash
# 传输运维脚本到服务器并测试 (停前端→验证→启前端→验证)
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"
BASE_URL="${LINGDIAN_BASE_URL:-https://lingdian-ai.top}"
REMOTE_DIR=/root/lingdian
if [[ "${ALLOW_REMOTE_RESTART_TEST:-false}" != true ]]; then
  echo "此脚本会在目标服务器停止并重新启动前端；确认目标和维护窗口后设置 ALLOW_REMOTE_RESTART_TEST=true。" >&2
  exit 2
fi

echo "===== 1. 传输 5 个运维脚本到服务器 ====="
scp $SSH_OPTS -i "$KEY" \
  scripts/start-all.sh scripts/stop-all.sh \
  scripts/start-frontend.sh scripts/stop-frontend.sh \
  scripts/restart-backend.sh \
  "$HOST:$REMOTE_DIR/scripts/"
ssh $SSH_OPTS -i "$KEY" "$HOST" "chmod +x $REMOTE_DIR/scripts/*.sh && ls -la $REMOTE_DIR/scripts/*.sh"
echo "  -> 传输并赋权完成"

echo ""
echo "===== 2. 测试 stop-frontend.sh ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "bash $REMOTE_DIR/scripts/stop-frontend.sh 2>&1"

echo ""
echo "===== 3. 验证前端已停止 (应非200) ====="
curl -s -m 8 -o /dev/null -w '首页 HTTP %{http_code} (停止后期望非200/超时)\n' "$BASE_URL/" || echo "  -> 连接失败(符合预期, 前端已停)"

echo ""
echo "===== 4. 测试 start-frontend.sh ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "bash $REMOTE_DIR/scripts/start-frontend.sh 2>&1"

echo ""
echo "===== 5. 验证前端已恢复 (应200) ====="
sleep 3
curl -s -m 10 -o /dev/null -w '首页 HTTP %{http_code} (恢复后期望200)\n' "$BASE_URL/"

echo ""
echo "===== 6. 最终容器状态 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker ps --filter name=lingdian --format 'table {{.Names}}\t{{.Status}}'"
