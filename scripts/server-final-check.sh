#!/bin/bash
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"
BASE_URL="${LINGDIAN_BASE_URL:-https://lingdian-ai.top}"

echo "===== 1. 所有容器状态(redis重启后恢复) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker ps --filter name=lingdian --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"

echo ""
echo "===== 2. 服务器现有运维脚本 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ls -la /root/lingdian/scripts/ 2>/dev/null || echo '服务器无 scripts 目录'"

echo ""
echo "===== 3. 后端最新健康(Started + 无ERROR) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker logs lingdian-backend --since 2m 2>&1 | grep -E 'Started AiGateway|ERROR|Exception' | tail -5 || echo '无异常'"

echo ""
echo "===== 4. 公网最终验证 ====="
curl -s -m 10 -o /dev/null -w '首页 HTTP %{http_code} (%{time_total}s)\n' "$BASE_URL/"
curl -s -m 10 "$BASE_URL/api/platform-model/list" | head -c 120; echo
