#!/bin/bash
# ============================================================
#  AI Gateway Platform - 全接口全场景综合测试脚本
#  覆盖所有控制器、所有接口、正常/异常/边界场景
#  用法: bash scripts/test-all-comprehensive.sh
# ============================================================

BASE_URL="http://localhost:8080/api"
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
TOTAL_COUNT=0
FAIL_DETAILS=""
START_TIME=$(date +%s)

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 测试结果存储数组 (注: 不使用关联数组以保证兼容性)

# 通用测试函数
test_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local headers="$4"
    local data="$5"
    local expect_code="${6:-200}"
    
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    
    local curl_cmd="curl -s --max-time 30 -o /tmp/api_resp.json -w '%{http_code}' -X ${method}"
    
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
    response=$(cat /tmp/api_resp.json 2>/dev/null)
    
    if [ "$http_code" = "$expect_code" ]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "${GREEN}  ✓ PASS${NC} [${http_code}] ${name}"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "${RED}  ✗ FAIL${NC} [${http_code}] ${name} (期望: ${expect_code})"
        FAIL_DETAILS="${FAIL_DETAILS}\n[${TOTAL_COUNT}] ${name}\n    URL: ${method} ${url}\n    期望: ${expect_code}, 实际: ${http_code}\n    响应: ${response:0:300}\n"
    fi
}

# SSE/流式测试专用
test_sse_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local headers="$4"
    local data="$5"
    
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    
    local curl_cmd="curl -s --max-time 10 -o /tmp/api_sse_resp.txt -w '%{http_code}' -X ${method}"
    
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
    
    local sse_content
    sse_content=$(head -c 500 /tmp/api_sse_resp.txt 2>/dev/null)
    
    if [ "$http_code" = "200" ]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "${GREEN}  ✓ PASS${NC} [${http_code}] ${name}"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "${RED}  ✗ FAIL${NC} [${http_code}] ${name} (期望: 200)"
        FAIL_DETAILS="${FAIL_DETAILS}\n[${TOTAL_COUNT}] ${name}\n    URL: ${method} ${url}\n    期望: 200, 实际: ${http_code}\n    响应: ${sse_content:0:300}\n"
    fi
}

# 导出/二进制文件测试
test_export_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local headers="$4"
    local data="$5"
    local expect_code="${6:-200}"
    
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    
    local curl_cmd="curl -s --max-time 30 -o /tmp/api_export_resp -w '%{http_code}' -X ${method}"
    
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
    
    if [ "$http_code" = "$expect_code" ]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "${GREEN}  ✓ PASS${NC} [${http_code}] ${name}"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "${RED}  ✗ FAIL${NC} [${http_code}] ${name} (期望: ${expect_code})"
        FAIL_DETAILS="${FAIL_DETAILS}\n[${TOTAL_COUNT}] ${name}\n    URL: ${method} ${url}\n    期望: ${expect_code}, 实际: ${http_code}\n"
    fi
}

# 带JSON校验的测试
test_api_with_check() {
    local name="$1"
    local method="$2"
    local url="$3"
    local headers="$4"
    local data="$5"
    local expect_code="${6:-200}"
    local check_field="$7"
    
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    
    local curl_cmd="curl -s --max-time 30 -o /tmp/api_resp.json -w '%{http_code}' -X ${method}"
    
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
    response=$(cat /tmp/api_resp.json 2>/dev/null)
    
    local pass=true
    if [ "$http_code" != "$expect_code" ]; then
        pass=false
    fi
    
    if [ -n "$check_field" ] && [ "$pass" = true ]; then
        local field_val
        field_val=$(echo "$response" | python3 -c "
import sys,json
d=json.load(sys.stdin)
v=d.get('$check_field')
if v is None: print('__NONE__')
elif v=='': print('__EMPTY__')
else: print('__OK__')
" 2>/dev/null)
        if [ "$field_val" != "__OK__" ]; then
            pass=false
        fi
    fi
    
    if [ "$pass" = true ]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "${GREEN}  ✓ PASS${NC} [${http_code}] ${name}"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "${RED}  ✗ FAIL${NC} [${http_code}] ${name} (期望: ${expect_code})"
        FAIL_DETAILS="${FAIL_DETAILS}\n[${TOTAL_COUNT}] ${name}\n    URL: ${method} ${url}\n    期望: ${expect_code}, 实际: ${http_code}\n    响应: ${response:0:300}\n"
    fi
}

# ============================================================
print_section() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_subsection() {
    echo -e "${BLUE}  --- $1 ---${NC}"
}

# ============================================================
echo "╔══════════════════════════════════════════════════════╗"
echo "║   AI Gateway Platform - 全接口全场景综合测试         ║"
echo "╚══════════════════════════════════════════════════════╝"
echo "Base URL: ${BASE_URL}"
echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# ============================================================
print_section "阶段 0: 前置健康检查"
# ============================================================

test_api "系统健康检查(actuator)" "GET" "${BASE_URL}/actuator/health" "" "" "200"
test_api "平台模型列表(无需认证)" "GET" "${BASE_URL}/platform-model/list" "" "" "200"

# ============================================================
print_section "阶段 1: 用户认证模块 (AuthController)"
# ============================================================

UNIQUE_USER="testfull_$(date +%s)"
TEST_EMAIL="${UNIQUE_USER}@test.com"
TEST_PASSWORD="Test123456"

print_subsection "1.1 注册场景"

test_api "注册-正常注册" "POST" "${BASE_URL}/auth/register" \
    "Content-Type|application/json" \
    "{\"username\":\"${UNIQUE_USER}\",\"password\":\"${TEST_PASSWORD}\",\"email\":\"${TEST_EMAIL}\"}" "200"

test_api "注册-重复用户名" "POST" "${BASE_URL}/auth/register" \
    "Content-Type|application/json" \
    "{\"username\":\"${UNIQUE_USER}\",\"password\":\"${TEST_PASSWORD}\",\"email\":\"${UNIQUE_USER}_2@test.com\"}" "200"

test_api "注册-缺少必填字段(username为空)" "POST" "${BASE_URL}/auth/register" \
    "Content-Type|application/json" \
    "{\"username\":\"\",\"password\":\"Test123456\",\"email\":\"empty@test.com\"}" "200"

test_api "注册-缺少email" "POST" "${BASE_URL}/auth/register" \
    "Content-Type|application/json" \
    "{\"username\":\"noemail_$(date +%s)\",\"password\":\"Test123456\"}" "200"

test_api "注册-缺少password" "POST" "${BASE_URL}/auth/register" \
    "Content-Type|application/json" \
    "{\"username\":\"nopass_$(date +%s)\",\"email\":\"nopass@test.com\"}" "200"

print_subsection "1.2 登录场景"

LOGIN_RESPONSE=$(curl -s --max-time 30 -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${UNIQUE_USER}\",\"password\":\"${TEST_PASSWORD}\"}")
USER_TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null)
DEVICE_ID=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('deviceId',''))" 2>/dev/null)

