#!/bin/bash

# AI Gateway Platform - 接口测试脚本

BASE_URL="http://localhost:8080/api"

echo "========================================="
echo "AI Gateway Platform - 接口测试"
echo "========================================="

# 检查服务是否运行
echo ""
echo "1. 检查服务状态..."
HEALTH=$(curl -s $BASE_URL/actuator/health)
if echo "$HEALTH" | grep -q "UP"; then
    echo "✅ 服务运行正常"
else
    echo "❌ 服务未启动，请先启动应用"
    exit 1
fi

# 注册用户
echo ""
echo "2. 注册测试用户..."
REGISTER_RESPONSE=$(curl -s -X POST $BASE_URL/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser_'"$(date +%s)"'",
    "password": "123456",
    "email": "test@example.com"
  }')

echo "注册响应: $REGISTER_RESPONSE"

# 登录获取 Token
echo ""
echo "3. 用户登录..."
LOGIN_RESPONSE=$(curl -s -X POST $BASE_URL/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser_'"$(date +%s)"'",
    "password": "123456"
  }')

# 提取 token 和 userId（简单方式）
TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
USER_ID=$(echo $LOGIN_RESPONSE | grep -o '"userId":[0-9]*' | cut -d':' -f2)

if [ -z "$TOKEN" ]; then
    echo "❌ 登录失败，无法获取 Token"
    echo "响应: $LOGIN_RESPONSE"
    
    # 尝试使用已知用户
    echo ""
    echo "尝试使用默认用户登录..."
    LOGIN_RESPONSE=$(curl -s -X POST $BASE_URL/auth/login \
      -H "Content-Type: application/json" \
      -d '{
        "username": "admin",
        "password": "admin123"
      }')
    
    TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    USER_ID=$(echo $LOGIN_RESPONSE | grep -o '"userId":[0-9]*' | cut -d':' -f2)
    
    if [ -z "$TOKEN" ]; then
        echo "❌ 仍然无法获取 Token，请手动创建用户"
        exit 1
    fi
fi

echo "✅ 登录成功"
echo "User ID: $USER_ID"
echo "Token: ${TOKEN:0:20}..."

# 获取用户信息
echo ""
echo "4. 获取用户信息..."
USER_INFO=$(curl -s -X GET "$BASE_URL/auth/userinfo?userId=$USER_ID" \
  -H "Authorization: Bearer $TOKEN")

echo "用户信息: $USER_INFO"

# 生成 API Key
echo ""
echo "5. 生成 API Key..."
API_KEY_RESPONSE=$(curl -s -X POST $BASE_URL/user/apikey/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "测试API Key",
    "rateLimit": 100
  }')

echo "API Key 响应: $API_KEY_RESPONSE"

# 提取 API Key
API_KEY=$(echo $API_KEY_RESPONSE | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)

if [ -z "$API_KEY" ]; then
    echo "❌ 生成 API Key 失败"
    exit 1
fi

echo "✅ API Key 生成成功: ${API_KEY:0:20}..."

# 测试流式对话
echo ""
echo "6. 测试流式对话（deepseek-chat）..."
curl -X POST $BASE_URL/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "stream": true
  }' | head -c 500

echo ""
echo ""

# 测试非流式对话
echo "7. 测试非流式对话（deepseek-chat）..."
CHAT_RESPONSE=$(curl -s -X POST $BASE_URL/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "请用一句话介绍你自己"}
    ],
    "stream": false
  }')

echo "对话响应: $CHAT_RESPONSE"

# 查看调用日志
echo ""
echo "8. 查看调用日志..."
CALL_LOG=$(curl -s -X GET "$BASE_URL/user/calllog?page=1&size=5" \
  -H "Authorization: Bearer $TOKEN")

echo "调用日志: $CALL_LOG"

# 查看点数余额
echo ""
echo "9. 查看点数余额..."
BALANCE=$(curl -s -X GET "$BASE_URL/user/info" \
  -H "Authorization: Bearer $TOKEN")

echo "余额信息: $BALANCE"

echo ""
echo "========================================="
echo "✅ 接口测试完成！"
echo "========================================="
echo ""
echo "保存的信息："
echo "- User ID: $USER_ID"
echo "- Token: $TOKEN"
echo "- API Key: $API_KEY"
echo ""
echo "可以使用这些信息继续进行手动测试"
