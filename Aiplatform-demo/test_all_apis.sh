#!/bin/bash

# ===============================
# AI Gateway 平台全面测试脚本
# 测试所有接口，重点测试流式和非流式对话
# ===============================

BASE_URL="http://localhost:8080/api"
DEEPSEEK_API_KEY="REDACTED_PROVIDER_API_KEY"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 全局变量
TOKEN=""
DEVICE_ID=""
USER_ID=""
API_KEY_ID=""

# ===============================
# 工具函数
# ===============================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
    ((PASSED_TESTS++))
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
    ((FAILED_TESTS++))
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

test_title() {
    echo ""
    echo "=========================================="
    echo -e "${BLUE}测试: $1${NC}"
    echo "=========================================="
    ((TOTAL_TESTS++))
}

check_service() {
    log_info "检查后端服务是否启动..."
    if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/model-config/list" | grep -q "200"; then
        log_success "后端服务运行正常"
        return 0
    else
        log_error "后端服务未启动或无法访问"
        return 1
    fi
}

# ===============================
# 1. 用户认证测试
# ===============================

test_register() {
    test_title "用户注册"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d '{
            "username": "testuser'$(date +%s)'",
            "password": "Test@123456",
            "email": "test'$(date +%s)'@example.com"
        }')
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "注册成功"
        USER_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | cut -d: -f2)
        log_info "用户ID: $USER_ID"
        return 0
    else
        log_error "注册失败: $RESPONSE"
        return 1
    fi
}

test_login() {
    test_title "用户登录"
    
    if [ -z "$USER_ID" ]; then
        # 如果没有新注册用户，尝试用固定账号登录
        USERNAME="testuser"
        PASSWORD="Test@123456"
    else
        # 使用刚才注册的用户
        USERNAME="testuser'$(date +%s)'"
        USERNAME="testuser"
        PASSWORD="Test@123456"
    fi
    
    DEVICE_ID=$(uuidgen)
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -H "X-Device-Id: $DEVICE_ID" \
        -d '{
            "username": "testuser",
            "password": "Test@123456"
        }')
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        TOKEN=$(echo "$RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        USER_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | cut -d: -f2)
        log_success "登录成功"
        log_info "Token: ${TOKEN:0:20}..."
        log_info "Device ID: $DEVICE_ID"
        log_info "User ID: $USER_ID"
        return 0
    else
        # 如果登录失败，尝试注册新用户
        log_warning "登录失败，尝试注册新用户..."
        
        USERNAME="testuser$(date +%s)"
        RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
            -H "Content-Type: application/json" \
            -d "{
                \"username\": \"$USERNAME\",
                \"password\": \"Test@123456\",
                \"email\": \"$USERNAME@example.com\"
            }")
        
        if echo "$RESPONSE" | grep -q '"code":200'; then
            log_success "注册成功，重新登录..."
            
            RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
                -H "Content-Type: application/json" \
                -H "X-Device-Id: $DEVICE_ID" \
                -d "{
                    \"username\": \"$USERNAME\",
                    \"password\": \"Test@123456\"
                }")
            
            if echo "$RESPONSE" | grep -q '"code":200'; then
                TOKEN=$(echo "$RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
                USER_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | cut -d: -f2)
                log_success "登录成功"
                return 0
            fi
        fi
        
        log_error "登录/注册失败: $RESPONSE"
        return 1
    fi
}

test_get_user_info() {
    test_title "获取用户信息"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/auth/userinfo" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取用户信息成功"
        log_info "响应: $(echo "$RESPONSE" | head -c 200)..."
        return 0
    else
        log_error "获取用户信息失败: $RESPONSE"
        return 1
    fi
}

test_get_balance() {
    test_title "获取余额"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/auth/balance" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取余额成功"
        log_info "响应: $RESPONSE"
        return 0
    else
        log_error "获取余额失败: $RESPONSE"
        return 1
    fi
}

test_get_points() {
    test_title "获取点数余额"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/user/points" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取点数余额成功"
        log_info "响应: $RESPONSE"
        return 0
    else
        log_error "获取点数余额失败: $RESPONSE"
        return 1
    fi
}

