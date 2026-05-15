#!/bin/bash
# DeepSeek API 对话测试脚本
# 测试流式和非流式对话功能

BASE_URL="http://localhost:8080"
TOKEN=""

echo "=========================================="
echo "DeepSeek API 对话功能测试"
echo "=========================================="
echo ""

# 1. 用户注册（如果还没有账号）
echo "📝 步骤1: 注册用户..."
REGISTER_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_deepseek_'$(date +%s)'",
    "password": "Test@123456",
    "email": "test_deepseek@example.com"
  }')

echo "注册响应: $REGISTER_RESPONSE"
echo ""

# 2. 用户登录获取Token
echo "🔐 步骤2: 用户登录..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_deepseek_'$(date +%s)'",
    "password": "Test@123456"
  }')

# 尝试从之前的注册用户名登录（如果注册失败，使用固定用户名）
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "username": "testuser",
    "password": "Test@123456"
  }')

echo "登录响应: $LOGIN_RESPONSE"

# 提取Token
TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 错误: 无法获取Token，请先手动登录并设置TOKEN变量"
  echo "使用方法: export TOKEN='your_token_here'"
  exit 1
fi

echo "✅ Token获取成功: ${TOKEN:0:20}..."
echo ""

# 3. 测试非流式对话
echo "💬 步骤3: 测试非流式对话（deepseek-v4-flash）..."
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
    "mode": "free",
    "maxTokens": 100,
    "temperature": 0.7
  }')

echo "非流式对话响应:"
echo "$NON_STREAM_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$NON_STREAM_RESPONSE"
echo ""

# 检查是否成功
if echo "$NON_STREAM_RESPONSE" | grep -q '"code":200'; then
  echo "✅ 非流式对话测试成功！"
else
  echo "❌ 非流式对话测试失败"
  echo "错误信息: $(echo $NON_STREAM_RESPONSE | grep -o '"message":"[^"]*"')"
fi
echo ""

# 4. 测试流式对话
echo "🌊 步骤4: 测试流式对话（deepseek-v4-flash）..."
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
    "mode": "free",
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

# 5. 测试付费模式（如果有余额）
echo "💰 步骤5: 测试付费模式对话（deepseek-v4-pro）..."
PAID_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/chat/completions" \
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
    "mode": "paid",
    "maxTokens": 200,
    "temperature": 0.7
  }')

echo "付费模式对话响应:"
echo "$PAID_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$PAID_RESPONSE"
echo ""

if echo "$PAID_RESPONSE" | grep -q '"code":200'; then
  echo "✅ 付费模式对话测试成功！"
else
  echo "⚠️ 付费模式对话可能失败（可能是点数不足）"
  echo "错误信息: $(echo $PAID_RESPONSE | grep -o '"message":"[^"]*"')"
fi
echo ""

# 6. 查看调用日志
echo "📊 步骤6: 查看最近的调用日志..."
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
echo "1. ✅ 非流式对话已测试"
echo "2. ✅ 流式对话已测试"
echo "3. ✅ 付费模式已测试"
echo ""
echo "💡 提示:"
echo "- 如果某些测试失败，请检查:"
echo "  1. 数据库中的 model_config 表是否有正确的 API Key"
echo "  2. 用户是否有足够的点数（付费模式）"
echo "  3. 应用日志: tail -f logs/ai-gateway.log"