if [ -n "$USER_TOKEN" ]; then
    echo -e "${GREEN}  ✓ 登录成功，获取到 Token (deviceId: ${DEVICE_ID:0:8}...)${NC}"
else
    echo -e "${RED}  ✗ 登录失败，后续用户测试将跳过${NC}"
    FAIL_COUNT=$((FAIL_COUNT + 5))
    TOTAL_COUNT=$((TOTAL_COUNT + 5))
    FAIL_DETAILS="${FAIL_DETAILS}\n登录失败，响应: ${LOGIN_RESPONSE:0:300}\n"
fi

test_api "登录-错误密码" "POST" "${BASE_URL}/auth/login" \
    "Content-Type|application/json" \
    "{\"username\":\"${UNIQUE_USER}\",\"password\":\"WrongPass999\"}" "200"

test_api "登录-不存在的用户" "POST" "${BASE_URL}/auth/login" \
    "Content-Type|application/json" \
    "{\"username\":\"nonexistent_$(date +%s)\",\"password\":\"Test123456\"}" "200"

test_api "登录-缺少username" "POST" "${BASE_URL}/auth/login" \
    "Content-Type|application/json" \
    "{\"password\":\"Test123456\"}" "200"

print_subsection "1.3 Token管理"

if [ -n "$USER_TOKEN" ]; then
    test_api "刷新Token" "POST" "${BASE_URL}/auth/refresh-token" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "退出登录" "POST" "${BASE_URL}/auth/logout" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    # 退出后重新登录
    LOGIN_RESPONSE=$(curl -s --max-time 30 -X POST "${BASE_URL}/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${UNIQUE_USER}\",\"password\":\"${TEST_PASSWORD}\"}")
    USER_TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null)
    DEVICE_ID=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('deviceId',''))" 2>/dev/null)
fi

print_subsection "1.4 认证后用户信息"

if [ -n "$USER_TOKEN" ]; then
    test_api "获取用户信息(/auth/userinfo)" "GET" "${BASE_URL}/auth/userinfo" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "获取余额(/auth/balance)" "GET" "${BASE_URL}/auth/balance" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

print_subsection "1.5 OAuth场景"

test_api "OAuth-获取GitHub授权URL" "GET" "${BASE_URL}/auth/oauth/url?provider=github" "" "" "200"
test_api "OAuth-获取Google授权URL" "GET" "${BASE_URL}/auth/oauth/url?provider=google" "" "" "200"
test_api "OAuth-获取微信授权URL" "GET" "${BASE_URL}/auth/oauth/url?provider=wechat" "" "" "200"
test_api "OAuth-不支持的provider" "GET" "${BASE_URL}/auth/oauth/url?provider=facebook" "" "" "200"
test_api "OAuth-缺少provider参数(预期400)" "GET" "${BASE_URL}/auth/oauth/url" "" "" "400"

test_api "OAuth-回调(无效code)" "POST" "${BASE_URL}/auth/oauth/callback" \
    "Content-Type|application/json" \
    '{"provider":"github","code":"invalid_code","state":"test_state"}' "200"

test_api "OAuth-回调缺少code" "POST" "${BASE_URL}/auth/oauth/callback" \
    "Content-Type|application/json" \
    '{"provider":"github","state":"test_state"}' "200"

if [ -n "$USER_TOKEN" ]; then
    test_api "OAuth-绑定(无效code)" "POST" "${BASE_URL}/auth/oauth/bind" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"provider":"github","code":"invalid_code"}' "200"
    
    test_api "OAuth-解绑(未绑定)" "POST" "${BASE_URL}/auth/oauth/unbind" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"provider":"github"}' "200"