# ===============================
# 2. API Key 管理测试
# ===============================

test_generate_api_key() {
    test_title "生成API Key"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/api-key/generate" \
        -H "Content-Type: application/json" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID" \
        -d '{
            "name": "测试API Key",
            "rateLimit": 100
        }')
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        API_KEY=$(echo "$RESPONSE" | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
        API_KEY_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | cut -d: -f2)
        log_success "生成API Key成功"
        log_info "API Key: ${API_KEY:0:20}..."
        log_info "API Key ID: $API_KEY_ID"
        return 0
    else
        log_error "生成API Key失败: $RESPONSE"
        return 1
    fi
}

test_list_api_keys() {
    test_title "获取API Key列表"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/api-key/list" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取API Key列表成功"
        log_info "响应: $(echo "$RESPONSE" | head -c 200)..."
        return 0
    else
        log_error "获取API Key列表失败: $RESPONSE"
        return 1
    fi
}

# ===============================
# 3. 模型配置测试
# ===============================

test_model_list() {
    test_title "获取模型列表"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/model-config/list")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取模型列表成功"
        log_info "响应: $RESPONSE"
        return 0
    else
        log_error "获取模型列表失败: $RESPONSE"
        return 1
    fi
}

# ===============================
# 4. 对话功能测试（重点）
# ===============================

test_chat_non_stream_free() {
    test_title "免费模式 - 非流式对话"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/chat/completions" \
        -H "Content-Type: application/json" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID" \
        -X-API-Key "$API_KEY" \
        -d '{
            "model": "deepseek-v4-flash",
            "mode": "free",
            "messages": [
                {
                    "role": "user",
                    "content": "你好，请简单介绍一下你自己（不超过50字）"
                }
            ],
            "stream": false,
            "temperature": 0.7
        }')
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "非流式对话成功"
        CONTENT=$(echo "$RESPONSE" | grep -o '"data":"[^"]*"' | cut -d'"' -f4 | sed 's/\\u/\\u/g')
        log_info "AI回复: $CONTENT"
        return 0
    else
        log_error "非流式对话失败: $RESPONSE"
        return 1
    fi
}

test_chat_stream_free() {
    test_title "免费模式 - 流式对话(SSE)"
    
    log_info "开始流式对话测试（这个会持续接收数据）..."
    
    # 使用curl的--no-buffer来测试SSE
    curl -s -N -X POST "$BASE_URL/chat/stream" \
        -H "Content-Type: application/json" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID" \
        -H "X-API-Key: $API_KEY" \
        -d '{
            "model": "deepseek-v4-flash",
            "mode": "free",
            "messages": [
                {
                    "role": "user",
                    "content": "写一首关于春天的短诗（4句）"
                }
            ],
            "stream": true,
            "temperature": 0.8
        }' 2>&1 | head -50 > /tmp/stream_output.txt &
    
    CURL_PID=$!
    sleep 10
    
    # 检查是否有输出
    if [ -s /tmp/stream_output.txt ]; then
        log_success "流式对话测试完成"
        log_info "收到的SSE数据:"
        cat /tmp/stream_output.txt | head -20
        kill $CURL_PID 2>/dev/null || true
        return 0
    else
        log_error "流式对话失败，没有收到数据"
        kill $CURL_PID 2>/dev/null || true
        return 1
    fi
}

