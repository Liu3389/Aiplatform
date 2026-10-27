#!/bin/bash
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"
BASE_URL="${LINGDIAN_BASE_URL:?Set LINGDIAN_BASE_URL to the target HTTPS origin}"
BASE="${BASE_URL%/}/api"
: "${LINGDIAN_ADMIN_USER:?Set LINGDIAN_ADMIN_USER}"
: "${LINGDIAN_ADMIN_PASSWORD:?Set LINGDIAN_ADMIN_PASSWORD}"

echo "===== 1. 确认 PayServer 已停 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "pgrep -af 'java.*PayServer' && echo '⚠️ PayServer 仍在运行' || echo '✅ PayServer 已停止'"

echo ""
echo "===== 2. 容器状态 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker ps --filter name=lingdian --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"

echo ""
echo "===== 3. HTTPS 登录测试 ====="
LOGIN=$(python3 -c 'import json,os; print(json.dumps({"username":os.environ["LINGDIAN_ADMIN_USER"],"password":os.environ["LINGDIAN_ADMIN_PASSWORD"]}))' | curl -s -m 15 -X POST "$BASE/auth/login" -H 'Content-Type: application/json' --data-binary @-)
TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then echo "✅ 登录成功 token=${TOKEN:0:8}..."; else echo "❌ 登录失败: $LOGIN" | head -c 200; fi

echo ""
echo "===== 4. SSE 流式对话测试 (nginx 反代后关键验证) ====="
if [ -n "$TOKEN" ]; then
  echo "  发起流式请求..."
  RESP=$(curl -s -m 30 -N -X POST "$BASE/chat/stream" \
    -H 'Content-Type: application/json' -H "X-User-Token: $TOKEN" \
    -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好,回复一个字"}],"stream":true}' 2>&1)
  # 统计 SSE 事件
  EVTS=$(echo "$RESP" | grep -c '^event:')
  Datas=$(echo "$RESP" | grep -c '^data:')
  echo "  收到 event 行: $EVTS, data 行: $Datas"
  if [ "$EVTS" -gt 0 ] || [ "$Datas" -gt 0 ]; then
    echo "✅ SSE 流式响应正常"
    echo "  响应片段预览:"; echo "$RESP" | head -8 | sed 's/^/    /'
  else
    echo "❌ SSE 无响应, 预览:"; echo "$RESP" | head -c 300 | sed 's/^/    /'
  fi
fi

echo ""
echo "===== 5. 非流式对话测试 ====="
if [ -n "$TOKEN" ]; then
  NR=$(curl -s -m 30 -X POST "$BASE/chat/completions" \
    -H 'Content-Type: application/json' -H "X-User-Token: $TOKEN" \
    -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"回复OK"}]}' 2>&1)
  echo "  预览:"; echo "$NR" | head -c 200 | sed 's/^/    /'
fi
