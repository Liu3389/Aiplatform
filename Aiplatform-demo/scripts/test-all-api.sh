#!/bin/bash
# AI Gateway Platform - 全面接口测试脚本
# 用法: bash scripts/test-all-api.sh

BASE_URL="http://localhost:8080/api"
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
FAIL_LIST=""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

test_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local headers="$4"
    local data="$5"
    local expect_code="${6:-200}"
    
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    
    local curl_cmd="curl -s -o /tmp/api_response.json -w '%{http_code}' -X ${method}"
    
    if [ -n "$headers" ]; then
        while IFS='|' read -r key value; do
            if [ -n "$key" ]; then
                curl_cmd="${curl_cmd} -H '${key}: ${value}'"
            fi
        done <<< "$headers"
    fi
    
    if [ -n "$data" ]; then
        curl_cmd="${curl_cmd} -d '${data}'"
    fi
    
    curl_cmd="${curl_cmd} '${url}'"
    
    local http_code
    http_code=$(eval $curl_cmd 2>/dev/null)
    
    local response
    response=$(cat /tmp/api_response.json 2>/dev/null)
    
    if [ "$http_code" = "$expect_code" ]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "${GREEN}✓ PASS${NC} [${http_code}] ${name}"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "${RED}✗ FAIL${NC} [${http_code}] ${name} (期望: ${expect_code})"
        FAIL_LIST="${FAIL_LIST}\n${TOTAL_COUNT}. ${name}\n   URL: ${method} ${url}\n   期望: ${expect_code}, 实际: ${http_code}\n   响应: ${response:0:150}\n"
    fi
}

echo "=========================================="
echo "  AI Gateway Platform - API 全面测试"
echo "=========================================="
echo "Base URL: ${BASE_URL}"
echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# ==========================================
# 1. 用户认证
# ==========================================
echo -e "\n${YELLOW}=== 1. 用户认证接口 ===${NC}"

UNIQUE_USER="testuser_$(date +%s)"
test_api "用户注册" "POST" "${BASE_URL}/auth/register" \
    "Content-Type|application/json" \
    "{\"username\":\"${UNIQUE_USER}\",\"password\":\"Test123456\",\"email\":\"${UNIQUE_USER}@test.com\"}" "200"

LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${UNIQUE_USER}\",\"password\":\"Test123456\"}")
USER_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$USER_TOKEN" ]; then
    echo -e "${GREEN}✓ 登录成功，获取到 Token${NC}"
else
    echo -e "${RED}✗ 登录失败${NC}"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    FAIL_LIST="${FAIL_LIST}\n登录失败，响应: ${LOGIN_RESPONSE:0:200}\n"
fi

# ==========================================
# 2. 用户信息
# ==========================================
echo -e "\n${YELLOW}=== 2. 用户信息接口 ===${NC}"

if [ -n "$USER_TOKEN" ]; then
    test_api "获取用户信息" "GET" "${BASE_URL}/auth/userinfo" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取用户余额" "GET" "${BASE_URL}/auth/balance" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取用户点数" "GET" "${BASE_URL}/user/points" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取免费配额" "GET" "${BASE_URL}/user/free-quota" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取点数账单" "GET" "${BASE_URL}/user/points-bills?page=1&size=10" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取充值记录" "GET" "${BASE_URL}/user/recharge-records" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取登录设备" "GET" "${BASE_URL}/user/login-devices" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取优惠券" "GET" "${BASE_URL}/user/coupons" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ==========================================
# 3. AI 对话
# ==========================================
echo -e "\n${YELLOW}=== 3. AI 对话接口 ===${NC}"

if [ -n "$USER_TOKEN" ]; then
    test_api "非流式对话" "POST" "${BASE_URL}/chat/completions" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|test-device-001|Content-Type|application/json" \
        '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"mode":"free"}' "200"
    
    test_api "流式对话(SSE)" "POST" "${BASE_URL}/chat/stream" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|test-device-001|Content-Type|application/json|Accept|text/event-stream" \
        '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"mode":"free"}' "200"
    
    test_api "AI生成文件" "POST" "${BASE_URL}/chat/generate-file" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|test-device-001|Content-Type|application/json" \
        '{"requirement":"写一首关于春天的诗","format":"md"}' "200"
    
    test_api "导出对话内容" "POST" "${BASE_URL}/chat/export-content" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"content":"# 测试\n\n内容","fileName":"test.md"}' "200"
fi

# ==========================================
# 4. AI 文档助手
# ==========================================
echo -e "\n${YELLOW}=== 4. AI 文档助手接口 ===${NC}"

if [ -n "$USER_TOKEN" ]; then
    test_api "导出AI结果" "POST" "${BASE_URL}/v1/ai-doc/export" \
        "Content-Type|application/json" \
        '{"content":"# 测试\n\n内容","format":"md"}' "200"
fi

