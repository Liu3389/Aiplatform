#!/bin/bash
# 快速接口验证脚本
# 用于检查所有关键接口是否正常工作

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "接口快速验证"
echo "=========================================="
echo ""

# 1. 测试用户注册
echo "1️⃣  测试用户注册..."
REGISTER=$(curl -s -X POST "${BASE_URL}/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_verify_'$(date +%s)'",
    "password": "Test@123456",
    "email": "test@example.com"
  }')

echo "响应: $(echo $REGISTER | head -c 100)..."
if echo "$REGISTER" | grep -q '"code":200'; then
  echo "✅ 注册成功"
else
  echo "❌ 注册失败"
fi
echo ""

# 2. 测试用户登录
echo "2️⃣  测试用户登录..."
LOGIN=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: test-device" \
  -d '{
    "username": "testuser",
    "password": "Test@123456"
  }')

TOKEN=$(echo $LOGIN | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$TOKEN" ]; then
  echo "✅ 登录成功，Token: ${TOKEN:0:20}..."
else
  echo "❌ 登录失败"
  echo "响应: $LOGIN"
fi
echo ""

# 3. 测试查询余额
echo "3️⃣  测试查询余额..."
if [ -n "$TOKEN" ]; then
  BALANCE=$(curl -s -X GET "${BASE_URL}/auth/balance" \
    -H "X-User-Token: $TOKEN")
  
  echo "响应: $BALANCE"
  if echo "$BALANCE" | grep -q '"code":200'; then
    echo "✅ 查询余额成功"
  else
    echo "❌ 查询余额失败"
  fi
else
  echo "⚠️  跳过（无Token）"
fi
echo ""

# 4. 测试用户充值
echo "4️⃣  测试用户充值..."
if [ -n "$TOKEN" ]; then
  RECHARGE=$(curl -s -X POST "${BASE_URL}/user/recharge" \
    -H "Content-Type: application/json" \
    -H "X-User-Token: $TOKEN" \
    -d '{"amount": 100}')
  
  echo "响应: $RECHARGE"
  if echo "$RECHARGE" | grep -q '"code":200\|充值成功'; then
    echo "✅ 充值成功"
  else
    echo "❌ 充值失败"
  fi
else
  echo "⚠️  跳过（无Token）"
fi
echo ""

# 5. 测试模型配置查询
echo "5️⃣  测试模型配置查询..."
if [ -n "$TOKEN" ]; then
  MODELS=$(curl -s -X GET "${BASE_URL}/model-config/list" \
    -H "X-User-Token: $TOKEN")
  
  echo "响应: $(echo $MODELS | head -c 150)..."
  if echo "$MODELS" | grep -q '"code":200'; then
    echo "✅ 模型查询成功"
  else
    echo "❌ 模型查询失败"
    echo "完整响应: $MODELS"
  fi
else
  echo "⚠️  跳过（无Token）"
fi
echo ""

# 6. 测试管理员登录
echo "6️⃣  测试管理员登录..."
ADMIN_LOGIN=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: admin-device" \
  -d '{
    "username": "admin",
    "password": "Admin@123456"
  }')

ADMIN_TOKEN=$(echo $ADMIN_LOGIN | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$ADMIN_TOKEN" ]; then
  echo "✅ 管理员登录成功"
else
  echo "❌ 管理员登录失败"
  echo "响应: $ADMIN_LOGIN"
  echo ""
  echo "💡 提示: 可能需要先创建admin账号"
  echo "   执行: mysql -u root -p ai_gateway -e \"INSERT INTO user (username, password, email, role, status) VALUES ('admin', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@example.com', 'ADMIN', 1);\""
fi
echo ""

# 7. 测试管理员发放点数
echo "7️⃣  测试管理员发放点数..."
if [ -n "$ADMIN_TOKEN" ]; then
  DISTRIBUTE=$(curl -s -X POST "${BASE_URL}/admin/points/distribute-daily" \
    -H "X-User-Token: $ADMIN_TOKEN")
  
  echo "响应: $DISTRIBUTE"
  if echo "$DISTRIBUTE" | grep -q '"code":200'; then
    echo "✅ 发放点数成功"
  else
    echo "❌ 发放点数失败"
  fi
else
  echo "⚠️  跳过（无Admin Token）"
fi
echo ""

echo "=========================================="
echo "验证完成！"
echo "=========================================="
echo ""
echo "📝 总结:"
echo "- 如果大部分接口返回404，说明路由未正确注册"
echo "- 如果返回500，查看日志: tail -f logs/ai-gateway.log"
echo "- 如果返回401，检查Token是否正确"
echo ""
echo "💡 常见问题解决:"
echo "1. API Key无效: mysql -u root -p ai_gateway < src/main/resources/sql/check_and_fix_api_keys.sql"
echo "2. 缺少admin账号: 手动插入或注册用户后修改role为ADMIN"
echo "3. 路由404: 检查应用是否正常启动，查看启动日志"