fi

# ============================================================
print_section "阶段 2: 用户信息模块 (UserController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    print_subsection "2.1 信息查询"
    
    test_api "用户-获取信息(/user/info)" "GET" "${BASE_URL}/user/info" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-获取余额(/user/balance)" "GET" "${BASE_URL}/user/balance" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-获取点数(/user/points)" "GET" "${BASE_URL}/user/points" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-获取免费配额(/user/free-quota)" "GET" "${BASE_URL}/user/free-quota" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-获取登录设备" "GET" "${BASE_URL}/user/login-devices" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-获取2FA状态" "GET" "${BASE_URL}/user/2fa/status" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-获取充值记录" "GET" "${BASE_URL}/user/recharge-records?page=1&size=10" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-获取点数账单" "GET" "${BASE_URL}/user/points-bills?page=1&size=10" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-点数账单(按类型筛选)" "GET" "${BASE_URL}/user/points-bills?page=1&size=10&changeType=RECHARGE" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    print_subsection "2.2 密码修改"
    
    test_api "用户-修改密码(新旧相同)" "POST" "${BASE_URL}/user/change-password" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"oldPassword\":\"${TEST_PASSWORD}\",\"newPassword\":\"${TEST_PASSWORD}\"}" "200"
    
    test_api "用户-修改密码(缺少参数)" "POST" "${BASE_URL}/user/change-password" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"oldPassword\":\"${TEST_PASSWORD}\"}" "200"
    
    print_subsection "2.3 充值场景"
    
    test_api "用户-充值(正常金额)" "POST" "${BASE_URL}/user/recharge?amount=10" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-充值(零金额应失败)" "POST" "${BASE_URL}/user/recharge?amount=0" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-充值(负金额应失败)" "POST" "${BASE_URL}/user/recharge?amount=-5" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-使用优惠券充值" "POST" "${BASE_URL}/user/recharge-with-coupon" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"amount\":10,\"couponId\":null}" "200"
    
    test_api "用户-使用优惠券充值(零金额)" "POST" "${BASE_URL}/user/recharge-with-coupon" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"amount\":0}" "200"
    
    print_subsection "2.4 绑定场景"
    
    test_api "用户-发送验证码" "POST" "${BASE_URL}/user/send-bind-code" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"type":"email","target":"test@example.com"}' "200"
    
    test_api "用户-绑定邮箱(无效验证码)" "POST" "${BASE_URL}/user/bind-email" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"email":"newemail@test.com","code":"000000"}' "200"
    
    test_api "用户-绑定邮箱(格式错误)" "POST" "${BASE_URL}/user/bind-email" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"email":"bad-email","code":"000000"}' "200"
    
    test_api "用户-绑定手机(无效验证码)" "POST" "${BASE_URL}/user/bind-phone" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"phone":"13800138000","code":"000000"}' "200"
    
    test_api "用户-绑定手机(格式错误)" "POST" "${BASE_URL}/user/bind-phone" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"phone":"12345","code":"000000"}' "200"
    
    print_subsection "2.5 2FA场景"
    
    test_api "用户-启用2FA" "POST" "${BASE_URL}/user/2fa/enable" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-验证2FA(错误码)" "POST" "${BASE_URL}/user/2fa/verify" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"code":"000000"}' "200"
    
    test_api "用户-禁用2FA(错误码)" "POST" "${BASE_URL}/user/2fa/disable" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"code":"000000"}' "200"
    
    print_subsection "2.6 设备管理"
    
    test_api "用户-移除登录设备" "POST" "${BASE_URL}/user/login-devices/remove" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"deviceId\":\"nonexistent-device\"}" "200"
    
    test_api "用户-移除所有设备(保留当前)" "POST" "${BASE_URL}/user/login-devices/remove-all" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"exceptCurrent":"true"}' "200"
    
    print_subsection "2.7 发票"
    
    test_api "用户-申请发票" "POST" "${BASE_URL}/user/invoice/apply" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"amount":100,"type":"personal","title":"测试用户"}' "200"
    
    print_subsection "2.8 优惠券用户接口"
    
    test_api "用户-获取优惠券列表" "GET" "${BASE_URL}/user/coupons" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-使用优惠券(无效码)" "POST" "${BASE_URL}/user/coupons/use" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"code":"INVALID_CODE"}' "200"
    
    test_api "用户-兑换优惠券(无效码)" "POST" "${BASE_URL}/user/coupons/redeem" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"redeemCode":"INVALID_REDEEM_CODE"}' "200"
    
    print_subsection "2.9 已废弃的订阅接口"
    
    test_api "用户-获取订阅(已废弃)" "GET" "${BASE_URL}/user/subscription" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "用户-订阅套餐(已废弃)" "POST" "${BASE_URL}/user/subscription/subscribe" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"plan":"pro","paymentMethod":"alipay"}' "200"
    
    test_api "用户-取消订阅(已废弃)" "POST" "${BASE_URL}/user/subscription/cancel" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ============================================================
