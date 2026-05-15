#!/bin/bash
# ===================================================================
# 管理员权限验证测试脚本
# 用于验证 @RequireAdmin 权限检查是否正确生效
# ===================================================================

BASE="http://localhost:8080/api"
PASS=0
FAIL=0

echo "===================================================================="
echo "  管理员权限验证测试"
echo "  测试时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "===================================================================="
echo ""

# ---- Helper Functions ----
check_permission() {
  local name="$1"
  local method="$2"
  local url="$3"
  local token="$4"
  local expect_code="$5"  # 期望的Response Body中的code字段
  local body="${6:-}"  # 可选的请求体，默认为空
  
  local curl_cmd="curl -s --connect-timeout 10 -X $method \"$url\" \
    -H \"X-User-Token: $token\" \
    -H \"Content-Type: application/json\""
  
  # 如果有请求体，添加到命令中
  if [ -n "$body" ]; then
    curl_cmd="$curl_cmd -d '$body'"
  fi
  
  local resp=$(eval "$curl_cmd" 2>/dev/null)
  
  local body_code=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code',-1))" 2>/dev/null)
  local message=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('message',''))" 2>/dev/null)
  
  if [ "$body_code" = "$expect_code" ]; then
    PASS=$((PASS + 1))
    echo "  ✅ PASS [code:$body_code] $name"
    echo "       └─ Message: $message"
  else
    FAIL=$((FAIL + 1))
    echo "  ❌ FAIL [code:$body_code expected $expect_code] $name"
    echo "       └─ Message: $message"
    echo "       └─ Body: $resp"
  fi
}

# ===================================================================
# 第一步：注册一个普通用户
# ===================================================================
echo "━━━ 第一步：注册普通用户 ━━━"

# 生成随机用户名避免冲突
RANDOM_USER="testuser_$(date +%s)"
echo "注册新用户: $RANDOM_USER"

REGISTER_RESP=$(curl -s --connect-timeout 10 -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"Test@123456\",\"email\":\"${RANDOM_USER}@test.com\"}" 2>/dev/null)

REGISTER_CODE=$(echo "$REGISTER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code',-1))" 2>/dev/null)

if [ "$REGISTER_CODE" = "200" ]; then
  echo "  ✅ 注册成功"
else
  echo "  ❌ 注册失败: $REGISTER_RESP"
  exit 1
fi

sleep 1

# ===================================================================
# 第二步：使用普通用户登录获取Token
# ===================================================================
echo ""
echo "━━━ 第二步：普通用户登录 ━━━"

LOGIN_RESP=$(curl -s --connect-timeout 10 -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"Test@123456\"}" \
  -H "X-Device-Id: test-device" 2>/dev/null)

USER_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null)

if [ -n "$USER_TOKEN" ]; then
  echo "  ✅ 登录成功，获取到Token"
  echo "       └─ Token: ${USER_TOKEN:0:20}..."
else
  echo "  ❌ 登录失败: $LOGIN_RESP"
  exit 1
fi

sleep 1

# ===================================================================
# 第三步：验证普通用户的role字段
# ===================================================================
echo ""
echo "━━━ 第三步：验证用户角色 ━━━"

USERINFO_RESP=$(curl -s --connect-timeout 10 -X GET "$BASE/auth/userinfo" \
  -H "X-User-Token: $USER_TOKEN" 2>/dev/null)

USER_ROLE=$(echo "$USERINFO_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('role',''))" 2>/dev/null)

echo "  用户角色: $USER_ROLE"

if [ "$USER_ROLE" = "USER" ]; then
  echo "  ✅ 角色正确（普通用户）"
else
  echo "  ⚠️  警告：角色不是USER，可能是 $USER_ROLE"
fi

sleep 1

# ===================================================================
# 第四步：测试普通用户访问管理员接口（应该被拒绝）
# ===================================================================
echo ""
echo "━━━ 第四步：测试权限拦截（普通用户访问管理员接口） ━━━"
echo "  预期结果：所有请求应返回 code: 403 (Forbidden)"
echo ""

# 测试1: 获取系统统计
check_permission "获取系统统计数据" "GET" \
  "$BASE/admin/stats" \
  "$USER_TOKEN" \
  "403"

sleep 0.5

# 测试2: 获取完整分析数据
check_permission "获取完整分析数据" "GET" \
  "$BASE/admin/analytics/full?days=7&months=6" \
  "$USER_TOKEN" \
  "403"

