#!/bin/bash
# AI Gateway 全接口 curl 测试脚本
# 使用已知可用的 token 和 apiKey

BASE="http://localhost:8080"
TOKEN="05055cc473964f9e926df055ef709012"
DEVICE="test-device-001"
USER_APIKEY="REDACTED_PROVIDER_API_KEY"
# 测试用第二个 token
TOKEN2=""
ADMIN_TOKEN=""

OK=0
FAIL=0

check() {
  local code=$(echo "$1" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)
  local name="$2"
  if [ "$code" = "200" ]; then
    echo "✅ $name"
    ((OK++))
  elif [ "$code" = "201" ]; then
    echo "✅ $name (201)"
    ((OK++))
  else
    echo "❌ $name (code=$code)"
    echo "   响应: $(echo "$1" | head -c 200)"
    ((FAIL++))
  fi
}

echo "╔════════════════════════════════════════════════════╗"
echo "║     AI Gateway 全接口 curl 测试                     ║"
echo "╚════════════════════════════════════════════════════╝"
echo ""

# ========================================================
# 模块1：模型配置（无需认证）
# ========================================================
echo "--- 模块1：模型配置 ---"

R=$(curl -s "$BASE/api/model-config/list")
check "$R" "1.1 获取模型配置列表"

R=$(curl -s "$BASE/api/model-config/detail/16")
check "$R" "1.2 获取模型配置详情(deepseek-v4-flash)"

# ========================================================
# 模块2：用户认证
# ========================================================
echo ""
echo "--- 模块2：用户认证 ---"