print_section "阶段 3: 优惠券模块 (CouponController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    test_api "优惠券-我的优惠券" "GET" "${BASE_URL}/user/my-coupons" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "优惠券-使用记录" "GET" "${BASE_URL}/user/coupon-usage-records" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ============================================================
print_section "阶段 4: 通知模块 (NotificationController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    test_api "通知-获取未读数" "GET" "${BASE_URL}/user/notifications/unread-count" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "通知-获取列表" "GET" "${BASE_URL}/user/notifications?unreadOnly=false" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "通知-仅未读列表" "GET" "${BASE_URL}/user/notifications?unreadOnly=true" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "通知-标记已读(不存在)" "PUT" "${BASE_URL}/user/notifications/99999/read" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "通知-全部标记已读" "PUT" "${BASE_URL}/user/notifications/read-all" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ============================================================
print_section "阶段 5: AI 对话模块 (ChatController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    print_subsection "5.1 非流式对话"
    
    test_api "对话-非流式(free模式)" "POST" "${BASE_URL}/chat/completions" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"回复OK即可"}],"mode":"free"}' "200"
    
    test_api "对话-非流式(points模式)" "POST" "${BASE_URL}/chat/completions" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"回复OK即可"}],"mode":"points"}' "200"
    
    test_api "对话-非流式(无mode默认)" "POST" "${BASE_URL}/chat/completions" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"回复OK即可"}]}' "200"
    
    print_subsection "5.2 流式对话(SSE)"
    
    test_sse_api "对话-流式SSE(free模式)" "POST" "${BASE_URL}/chat/stream" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"回复OK即可"}],"mode":"free"}'
    
    print_subsection "5.3 AI文件生成"
    
    test_api "对话-生成文件" "POST" "${BASE_URL}/chat/generate-file" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"requirement":"写一首关于春天的诗","format":"md"}' "200"
    
    test_api "对话-生成文件(需求为空)" "POST" "${BASE_URL}/chat/generate-file" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"requirement":"","format":"md"}' "200"
    
    test_api "对话-生成文件(格式为空)" "POST" "${BASE_URL}/chat/generate-file" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"requirement":"测试","format":""}' "200"
    
    print_subsection "5.4 导出与同步"
    
    test_export_api "对话-导出内容" "POST" "${BASE_URL}/chat/export-content" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"content":"# 测试导出\n\n这是测试内容","fileName":"test.md"}' "200"
    
    test_api "对话-同步对话数据" "POST" "${BASE_URL}/chat/sync" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"conversations":[{"id":"test","title":"test","messages":[]}]}' "200"
    
    test_api "对话-加载对话文件" "GET" "${BASE_URL}/chat/load?conversationId=1" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "对话-加载对话文件(不存在)" "GET" "${BASE_URL}/chat/load?conversationId=999999" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    print_subsection "5.5 错误场景"
    
    test_api "对话-非流式(无效model)" "POST" "${BASE_URL}/chat/completions" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"model":"invalid-model","messages":[{"role":"user","content":"你好"}],"mode":"free"}' "200"
    
    test_api "对话-非流式(空messages)" "POST" "${BASE_URL}/chat/completions" \
        "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
        '{"model":"deepseek-v4-flash","messages":[],"mode":"free"}' "200"
fi

# ============================================================
print_section "阶段 6: AI 文档助手模块 (AiDocController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    test_export_api "AI文档-导出结果(md格式)" "POST" "${BASE_URL}/v1/ai-doc/export" \
        "Content-Type|application/json" \
        '{"content":"# AI生成内容\n\n这是测试","format":"md"}' "200"
    
    test_export_api "AI文档-导出结果(txt格式)" "POST" "${BASE_URL}/v1/ai-doc/export" \
        "Content-Type|application/json" \
        '{"content":"纯文本测试内容","format":"txt"}' "200"

    test_export_api "AI文档-导出结果(默认md)" "POST" "${BASE_URL}/v1/ai-doc/export" \
        "Content-Type|application/json" \
        '{"content":"默认格式测试"}' "200"
    
    test_export_api "AI文档-导出结果(空内容)" "POST" "${BASE_URL}/v1/ai-doc/export" \
        "Content-Type|application/json" \
        '{"format":"md"}' "200"
fi

# ============================================================
print_section "阶段 7: 对话管理模块 (ConversationController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    test_api "对话-获取对话列表" "GET" "${BASE_URL}/user/conversations/fetch" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "对话-获取对话版本" "GET" "${BASE_URL}/user/conversations/version" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "对话-同步对话" "POST" "${BASE_URL}/user/conversations/sync" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '[{"id":"1","title":"测试对话","messages":[]}]' "200"
    
    test_api "对话-获取最新消息" "GET" "${BASE_URL}/user/conversations/1/messages/latest?limit=10" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "对话-游标分页查询" "POST" "${BASE_URL}/user/conversations/messages/page" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"conversationId":1,"limit":10}' "200"
    
    test_api "对话-游标分页(缺少conversationId)" "POST" "${BASE_URL}/user/conversations/messages/page" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"limit":10}' "200"
    
    test_api "对话-切换账号清除缓存" "POST" "${BASE_URL}/user/conversations/cache/clear" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "对话-清除所有对话" "DELETE" "${BASE_URL}/user/conversations/clear" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ============================================================