sleep 0.5

# 测试3: 仪表盘统计
check_permission "获取仪表盘统计数据" "GET" \
  "$BASE/admin/statistics/dashboard?period=7d" \
  "$USER_TOKEN" \
  "403"

sleep 0.5

# 测试4: 用户统计
check_permission "获取用户统计数据" "GET" \
  "$BASE/admin/statistics/users" \
  "$USER_TOKEN" \
  "403"

sleep 0.5

# 测试5: 对话统计
check_permission "获取对话统计数据" "GET" \
  "$BASE/admin/statistics/conversations" \
  "$USER_TOKEN" \
  "403"

sleep 0.5

# 测试6: 分页查询用户
check_permission "分页查询用户" "GET" \
  "$BASE/admin/users?page=1&size=20" \
  "$USER_TOKEN" \
  "403"

sleep 0.5

# 测试7: 切换用户状态（POST）
# 注意：必须发送空JSON对象{}，否则Spring无法解析@RequestBody
check_permission "切换用户状态" "POST" \
  "$BASE/admin/user/status" \
  "$USER_TOKEN" \
  "403" \
  '{}'

sleep 0.5

# 测试8: 给用户充值
check_permission "给用户充值" "POST" \
  "$BASE/admin/user/recharge" \
  "$USER_TOKEN" \
  "403" \
  '{}'

# ===================================================================
# 第五步：使用真正的管理员账号测试（应该成功）
# ===================================================================
echo ""
echo "━━━ 第五步：验证管理员可以正常访问 ━━━"
echo "  使用测试数据中的 admin01 账号"
echo ""

ADMIN_LOGIN_RESP=$(curl -s --connect-timeout 10 -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin01","password":"Test@123456"}' \
  -H "X-Device-Id: admin-device" 2>/dev/null)

ADMIN_TOKEN=$(echo "$ADMIN_LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null)

if [ -n "$ADMIN_TOKEN" ]; then
  echo "  ✅ 管理员登录成功"
  
  # 测试管理员访问
  ADMIN_RESP=$(curl -s -w '\n%{http_code}' --connect-timeout 10 -X GET "$BASE/admin/stats" \
    -H "X-User-Token: $ADMIN_TOKEN" 2>/dev/null)
  
  ADMIN_HTTP=$(echo "$ADMIN_RESP" | tail -1)
  
  if [ "$ADMIN_HTTP" = "200" ]; then
    echo "  ✅ 管理员可以正常访问 /admin/stats (HTTP 200)"
  else
    echo "  ❌ 管理员访问失败 (HTTP $ADMIN_HTTP)"
  fi
else
  echo "  ⚠️  admin01 账号不存在或密码错误"
  echo "  提示：请先执行 init_test_data.sh 初始化测试数据"
fi

# ===================================================================
# 测试结果汇总
# ===================================================================
echo ""
echo "===================================================================="
echo "  测试结果汇总"
echo "===================================================================="
echo ""
echo "  总测试数: $((PASS + FAIL))"
echo "  ✅ 通过: $PASS"
echo "  ❌ 失败: $FAIL"
echo ""

if [ $FAIL -eq 0 ]; then
  echo "  🎉 所有测试通过！@RequireAdmin 权限检查正常工作"
  echo ""
  echo "  结论："
  echo "  - 普通用户无法访问管理员接口（返回403）"
  echo "  - AdminAuthAspect 切面正确拦截了 /admin/** 路径"
  echo "  - 权限控制系统工作正常"
else
  echo "  ⚠️  存在失败的测试，请检查上面的详细信息"
  echo ""
  echo "  可能的问题："
  echo "  1. AdminAuthAspect 切面未正确配置"
  echo "  2. @RequireAdmin 注解未被正确识别"
  echo "  3. 某些接口缺少 @RequireAdmin 注解"
  echo ""
  echo "  建议检查："
  echo "  - src/main/java/com/ai/gateway/aspect/AdminAuthAspect.java"
  echo "  - src/main/java/com/ai/gateway/controller/AdminController.java"
fi

echo ""
echo "===================================================================="

# 清理：可以选择删除测试用户
echo ""
read -p "是否删除测试用户 $RANDOM_USER？(y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
  echo "提示：需要管理员权限才能删除用户，请使用管理员账号手动删除"
fi

exit $FAIL