R=$(curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: test-device-002" \
  -d '{"username":"testuser001","password":"Test@123456"}')
TOKEN2=$(echo "$R" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
check "$R" "2.1 用户登录"

R=$(curl -s "$BASE/api/auth/userinfo" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "2.2 查询用户信息"

R=$(curl -s "$BASE/api/auth/balance" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "2.3 查询余额"

R=$(curl -s -X POST "$BASE/api/auth/refresh-token" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "2.4 刷新Token"

# ========================================================
# 模块3：API Key 管理
# ========================================================
echo ""
echo "--- 模块3：API Key 管理 ---"

R=$(curl -s -X POST "$BASE/api/api-key/generate" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE" \
  -d '{"name":"TestKey","rateLimit":50}')
check "$R" "3.1 生成API Key"

R=$(curl -s "$BASE/api/api-key/list" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "3.2 获取API Key列表"

R=$(curl -s -X PUT "$BASE/api/api-key/2/status" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE" \
  -d '{"status":"ACTIVE"}')
check "$R" "3.3 切换API Key状态"

R=$(curl -s -X DELETE "$BASE/api/api-key/2" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "3.4 删除API Key"

# ========================================================
# 模块4：会话管理
# ========================================================
echo ""
echo "--- 模块4：会话管理 ---"

R=$(curl -s "$BASE/api/conversations?page=1&size=10" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "4.1 获取会话列表"

R=$(curl -s -X POST "$BASE/api/conversations" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE" \
  -d '{"title":"测试会话"}')
check "$R" "4.2 创建会话"

# ========================================================
# 模块5：聊天对话（核心）
# ========================================================
echo ""
echo "--- 模块5：聊天对话（核心）---"

R=$(curl -s -X POST "$BASE/api/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE" \
  -H "X-API-Key: $USER_APIKEY" \
  -d '{"model":"deepseek-v4-flash","mode":"free","messages":[{"role":"user","content":"1+1等于几？只回答数字"}],"stream":false,"temperature":0.7}')
check "$R" "5.1 免费模式非流式对话(deepseek-v4-flash)"

R=$(curl -s -X POST "$BASE/api/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE" \
  -H "X-API-Key: $USER_APIKEY" \
  -d '{"model":"deepseek-v4-pro","mode":"paid","messages":[{"role":"user","content":"你好"}],"stream":false,"temperature":0.7}')
check "$R" "5.2 付费模式非流式对话(deepseek-v4-pro)"

# 流式对话 - 保存到文件
echo -n "   测试 5.3 流式对话(SSE)... "
curl -s -N -X POST "$BASE/api/chat/stream" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE" \
  -H "X-API-Key: $USER_APIKEY" \
  -d '{"model":"deepseek-v4-flash","mode":"free","messages":[{"role":"user","content":"说一个字：春"}],"stream":true,"temperature":0.7}' \
  > /tmp/sse_test.txt 2>/dev/null &
SSE_PID=$!
sleep 8
kill $SSE_PID 2>/dev/null; wait $SSE_PID 2>/dev/null

if grep -q '"春"' /tmp/sse_test.txt 2>/dev/null || grep -q 'done' /tmp/sse_test.txt 2>/dev/null; then
  echo "✅ 5.3 流式对话(SSE) - 接收到数据"
  ((OK++))
  # 统计null数量
  NULL_COUNT=$(grep -c '"null"' /tmp/sse_test.txt 2>/dev/null || echo 0)
  echo "    null chunk 数量: $NULL_COUNT"
else
  echo "❌ 5.3 流式对话(SSE) - 失败或无数据"
  ((FAIL++))
fi

# ========================================================
# 模块6：聊天记录管理
# ========================================================
echo ""
echo "--- 模块6：聊天记录管理 ---"

R=$(curl -s "$BASE/api/chat/messages?conversationId=1&cursor=0&size=10" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "6.1 获取聊天记录"

R=$(curl -s "$BASE/api/chat/messages/history?conversationId=1&cursor=0&size=10" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "6.2 获取历史聊天记录(冷数据)"

R=$(curl -s -X DELETE "$BASE/api/chat/messages/1" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "6.3 删除消息"

# ========================================================
# 模块7：用户中心
# ========================================================
echo ""
echo "--- 模块7：用户中心 ---"

R=$(curl -s "$BASE/api/user/info" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "7.1 获取用户中心信息"

R=$(curl -s "$BASE/api/user/points" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "7.2 获取点数余额"

R=$(curl -s "$BASE/api/user/free-quota" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "7.3 获取免费额度"

R=$(curl -s "$BASE/api/user/points-bills?page=1&size=10" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "7.4 获取点数账单"

R=$(curl -s "$BASE/api/user/subscription" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "7.5 获取订阅信息"

R=$(curl -s -X POST "$BASE/api/user/recharge?amount=10" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "7.6 用户充值"

# ========================================================
# 模块8：通知与优惠券
# ========================================================
echo ""
echo "--- 模块8：通知 ---"

R=$(curl -s "$BASE/api/user/notifications" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "8.1 获取通知列表"

R=$(curl -s "$BASE/api/user/notifications/unread-count" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "8.2 获取未读通知数"

# ========================================================
# 模块9：优惠券
# ========================================================
echo ""
echo "--- 模块9：优惠券 ---"

R=$(curl -s "$BASE/api/coupons/list?page=1&size=10" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "9.1 获取优惠券列表"

R=$(curl -s "$BASE/api/coupons/my" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "9.2 获取我的优惠券"

# ========================================================
# 模块10：套餐订阅
# ========================================================
echo ""
echo "--- 模块10：套餐订阅 ---"

R=$(curl -s "$BASE/api/subscriptions/packages" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "10.1 获取套餐列表"

R=$(curl -s "$BASE/api/subscriptions/status" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "10.2 获取订阅状态"

R=$(curl -s "$BASE/api/subscriptions/history?page=1&size=10" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "10.3 订阅历史"

# ========================================================
# 模块11：用户自定义 API Key
# ========================================================
echo ""
echo "--- 模块11：用户自定义 API Key ---"

R=$(curl -s -X POST "$BASE/api/user/api-keys" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE" \
  -d '{"name":"MyKey","provider":"deepseek","apiKey":"REDACTED_PROVIDER_API_KEY"}')
check "$R" "11.1 添加自定义API Key"

R=$(curl -s "$BASE/api/user/api-keys" \
  -H "X-User-Token: $TOKEN" -H "X-Device-Id: $DEVICE")
check "$R" "11.2 获取自定义API Key列表"

# ========================================================
# 结果统计
# ========================================================
echo ""
echo "╔════════════════════════════════════════════════════╗"
echo "║                    测试结果汇总                     ║"
echo "╠════════════════════════════════════════════════════╣"
echo "║  通过: $OK / $(($OK + $FAIL))"
echo "║  失败: $FAIL"
echo "╚════════════════════════════════════════════════════╝"
echo ""
rm -f /tmp/sse_test.txt