# ==========================================
# 5. 对话管理
# ==========================================
echo -e "\n${YELLOW}=== 5. 对话管理接口 ===${NC}"

if [ -n "$USER_TOKEN" ]; then
    test_api "获取对话列表" "GET" "${BASE_URL}/user/conversations/fetch" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|test-device-001" "" "" "200"
    
    test_api "获取对话版本" "GET" "${BASE_URL}/user/conversations/version" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|test-device-001" "" "" "200"
fi

# ==========================================
# 6. 订阅相关
# ==========================================
echo -e "\n${YELLOW}=== 6. 订阅接口 ===${NC}"

if [ -n "$USER_TOKEN" ]; then
    test_api "获取订阅套餐列表" "GET" "${BASE_URL}/user/subscriptions/packages" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取当前订阅状态" "GET" "${BASE_URL}/user/subscriptions/current" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取订阅历史" "GET" "${BASE_URL}/user/subscriptions/history" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ==========================================
# 7. 通知相关
# ==========================================
echo -e "\n${YELLOW}=== 7. 通知接口 ===${NC}"

if [ -n "$USER_TOKEN" ]; then
    test_api "获取未读通知数" "GET" "${BASE_URL}/user/notifications/unread-count" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ==========================================
# 8. API Key 管理
# ==========================================
echo -e "\n${YELLOW}=== 8. API Key 管理接口 ===${NC}"

if [ -n "$USER_TOKEN" ]; then
    test_api "获取API Key列表" "GET" "${BASE_URL}/api-key/list" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ==========================================
# 9. 模型相关
# ==========================================
echo -e "\n${YELLOW}=== 9. 模型接口 ===${NC}"

test_api "获取平台模型列表" "GET" "${BASE_URL}/platform-model/list" "" "" "" "200"

# ==========================================
# 10. 管理员接口
# ==========================================
echo -e "\n${YELLOW}=== 10. 管理员接口 ===${NC}"

ADMIN_LOGIN=$(curl -s -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"testadmin2","password":"Test@123456"}')
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$ADMIN_TOKEN" ]; then
    echo -e "${GREEN}✓ 管理员登录成功${NC}"
    
    test_api "管理员-系统统计" "GET" "${BASE_URL}/admin/stats" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-用户列表" "GET" "${BASE_URL}/admin/users?page=1&size=10" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-完整分析" "GET" "${BASE_URL}/admin/analytics/full?days=7&months=6" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-仪表盘统计" "GET" "${BASE_URL}/admin/statistics/dashboard?period=7d" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-用户统计" "GET" "${BASE_URL}/admin/statistics/users" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-对话统计" "GET" "${BASE_URL}/admin/statistics/conversations" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-健康检查" "GET" "${BASE_URL}/admin/health" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-操作日志" "GET" "${BASE_URL}/admin/logs?page=1&size=10" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-通知未读数" "GET" "${BASE_URL}/admin/notifications/unread-count" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-通知列表" "GET" "${BASE_URL}/admin/notifications?page=1&size=10" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-每日统计" "GET" "${BASE_URL}/admin/statistics/daily?days=7" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-每周统计" "GET" "${BASE_URL}/admin/statistics/weekly?weeks=4" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-每月统计" "GET" "${BASE_URL}/admin/statistics/monthly?months=6" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-套餐列表" "GET" "${BASE_URL}/admin/packages/list" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
else
    echo -e "${RED}✗ 管理员登录失败${NC}"
    FAIL_COUNT=$((FAIL_COUNT + 14))
    TOTAL_COUNT=$((TOTAL_COUNT + 14))
    FAIL_LIST="${FAIL_LIST}\n管理员登录失败，响应: ${ADMIN_LOGIN:0:200}\n"
fi

# ==========================================
# 11. 错误场景
# ==========================================
echo -e "\n${YELLOW}=== 11. 错误场景测试 ===${NC}"

test_api "无效Token" "GET" "${BASE_URL}/user/points" \
    "X-User-Token|invalid_token" "" "" "200"

test_api "缺少Token" "GET" "${BASE_URL}/user/points" \
    "" "" "" "200"

# ==========================================
# 汇总
# ==========================================
echo ""
echo "=========================================="
echo "  测试汇总"
echo "=========================================="
echo "总测试数: ${TOTAL_COUNT}"
echo -e "通过: ${GREEN}${PASS_COUNT}${NC}"
echo -e "失败: ${RED}${FAIL_COUNT}${NC}"
echo "成功率: $(( PASS_COUNT * 100 / (TOTAL_COUNT > 0 ? TOTAL_COUNT : 1) ))%"
echo "结束时间: $(date '+%Y-%m-%d %H:%M:%S')"

if [ $FAIL_COUNT -gt 0 ]; then
    echo -e "\n${RED}=== 失败详情 ===${NC}"
    echo -e "$FAIL_LIST"
fi

echo ""
echo "测试完成！"