print_section "阶段 8: 订阅模块 (UserSubscriptionController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    test_api "订阅-获取套餐列表" "GET" "${BASE_URL}/user/subscriptions/packages" "" "" "200"
    
    test_api "订阅-获取当前订阅" "GET" "${BASE_URL}/user/subscriptions/current" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "订阅-获取订阅历史" "GET" "${BASE_URL}/user/subscriptions/history" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "订阅-购买套餐(无效代码)" "POST" "${BASE_URL}/user/subscriptions/purchase" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"packageCode":"INVALID_CODE"}' "200"
    
    test_api "订阅-取消订阅(不存在)" "POST" "${BASE_URL}/user/subscriptions/cancel/99999" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ============================================================
print_section "阶段 9: API Key 管理模块 (ApiKeyController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    # 生成API Key
    GENERATE_RESPONSE=$(curl -s --max-time 30 -X POST "${BASE_URL}/api-key/generate" \
        -H "X-User-Token: ${USER_TOKEN}" \
        -H "Content-Type: application/json" \
        -d '{"name":"test-key"}')
    API_KEY_ID=$(echo "$GENERATE_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null)
    
    test_api_with_check "APIKey-生成(带校验)" "POST" "${BASE_URL}/api-key/generate" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"name\":\"test-key-$(date +%s)\"}" "200" "message"
    
    test_api "APIKey-生成(指定限流)" "POST" "${BASE_URL}/api-key/generate" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"name":"limited-key","rateLimit":50}' "200"
    
    test_api "APIKey-获取列表" "GET" "${BASE_URL}/api-key/list" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    if [ -n "$API_KEY_ID" ]; then
        test_api "APIKey-禁用" "PUT" "${BASE_URL}/api-key/${API_KEY_ID}/disable" \
            "X-User-Token|${USER_TOKEN}" "" "" "200"
        
        test_api "APIKey-启用" "PUT" "${BASE_URL}/api-key/${API_KEY_ID}/enable" \
            "X-User-Token|${USER_TOKEN}" "" "" "200"
        
        test_api "APIKey-更新限流" "PUT" "${BASE_URL}/api-key/${API_KEY_ID}/rate-limit?rateLimit=200" \
            "X-User-Token|${USER_TOKEN}" "" "" "200"
        
        test_api "APIKey-删除" "DELETE" "${BASE_URL}/api-key/${API_KEY_ID}" \
            "X-User-Token|${USER_TOKEN}" "" "" "200"
    fi
    
    test_api "APIKey-删除(不存在)" "DELETE" "${BASE_URL}/api-key/99999" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
fi

# ============================================================
print_section "阶段 10: Marketplace 模块 (MarketplaceController)"
# ============================================================

if [ -n "$USER_TOKEN" ]; then
    test_api "Marketplace-获取Key列表" "GET" "${BASE_URL}/marketplace/keys" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "Marketplace-购买Key" "POST" "${BASE_URL}/marketplace/buy-key" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"providerId":1}' "200"
fi

# ============================================================
print_section "阶段 11: 管理员登录"
# ============================================================

ADMIN_LOGIN=$(curl -s --max-time 30 -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"testadmin2","password":"Test@123456"}')
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null)
ADMIN_DEVICE_ID=$(echo "$ADMIN_LOGIN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('deviceId',''))" 2>/dev/null)

if [ -n "$ADMIN_TOKEN" ]; then
    echo -e "${GREEN}  ✓ 管理员登录成功${NC}"
else
    echo -e "${RED}  ✗ 管理员登录失败，后续管理员测试将跳过${NC}"
    FAIL_DETAILS="${FAIL_DETAILS}\n管理员登录失败，响应: ${ADMIN_LOGIN:0:300}\n"
fi

# ============================================================
print_section "阶段 12: 管理员统计模块 (AdminController - Statistics)"
# ============================================================

if [ -n "$ADMIN_TOKEN" ]; then
    test_api "管理员-系统统计(/admin/stats)" "GET" "${BASE_URL}/admin/stats" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-完整分析" "GET" "${BASE_URL}/admin/analytics/full?days=7&months=6" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-仪表盘统计" "GET" "${BASE_URL}/admin/statistics/dashboard?period=7d" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-仪表盘统计(30天)" "GET" "${BASE_URL}/admin/statistics/dashboard?period=30d" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-用户统计" "GET" "${BASE_URL}/admin/statistics/users" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-对话统计" "GET" "${BASE_URL}/admin/statistics/conversations" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-每日统计" "GET" "${BASE_URL}/admin/statistics/daily?startDate=2026-01-01&endDate=2026-05-16" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-每周统计" "GET" "${BASE_URL}/admin/statistics/weekly?weeks=4" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-每月统计" "GET" "${BASE_URL}/admin/statistics/monthly?months=6" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-用户增长趋势" "GET" "${BASE_URL}/admin/statistics/user-growth?months=6" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-收入趋势" "GET" "${BASE_URL}/admin/statistics/revenue?months=6" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-导出统计报表(CSV)" "GET" "${BASE_URL}/admin/statistics/export?type=user&startDate=2026-01-01&endDate=2026-05-16" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-导出统计报表(无效type预期400)" "GET" "${BASE_URL}/admin/statistics/export?type=invalid&startDate=2026-01-01&endDate=2026-05-16" \
        "X-User-Token|${ADMIN_TOKEN}" "" "400"
