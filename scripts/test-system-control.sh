#!/bin/bash
# =============================================================================
# 测试管理员系统控制全流程: 登录 -> 二级密码验证 -> 容器状态 -> 容器操作
# =============================================================================
BASE_URL="${LINGDIAN_BASE_URL:-http://127.0.0.1:18080}"
BASE="${BASE_URL%/}/api"
ADMIN_USER="${LINGDIAN_ADMIN_USER:?Set LINGDIAN_ADMIN_USER}"
ADMIN_PASS="${LINGDIAN_ADMIN_PASSWORD:?Set LINGDIAN_ADMIN_PASSWORD}"
SECONDARY_PWD="${LINGDIAN_SECONDARY_PASSWORD:?Set LINGDIAN_SECONDARY_PASSWORD}"
if [[ "$BASE_URL" != http://127.0.0.1* && "${ALLOW_REMOTE_ADMIN_TESTS:-false}" != true ]]; then
  echo "拒绝对远端执行会重启服务的管理员测试；仅在明确授权后设置 ALLOW_REMOTE_ADMIN_TESTS=true。" >&2
  exit 2
fi

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
ok(){ echo -e "${GREEN}✅ $1${NC}"; }
fail(){ echo -e "${RED}❌ $1${NC}"; }
hdr(){ echo -e "\n${CYAN}===== $1 =====${NC}"; }

hdr "1. 管理员登录"
LOGIN=$(curl -s -m 10 -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
echo "$LOGIN" | head -c 300; echo
TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
ROLE=$(echo "$LOGIN" | grep -o '"role":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then ok "登录成功 (role=$ROLE)"; else fail "登录失败"; exit 1; fi

hdr "2. 二级密码验证"
VERIFY=$(curl -s -m 10 -X POST "$BASE/admin/system/verify" \
  -H 'Content-Type: application/json' -H "X-User-Token: $TOKEN" \
  -d "{\"password\":\"$SECONDARY_PWD\"}")
echo "$VERIFY" | head -c 400; echo
SEC_TOKEN=$(echo "$VERIFY" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -n "$SEC_TOKEN" ]; then ok "二级验证通过, 获得 secondary token"; else fail "二级验证失败"; exit 1; fi

hdr "3. 错误二级密码应被拒(测试安全性)"
BAD=$(curl -s -m 10 -X POST "$BASE/admin/system/verify" \
  -H 'Content-Type: application/json' -H "X-User-Token: $TOKEN" \
  -d '{"password":"0000"}')
echo "$BAD" | head -c 200; echo
if echo "$BAD" | grep -q "错误\|incorrect\|401\|403"; then ok "错误密码被正确拒绝"; else fail "安全校验异常"; fi

hdr "4. 查询所有容器状态"
STATUS=$(curl -s -m 10 "$BASE/admin/system/status" \
  -H "X-User-Token: $TOKEN" -H "X-Secondary-Token: $SEC_TOKEN")
echo "$STATUS" | head -c 800; echo
if echo "$STATUS" | grep -q "lingdian"; then ok "容器状态查询成功"; else fail "状态查询失败"; fi

hdr "5. 无二级token访问应被拒(测试鉴权)"
NOAUTH=$(curl -s -m 10 "$BASE/admin/system/status" -H "X-User-Token: $TOKEN")
echo "$NOAUTH" | head -c 200; echo
if echo "$NOAUTH" | grep -q "过期\|未验证\|401\|403\|token"; then ok "无二级token被正确拒绝"; else fail "鉴权异常"; fi

hdr "6. 获取 redis 容器日志(最近20行)"
LOGS=$(curl -s -m 10 "$BASE/admin/system/logs/redis?lines=20" \
  -H "X-User-Token: $TOKEN" -H "X-Secondary-Token: $SEC_TOKEN")
echo "$LOGS" | head -c 400; echo
if [ ${#LOGS} -gt 10 ]; then ok "日志获取成功"; else fail "日志获取失败"; fi

hdr "7. 重启 redis 容器(测试容器控制)"
RESTART=$(curl -s -m 15 -X POST "$BASE/admin/system/container/redis/restart" \
  -H "X-User-Token: $TOKEN" -H "X-Secondary-Token: $SEC_TOKEN")
echo "$RESTART" | head -c 200; echo
if echo "$RESTART" | grep -q "成功\|success\|200"; then ok "redis 重启指令已发送"; else fail "重启失败"; fi

echo ""
echo -e "${CYAN}========== 测试完成 ==========${NC}"