test_chat_non_stream_paid() {
    test_title "付费模式 - 非流式对话"
    
    # 先给用户充值点数
    log_info "先给用户充值点数..."
    RESPONSE=$(curl -s -X POST "$BASE_URL/user/recharge?amount=100" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    log_info "充值响应: $RESPONSE"
    
    # 测试付费对话
    RESPONSE=$(curl -s -X POST "$BASE_URL/chat/completions" \
        -H "Content-Type: application/json" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID" \
        -H "X-API-Key: $API_KEY" \
        -d '{
            "model": "deepseek-v4-flash",
            "mode": "paid",
            "messages": [
                {
                    "role": "user",
                    "content": "1+1等于几？"
                }
            ],
            "stream": false,
            "temperature": 0.7
        }')
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "付费模式非流式对话成功"
        CONTENT=$(echo "$RESPONSE" | grep -o '"data":"[^"]*"' | cut -d'"' -f4)
        log_info "AI回复: $CONTENT"
        return 0
    else
        log_error "付费模式非流式对话失败: $RESPONSE"
        return 1
    fi
}

# ===============================
# 5. 用户中心其他接口测试
# ===============================

test_get_user_center_info() {
    test_title "获取用户中心信息"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/user/info" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取用户中心信息成功"
        return 0
    else
        log_error "获取用户中心信息失败: $RESPONSE"
        return 1
    fi
}

test_get_free_quota() {
    test_title "获取免费额度"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/user/free-quota" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取免费额度成功"
        log_info "响应: $RESPONSE"
        return 0
    else
        log_error "获取免费额度失败: $RESPONSE"
        return 1
    fi
}

test_get_points_bills() {
    test_title "获取点数账单"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/user/points-bills?page=1&size=10" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取点数账单成功"
        return 0
    else
        log_error "获取点数账单失败: $RESPONSE"
        return 1
    fi
}

test_get_subscription() {
    test_title "获取订阅信息"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/user/subscription" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取订阅信息成功"
        return 0
    else
        log_error "获取订阅信息失败: $RESPONSE"
        return 1
    fi
}

# ===============================
# 6. 会话管理测试
# ===============================

test_get_conversations() {
    test_title "获取会话列表"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/conversations" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取会话列表成功"
        return 0
    else
        log_error "获取会话列表失败: $RESPONSE"
        return 1
    fi
}

# ===============================
# 7. 通知管理测试
# ===============================

test_get_notifications() {
    test_title "获取通知列表"
    
    RESPONSE=$(curl -s -X GET "$BASE_URL/user/notifications" \
        -H "X-User-Token: $TOKEN" \
        -H "X-Device-Id: $DEVICE_ID")
    
    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_success "获取通知列表成功"
        return 0
    else
        log_error "获取通知列表失败: $RESPONSE"
        return 1
    fi
}

# ===============================
# 主测试流程
# ===============================

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║         AI Gateway 平台全面测试脚本 v1.0                   ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    
    # 检查服务
    if ! check_service; then
        log_error "请先启动后端服务！"
        exit 1
    fi
    
    echo ""
    log_info "使用 DeepSeek API Key: ${DEEPSEEK_API_KEY:0:20}..."
    echo ""
    
    # 1. 用户认证
    test_login
    if [ -n "$TOKEN" ]; then
        test_get_user_info
        test_get_balance
        test_get_points
    fi
    
    # 2. API Key 管理
    if [ -n "$TOKEN" ]; then
        test_generate_api_key
        test_list_api_keys
    fi
    
    # 3. 模型配置
    test_model_list
    
    # 4. 对话功能（重点）
    if [ -n "$TOKEN" ] && [ -n "$API_KEY" ]; then
        test_chat_non_stream_free
        test_chat_stream_free
        test_chat_non_stream_paid
    fi
    
    # 5. 用户中心
    if [ -n "$TOKEN" ]; then
        test_get_user_center_info
        test_get_free_quota
        test_get_points_bills
        test_get_subscription
    fi
    
    # 6. 会话管理
    if [ -n "$TOKEN" ]; then
        test_get_conversations
    fi
    
    # 7. 通知管理
    if [ -n "$TOKEN" ]; then
        test_get_notifications
    fi
    
    # ===============================
    # 测试总结
    # ===============================
    echo ""
    echo "=========================================="
    echo -e "${BLUE}测试总结${NC}"
    echo "=========================================="
    echo -e "总测试数: ${BLUE}$TOTAL_TESTS${NC}"
    echo -e "通过: ${GREEN}$PASSED_TESTS${NC}"
    echo -e "失败: ${RED}$FAILED_TESTS${NC}"
    echo ""
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "${GREEN}✓ 所有测试通过！平台运行正常。${NC}"
    else
        echo -e "${RED}✗ 有 $FAILED_TESTS 个测试失败，请检查。${NC}"
    fi
    
    echo ""
}

# 运行主函数
main "$@"