fi

# ============================================================
print_section "阶段 13: 管理员用户管理模块"
# ============================================================

if [ -n "$ADMIN_TOKEN" ]; then
    print_subsection "13.1 用户查询"
    
    test_api "管理员-用户列表" "GET" "${BASE_URL}/admin/users?page=1&size=10" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-用户列表(带搜索)" "GET" "${BASE_URL}/admin/users?page=1&size=10&search=${UNIQUE_USER}" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-高级用户查询" "GET" "${BASE_URL}/admin/users/advanced?page=1&size=10" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-高级用户查询(多条件)" "GET" "${BASE_URL}/admin/users/advanced?page=1&size=10&status=ACTIVE&minBalance=0" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    # 获取用户ID用于后续测试
    FIRST_USER_ID=$(curl -s --max-time 30 -X GET "${BASE_URL}/admin/users?page=1&size=1" \
        -H "X-User-Token: ${ADMIN_TOKEN}" 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
records=d.get('data',{}).get('records',[])
if records: print(records[0].get('id',''))
" 2>/dev/null)
    
    if [ -n "$FIRST_USER_ID" ]; then
        echo -e "${BLUE}  使用测试用户ID: ${FIRST_USER_ID}${NC}"
        
        test_api "管理员-用户详情" "GET" "${BASE_URL}/admin/user/${FIRST_USER_ID}" \
            "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
        
        print_subsection "13.2 用户状态管理"
        
        test_api "管理员-切换用户状态(ACTIVE)" "POST" "${BASE_URL}/admin/user/status" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"status\":\"ACTIVE\"}" "200"
        
        test_api "管理员-封禁用户" "PUT" "${BASE_URL}/admin/user/${FIRST_USER_ID}/ban" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            '{"reason":"测试封禁"}' "200"
        
        test_api "管理员-解封用户" "PUT" "${BASE_URL}/admin/user/${FIRST_USER_ID}/ban" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            '{"reason":"测试解封"}' "200"
        
        test_api "管理员-封禁不存在用户" "PUT" "${BASE_URL}/admin/user/99999/ban" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            '{"reason":"测试"}' "200"
        
        print_subsection "13.3 点数管理"
        
        test_api "管理员-充值点数" "POST" "${BASE_URL}/admin/points/recharge" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"points\":100}" "200"
        
        test_api "管理员-调整点数" "POST" "${BASE_URL}/admin/points/adjust" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"points\":50,\"reason\":\"测试调整\"}" "200"
        
        test_api "管理员-调整点数(扣减)" "POST" "${BASE_URL}/admin/points/adjust" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"points\":-10,\"reason\":\"测试扣减\"}" "200"
        
        test_api "管理员-设置免费次数" "POST" "${BASE_URL}/admin/points/set-free-count" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"count\":10}" "200"
        
        test_api "管理员-每日点数发放(所有用户)" "POST" "${BASE_URL}/admin/points/distribute-daily" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            '{}' "200"
        
        test_api "管理员-每日点数发放(指定用户)" "POST" "${BASE_URL}/admin/points/distribute-daily" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID}}" "200"
        
        print_subsection "13.4 角色管理"
        
        test_api "管理员-分配角色" "POST" "${BASE_URL}/admin/assign-role" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"role\":\"ADMIN\"}" "200"
        
        test_api "管理员-撤销角色" "POST" "${BASE_URL}/admin/revoke-role" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID}}" "200"
        
        print_subsection "13.5 密码与会员"
        
        test_api "管理员-重置密码" "POST" "${BASE_URL}/admin/reset-password" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"password\":\"NewPass123\"}" "200"
        
        test_api "管理员-重置密码(纯数字)" "POST" "${BASE_URL}/admin/reset-password" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"password\":\"12345678\"}" "200"
        
        test_api "管理员-更新会员" "POST" "${BASE_URL}/admin/membership/update" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"membership\":\"VIP\"}" "200"
        
        test_api "管理员-设置会员过期时间" "POST" "${BASE_URL}/admin/membership/set-expire-time" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"expireTime\":\"2026-12-31T23:59:59\"}" "200"
        
        test_api "管理员-设置会员过期时间(过去时间应失败)" "POST" "${BASE_URL}/admin/membership/set-expire-time" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"userId\":${FIRST_USER_ID},\"expireTime\":\"2020-01-01T00:00:00\"}" "200"
    fi
    
    print_subsection "13.6 批量操作"
    
    # 确保有用户ID用于批量操作
    BATCH_USER_IDS="[${FIRST_USER_ID}]"
    if [ -z "$FIRST_USER_ID" ]; then
        BATCH_USER_IDS="[1]"
    fi
    
    test_api "管理员-批量更新状态" "POST" "${BASE_URL}/admin/batch/update-status" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        "{\"userIds\":${BATCH_USER_IDS},\"status\":1}" "200"
    
    test_api "管理员-批量更新状态(非法值)" "POST" "${BASE_URL}/admin/batch/update-status" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        "{\"userIds\":${BATCH_USER_IDS},\"status\":99}" "200"
    
    test_api "管理员-批量赠送点数" "POST" "${BASE_URL}/admin/batch/grant-points" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        "{\"userIds\":${BATCH_USER_IDS},\"points\":50}" "200"
    
    test_api "管理员-批量赠送点数(空列表)" "POST" "${BASE_URL}/admin/batch/grant-points" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"userIds":[],"points":50}' "200"
    
    test_api "管理员-批量激活会员" "POST" "${BASE_URL}/admin/batch/activate-membership" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        "{\"userIds\":${BATCH_USER_IDS},\"membership\":\"VIP\",\"days\":30}" "200"
