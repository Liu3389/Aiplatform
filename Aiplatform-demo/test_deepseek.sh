#!/bin/bash

# 测试DeepSeek API的正确调用方式

API_KEY="REDACTED_PROVIDER_API_KEY"

echo "=========================================="
echo "测试DeepSeek API调用"
echo "=========================================="
echo ""

# 测试1: 使用完整路径 https://api.deepseek.com/v1/chat/completions
echo "测试1: 使用完整路径 https://api.deepseek.com/v1/chat/completions"
echo "----------------------------------------"
RESPONSE1=$(curl -s -X POST "https://api.deepseek.com/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $API_KEY" \
    -d '{
        "model": "deepseek-chat",
        "messages": [{"role": "user", "content": "你好，请说一句话"}],
        "stream": false
    }')

if echo "$RESPONSE1" | grep -q '"choices":'; then
    echo "✅ 成功！"
    CONTENT=$(echo "$RESPONSE1" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "AI回复: $CONTENT"
else
    echo "❌ 失败！响应: $RESPONSE1"
fi

echo ""
echo "=========================================="

# 测试2: 使用基础路径 https://api.deepseek.com 并添加 /v1/chat/completions
echo "测试2: 使用基础路径 https://api.deepseek.com (应该也能工作)"
echo "----------------------------------------"
RESPONSE2=$(curl -s -X POST "https://api.deepseek.com/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $API_KEY" \
    -d '{
        "model": "deepseek-v4-flash",
        "messages": [{"role": "user", "content": "你好"}],
        "stream": false
    }')

if echo "$RESPONSE2" | grep -q '"choices":'; then
    echo "✅ 成功！"
    CONTENT=$(echo "$RESPONSE2" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "AI回复: $CONTENT"
else
    echo "❌ 失败！响应: $RESPONSE2"
fi

echo ""
echo "=========================================="
echo "测试新模型 deepseek-v4-flash"
echo "=========================================="

RESPONSE3=$(curl -s -X POST "https://api.deepseek.com/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $API_KEY" \
    -d '{
        "model": "deepseek-v4-flash",
        "messages": [{"role": "user", "content": "你好，你是deepseek-v4-flash吗？"}],
        "stream": false
    }')

if echo "$RESPONSE3" | grep -q '"choices":'; then
    echo "✅ deepseek-v4-flash 模型可用！"
    CONTENT=$(echo "$RESPONSE3" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "AI回复: $CONTENT"
else
    echo "❌ deepseek-v4-flash 模型可能不可用！响应: $RESPONSE3"
fi
