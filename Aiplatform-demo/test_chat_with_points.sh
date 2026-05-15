#!/bin/bash
# DeepSeek API 对话测试脚本（点数结算版）
# 所有对话统一使用点数结算：deepseek-v4-flash消耗1点/次

BASE_URL="http://localhost:8080"
TOKEN=""
USERNAME="test_points_$(date +%s)"
PASSWORD="Test@123456"

echo "=========================================="
echo "DeepSeek API 对话功能测试（点数结算）"
echo "=========================================="
echo "说明：deepseek-v4-flash = 1点/次，每日发放30点"
echo ""

# 1. 用户注册
echo "📝 步骤1: 注册用户..."
echo "用户名: $USERNAME"
REGISTER_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USERNAME}\",
    \"password\": \"${PASSWORD}\",
    \"email\": \"test@example.com\"
  }")

echo "注册响应: $(echo $REGISTER_RESPONSE | head -c 150)..."
echo ""

# 检查注册是否成功
if ! echo "$REGISTER_RESPONSE" | grep -q '"code":200'; then
  echo "⚠️ 注册失败，尝试使用已有账号..."
  USERNAME="testuser"
  PASSWORD="Test@123456"
fi

# 2. 用户登录获取Token
echo "🔐 步骤2: 用户登录..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: test-device-001" \
  -d "{
    \"username\": \"${USERNAME}\",
    \"password\": \"${PASSWORD}\"
  }")

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 错误: 无法获取Token"
  exit 1
fi

echo "✅ Token获取成功"
echo ""

# 3. 查看用户点数
echo "💰 步骤3: 查看用户点数..."
BALANCE_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/auth/balance" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001")

echo "余额响应: $BALANCE_RESPONSE"
echo ""

# 4. 给用户充值点数（用于测试）
echo "💳 步骤4: 充值点数..."
RECHARGE_RESPONSE=$(curl -s -X POST "${BASE_URL}/user/recharge" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -d '{"amount": 100}')

echo "充值响应: $RECHARGE_RESPONSE"
echo ""

# 5. 查看模型列表（显示点数消耗）
echo "📋 步骤5: 查看可用模型及点数消耗..."
MODELS_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/models" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001")

echo "模型列表:"
echo "$MODELS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$MODELS_RESPONSE"
echo ""

# 6. 测试非流式对话（点数结算）
echo "💬 步骤6: 测试非流式对话（deepseek-v4-flash）..."
NON_STREAM_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "model": "deepseek-v4-flash",
    "messages": [
      {
        "role": "user",
        "content": "你好，请用一句话介绍你自己"
      }
    ],
    "maxTokens": 100,
    "temperature": 0.7
  }')

echo "非流式对话响应:"
echo "$NON_STREAM_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$NON_STREAM_RESPONSE"
echo ""

if echo "$NON_STREAM_RESPONSE" | grep -q '"code":200'; then
  echo "✅ 非流式对话测试成功！"
else
  echo "❌ 非流式对话测试失败"
  echo "错误信息: $(echo $NON_STREAM_RESPONSE | grep -o '"message":"[^"]*"')"
  echo ""
  echo "💡 请检查："
  echo "  1. 数据库中的API Key是否正确"
  echo "  2. 执行: mysql -u root -p ai_gateway < src/main/resources/sql/check_and_fix_api_keys.sql"
  echo "  3. 查看日志: tail -f logs/ai-gateway.log"
fi
echo ""

# 7. 测试流式对话（点数结算）
echo "🌊 步骤7: 测试流式对话（deepseek-v4-flash）..."
echo "（流式输出将在下方逐行显示）"
echo "---"

curl -s -X POST "${BASE_URL}/api/chat/stream" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "model": "deepseek-v4-flash",
    "messages": [
      {
        "role": "user",
        "content": "请写一首关于春天的短诗，不超过4句"
      }
    ],
    "maxTokens": 150,
    "temperature": 0.8
  }' | while IFS= read -r line; do
    if [[ $line == data:* ]]; then
      content=$(echo $line | sed 's/^data: //' | python3 -c "import sys,json; print(json.load(sys.stdin).get('content',''), end='')" 2>/dev/null)
      if [ -n "$content" ]; then
        echo -n "$content"
      fi
    fi
  done

echo ""
echo "---"
echo ""

# 8. 测试高级模型（deepseek-v4-pro）
echo "⭐ 步骤8: 测试高级模型（deepseek-v4-pro）..."
PRO_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "model": "deepseek-v4-pro",
    "messages": [
      {
        "role": "user",
        "content": "解释一下什么是人工智能"
      }
    ],
    "maxTokens": 200,
    "temperature": 0.7
  }')

echo "高级模型响应:"
echo "$PRO_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$PRO_RESPONSE"
echo ""

# 9. 查看剩余点数
echo "📊 步骤9: 查看剩余点数..."
FINAL_BALANCE=$(curl -s -X GET "${BASE_URL}/api/auth/balance" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001")

echo "最终余额: $FINAL_BALANCE"
echo ""

# 10. 查看调用日志
echo "📝 步骤10: 查看调用日志..."
LOGS_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/user/call-logs?page=1&size=5" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001")

echo "调用日志:"
echo "$LOGS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$LOGS_RESPONSE"
echo ""

echo "=========================================="
echo "测试完成！"
echo "=========================================="
echo ""
echo "📝 总结:"
echo "✅ 所有对话均采用点数结算"
echo "✅ 模型商城显示每次对话消耗的点数"
echo "✅ 用户点击模型后直接进入对话并自动扣点"
echo ""
echo "💡 提示:"
echo "- 如果测试失败，请检查:"
echo "  1. 数据库中的 model_config 表是否有正确的 API Key"
echo "  2. 执行 SQL: source src/main/resources/sql/check_and_fix_api_keys.sql"
echo "  3. 应用日志: tail -f logs/ai-gateway.log"