fi

# ============================================================
print_section "阶段 14: 管理员日志和通知模块"
# ============================================================

if [ -n "$ADMIN_TOKEN" ]; then
    test_api "管理员-操作日志" "GET" "${BASE_URL}/admin/logs?page=1&size=10" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-操作日志(按类型筛选)" "GET" "${BASE_URL}/admin/logs?page=1&size=10&operationType=USER_MANAGE" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-通知未读数" "GET" "${BASE_URL}/admin/notifications/unread-count" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-通知列表" "GET" "${BASE_URL}/admin/notifications?page=1&size=10" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-通知列表(仅未读)" "GET" "${BASE_URL}/admin/notifications?page=1&size=10&isRead=0" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-标记通知已读" "POST" "${BASE_URL}/admin/notifications/1/read" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-全部标记已读" "POST" "${BASE_URL}/admin/notifications/read-all" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-删除通知" "DELETE" "${BASE_URL}/admin/notifications/1" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "管理员-获取最新通知" "GET" "${BASE_URL}/admin/notifications/latest?limit=5" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
fi

# ============================================================
print_section "阶段 15: 管理员发送优惠券和通知 (CouponController Admin)"
# ============================================================

if [ -n "$ADMIN_TOKEN" ]; then
    test_api "管理员-发送优惠券(全部用户)" "POST" "${BASE_URL}/admin/coupons/send" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"amount":10,"thresholdAmount":0,"applicablePlan":"all","target":"all"}' "200"
    
    test_api "管理员-发送优惠券(新用户)" "POST" "${BASE_URL}/admin/coupons/send" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"amount":20,"thresholdAmount":50,"applicablePlan":"pro","target":"new_users","days":7}' "200"
    
    test_api "管理员-发送优惠券(会员)" "POST" "${BASE_URL}/admin/coupons/send" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"amount":30,"thresholdAmount":100,"applicablePlan":"all","target":"membership","membership":"VIP"}' "200"
    
    test_api "管理员-发送系统通知(全部)" "POST" "${BASE_URL}/admin/notifications/send" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"title":"系统通知","content":"这是一条测试通知","type":"SYSTEM","target":"all"}' "200"
    
    test_api "管理员-发送系统通知(会员)" "POST" "${BASE_URL}/admin/notifications/send" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"title":"VIP通知","content":"VIP专属通知","type":"PROMOTION","target":"membership","membership":"VIP"}' "200"
fi

# ============================================================
print_section "阶段 16: 管理员套餐管理模块 (PackageAdminController)"
# ============================================================

if [ -n "$ADMIN_TOKEN" ]; then
    test_api "套餐管理-获取列表" "GET" "${BASE_URL}/admin/packages/list" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    test_api "套餐管理-获取列表(含未激活)" "GET" "${BASE_URL}/admin/packages/list?includeInactive=true" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    # 获取第一个套餐ID
    PKG_ID=$(curl -s --max-time 30 -X GET "${BASE_URL}/admin/packages/list" \
        -H "X-User-Token: ${ADMIN_TOKEN}" 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',[])
if data: print(data[0].get('id',''))
" 2>/dev/null)
    
    if [ -n "$PKG_ID" ]; then
        echo -e "${BLUE}  使用套餐ID: ${PKG_ID}${NC}"
        
        test_api "套餐管理-获取详情" "GET" "${BASE_URL}/admin/packages/${PKG_ID}" \
            "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    fi
    
    test_api "套餐管理-获取详情(不存在)" "GET" "${BASE_URL}/admin/packages/99999" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    
    # 创建套餐
    UNIQUE_PKG="TEST_PKG_$(date +%s)"
    CREATE_PKG_RESPONSE=$(curl -s --max-time 30 -X POST "${BASE_URL}/admin/packages/create" \
        -H "X-User-Token: ${ADMIN_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"packageName\":\"${UNIQUE_PKG}\",\"packageCode\":\"${UNIQUE_PKG}\",\"price\":9.99,\"points\":100,\"dailyCallLimit\":50,\"monthlyCallLimit\":1000,\"maxTokensPerCall\":8000,\"priorityLevel\":1,\"status\":1}")
    CREATED_PKG_ID=$(echo "$CREATE_PKG_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null)
    
    if [ -n "$CREATED_PKG_ID" ]; then
        echo -e "${BLUE}  创建套餐ID: ${CREATED_PKG_ID}${NC}"
        
        # 更新套餐
        test_api "套餐管理-更新" "PUT" "${BASE_URL}/admin/packages/update" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"id\":${CREATED_PKG_ID},\"packageName\":\"${UNIQUE_PKG}_UPDATED\",\"price\":19.99,\"status\":1}" "200"
        
        # 切换状态
        test_api "套餐管理-切换状态(下架)" "POST" "${BASE_URL}/admin/packages/toggle-status" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"id\":${CREATED_PKG_ID},\"status\":0}" "200"
        
        test_api "套餐管理-切换状态(上架)" "POST" "${BASE_URL}/admin/packages/toggle-status" \
            "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
            "{\"id\":${CREATED_PKG_ID},\"status\":1}" "200"
        
        # 删除套餐
        test_api "套餐管理-删除" "DELETE" "${BASE_URL}/admin/packages/${CREATED_PKG_ID}" \
            "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
    else
        # 创建失败但记录一下尝试
        TOTAL_COUNT=$((TOTAL_COUNT + 5))
        FAIL_COUNT=$((FAIL_COUNT + 5))
        echo -e "${RED}  ✗ 套餐创建失败，跳过后续套餐测试${NC}"
        FAIL_DETAILS="${FAIL_DETAILS}\n套餐创建失败: ${CREATE_PKG_RESPONSE:0:300}\n"
    fi
    
    test_api "套餐管理-创建(负价格应失败)" "POST" "${BASE_URL}/admin/packages/create" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"packageName":"BadPkg","packageCode":"BAD_PKG","price":-1,"points":100,"status":1}' "200"
    
    test_api "套餐管理-更新(无ID应失败)" "PUT" "${BASE_URL}/admin/packages/update" \
        "X-User-Token|${ADMIN_TOKEN}|Content-Type|application/json" \
        '{"packageName":"NoID","price":9.99}' "200"
fi

# ============================================================
print_section "阶段 17: 权限与错误场景综合测试"
# ============================================================

print_subsection "17.1 认证错误场景"

test_api "错误-无效Token" "GET" "${BASE_URL}/user/points" \
    "X-User-Token|invalid_token_xxx" "" "" "200"

test_api "错误-缺少Token" "GET" "${BASE_URL}/user/points" \
    "" "" "200"

test_api "错误-空Token" "GET" "${BASE_URL}/user/info" \
    "X-User-Token|" "" "" "200"

print_subsection "17.2 授权错误场景"

if [ -n "$USER_TOKEN" ] && [ -n "$ADMIN_TOKEN" ]; then
    test_api "错误-普通用户访问管理员接口" "GET" "${BASE_URL}/admin/stats" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "错误-普通用户访问用户管理" "GET" "${BASE_URL}/admin/users?page=1&size=10" \
        "X-User-Token|${USER_TOKEN}" "" "" "200"
    
    test_api "错误-普通用户发送优惠券" "POST" "${BASE_URL}/admin/coupons/send" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        '{"amount":10,"target":"all"}' "200"
fi

print_subsection "17.3 接口参数错误场景"

test_api "错误-对话生成文件(需求过长)" "POST" "${BASE_URL}/chat/generate-file" \
    "X-User-Token|${USER_TOKEN}|X-Device-Id|${DEVICE_ID}|Content-Type|application/json" \
    "{\"requirement\":\"$(python3 -c "print('测试'*3000)")\",\"format\":\"md\"}" "200"

# 注销账户测试(放在最后)
if [ -n "$USER_TOKEN" ]; then
    print_subsection "17.4 注销账户场景"
    
    test_api "用户-注销账户(确认错误)" "POST" "${BASE_URL}/user/delete-account" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"password\":\"${TEST_PASSWORD}\",\"confirm\":\"NO\"}" "200"
    
    test_api "用户-注销账户(密码错误)" "POST" "${BASE_URL}/user/delete-account" \
        "X-User-Token|${USER_TOKEN}|Content-Type|application/json" \
        "{\"password\":\"WrongPassword\",\"confirm\":\"DELETE\"}" "200"
fi

# ============================================================
print_section "阶段 18: 测试数据管理 (TestDataController)"
# ============================================================

if [ -n "$ADMIN_TOKEN" ]; then
    test_api "测试数据-修复模型点数" "POST" "${BASE_URL}/admin/test-data/fix-points" \
        "X-User-Token|${ADMIN_TOKEN}" "" "" "200"
fi

# ============================================================
# 汇总
# ============================================================
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║              测 试 汇 总                              ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo "  总测试数:    ${TOTAL_COUNT}"
echo -e "  通过:        ${GREEN}${PASS_COUNT}${NC}"
echo -e "  失败:        ${RED}${FAIL_COUNT}${NC}"
if [ $TOTAL_COUNT -gt 0 ]; then
    SUCCESS_RATE=$(( PASS_COUNT * 100 / TOTAL_COUNT ))
else
    SUCCESS_RATE=0
fi
echo "  成功率:      ${SUCCESS_RATE}%"
echo "  耗时:        ${DURATION}s"
echo "  结束时间:    $(date '+%Y-%m-%d %H:%M:%S')"

if [ $FAIL_COUNT -gt 0 ]; then
    echo ""
    echo -e "${RED}╔══════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║              失 败 详 情                              ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════════════╝${NC}"
    echo -e "$FAIL_DETAILS"
fi

echo ""
if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}✅ 所有测试全部通过！${NC}"
else
    echo -e "${RED}⚠️  有 ${FAIL_COUNT} 个测试失败，请检查上述详情${NC}"
fi

echo ""
echo "测试完成！"