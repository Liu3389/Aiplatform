#!/bin/bash

# AI 文档助手功能测试脚本
# 测试文件上传、解析、流式对话等功能

set -e

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
BASE_URL="http://localhost:8080"
TOKEN=""
TEST_FILE="/tmp/test_document.txt"

# 统计
TOTAL=0
PASS=0
FAIL=0

# 打印分隔线
print_header() {
    echo ""
    echo "=========================================="
    echo -e "${YELLOW}$1${NC}"
    echo "=========================================="
}

# 打印测试结果
print_result() {
    local name="$1"
    local status="$2"
    local detail="$3"
    
    TOTAL=$((TOTAL + 1))
    
    if [ "$status" = "PASS" ]; then
        PASS=$((PASS + 1))
        echo -e "  ${GREEN}✓ PASS${NC} $name"
    else
        FAIL=$((FAIL + 1))
        echo -e "  ${RED}✗ FAIL${NC} $name"
        if [ -n "$detail" ]; then
            echo -e "       ${YELLOW}详情: $detail${NC}"
        fi
    fi
}

# 登录获取 Token
login() {
    print_header "步骤 1: 用户登录"
    
    local response=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
        -H "Content-Type: application/json" \
        -d '{
            "username": "testuser",
            "password": "Test@123456"
        }')
    
    TOKEN=$(echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$TOKEN" ]; then
        print_result "用户登录" "PASS"
        echo "  Token: ${TOKEN:0:20}..."
    else
        print_result "用户登录" "FAIL" "无法获取 Token"
        exit 1
    fi
}

# 创建测试文件
create_test_file() {
    print_header "步骤 2: 创建测试文件"
    
    cat > "$TEST_FILE" << 'EOF'
# AI 技术发展报告

## 1. 引言

人工智能（AI）技术正在快速发展，深刻改变着各行各业。本报告总结了当前 AI 技术的主要发展趋势和应用场景。

## 2. 主要技术趋势

### 2.1 大语言模型（LLM）

大语言模型是近年来 AI 领域最重要的突破之一。代表性模型包括：
- GPT 系列（OpenAI）
- Claude 系列（Anthropic）
- DeepSeek 系列（深度求索）
- Llama 系列（Meta）

这些模型在自然语言处理、代码生成、逻辑推理等方面表现出色。

### 2.2 多模态 AI

多模态 AI 能够同时处理文本、图像、音频等多种类型的数据，实现更智能的交互。

### 2.3 AI Agent

AI Agent 能够自主规划、执行复杂任务，是 AI 技术的下一个重要发展方向。

## 3. 应用场景

### 3.1 智能客服

AI 客服系统可以7x24小时响应用户问题，大幅降低人力成本。

### 3.2 内容创作

AI 辅助写作、绘画、视频生成等应用正在改变内容创作行业。

### 3.3 数据分析

AI 可以快速分析海量数据，发现隐藏的模式和洞察。

## 4. 挑战与展望

### 4.1 主要挑战

- 数据隐私和安全
- 算法偏见和公平性
- 能源消耗和环境影响
- 就业结构变化

### 4.2 未来展望

AI 技术将继续快速发展，预计在未来5-10年内将在更多领域实现突破性应用。我们需要在技术创新和社会责任之间找到平衡。

## 5. 结论

AI 技术正处于快速发展的黄金时期，既带来巨大机遇，也面临诸多挑战。我们应该积极拥抱技术变革，同时关注其社会影响。
EOF
    
    print_result "创建测试文件" "PASS"
    echo "  文件路径: $TEST_FILE"
    echo "  文件大小: $(wc -c < "$TEST_FILE") bytes"
}

# 测试 1: 上传文件并解析
test_upload_file() {
    print_header "测试 1: 上传文件并解析（两步流程 - 第一步）"
    
    local response=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/ai-doc/upload" \
        -H "X-User-Token: ${TOKEN}" \
        -F "file=@${TEST_FILE}")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        local file_id=$(echo "$body" | grep -o '"fileId":"[^"]*"' | cut -d'"' -f4)
        
        if [ -n "$file_id" ]; then
            print_result "上传文件并解析" "PASS"
            echo "  File ID: $file_id"
            
            # 保存 fileId 供后续测试使用
            echo "$file_id" > /tmp/test_file_id.txt
            
            # 显示返回的其他信息
            local file_name=$(echo "$body" | grep -o '"fileName":"[^"]*"' | cut -d'"' -f4)
            local file_size=$(echo "$body" | grep -o '"fileSize":[0-9]*' | cut -d':' -f2)
            local status=$(echo "$body" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
            
            echo "  文件名: $file_name"
            echo "  文件大小: $file_size bytes"
            echo "  状态: $status"
        else
            print_result "上传文件并解析" "FAIL" "未返回 fileId"
            echo "  响应: $body"
        fi
    else
        print_result "上传文件并解析" "FAIL" "HTTP $http_code"
        echo "  响应: $body"
    fi
}

# 测试 2: 查询解析状态
test_parse_status() {
    print_header "测试 2: 查询文件解析状态"
    
    local file_id=$(cat /tmp/test_file_id.txt 2>/dev/null)
    
    if [ -z "$file_id" ]; then
        print_result "查询解析状态" "FAIL" "未找到 fileId"
        return
    fi
    
    local response=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/v1/ai-doc/parse-status/${file_id}" \
        -H "X-User-Token: ${TOKEN}")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        local status=$(echo "$body" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        
        if [ "$status" = "parsed" ]; then
            print_result "查询解析状态" "PASS"
            echo "  状态: $status"
        else
            print_result "查询解析状态" "FAIL" "状态不是 parsed: $status"
        fi
    else
        print_result "查询解析状态" "FAIL" "HTTP $http_code"
        echo "  响应: $body"
    fi
}

# 测试 3: 流式对话（基于 fileId）
test_stream_chat() {
    print_header "测试 3: 流式对话（两步流程 - 第二步）"
    
    local file_id=$(cat /tmp/test_file_id.txt 2>/dev/null)
    
    if [ -z "$file_id" ]; then
        print_result "流式对话" "FAIL" "未找到 fileId"
        return
    fi
    
    echo "  发送请求..."
    
    # 使用 curl 测试 SSE，保存响应到文件
    local response_file="/tmp/sse_response.txt"
    
    curl -s -X POST "${BASE_URL}/v1/ai-doc/chat/stream" \
        -H "X-User-Token: ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{
            \"fileId\": \"${file_id}\",
            \"question\": \"请总结这份报告的主要内容\"
        }" > "$response_file" &
    
    local curl_pid=$!
    
    # 等待最多 30 秒
    local wait_count=0
    while kill -0 $curl_pid 2>/dev/null; do
        sleep 1
        wait_count=$((wait_count + 1))
        if [ $wait_count -ge 30 ]; then
            kill $curl_pid 2>/dev/null
            break
        fi
    done
    
    # 检查响应
    if [ -f "$response_file" ] && [ -s "$response_file" ]; then
        local has_status=$(grep -c "event: status" "$response_file" || true)
        local has_content=$(grep -c "event: content" "$response_file" || true)
        local has_done=$(grep -c "event: done" "$response_file" || true)
        
        if [ "$has_status" -gt 0 ] && [ "$has_content" -gt 0 ] && [ "$has_done" -gt 0 ]; then
            print_result "流式对话" "PASS"
            echo "  ✓ 收到 status 事件: $has_status 个"
            echo "  ✓ 收到 content 事件: $has_content 个"
            echo "  ✓ 收到 done 事件: $has_done 个"
            
            # 显示部分回复内容
            echo ""
            echo "  AI 回复预览:"
            echo "  ----------------------------------------"
            grep "data:" "$response_file" | head -3 | sed 's/data: //g' | head -c 200
            echo "..."
            echo "  ----------------------------------------"
        else
            print_result "流式对话" "FAIL" "缺少必要的事件"
            echo "  status 事件: $has_status, content 事件: $has_content, done 事件: $has_done"
        fi
    else
        print_result "流式对话" "FAIL" "未收到响应或响应为空"
    fi
    
    rm -f "$response_file"
}

# 测试 4: 非流式对话（兼容接口）
test_non_stream_chat() {
    print_header "测试 4: 非流式对话（兼容接口）"
    
    local response=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/ai-doc/chat" \
        -H "X-User-Token: ${TOKEN}" \
        -F "file=@${TEST_FILE}" \
        -F "question=请简要介绍 AI 的主要应用场景")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        local success=$(echo "$body" | grep -o '"code":200' || true)
        
        if [ -n "$success" ]; then
            print_result "非流式对话" "PASS"
            
            # 显示部分回复
            local data=$(echo "$body" | grep -o '"data":"[^"]*"' | head -1 | cut -d'"' -f4)
            if [ -n "$data" ]; then
                echo "  回复预览: ${data:0:100}..."
            fi
        else
            print_result "非流式对话" "FAIL" "响应码不是 200"
            echo "  响应: $body"
        fi
    else
        print_result "非流式对话" "FAIL" "HTTP $http_code"
        echo "  响应: $body"
    fi
}

# 测试 5: 导出结果
test_export_result() {
    print_header "测试 5: 导出 AI 结果"
    
    local response=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/ai-doc/export" \
        -H "X-User-Token: ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d '{
            "content": "# 测试导出\n\n这是 AI 生成的内容。",
            "format": "md"
        }' \
        --output /tmp/exported_file.md)
    
    local http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "200" ] && [ -f "/tmp/exported_file.md" ]; then
        local file_size=$(wc -c < /tmp/exported_file.md)
        
        if [ "$file_size" -gt 0 ]; then
            print_result "导出结果" "PASS"
            echo "  导出文件: /tmp/exported_file.md"
            echo "  文件大小: $file_size bytes"
            
            # 显示文件内容
            echo "  文件内容:"
            cat /tmp/exported_file.md | head -5 | sed 's/^/    /'
        else
            print_result "导出结果" "FAIL" "导出文件为空"
        fi
    else
        print_result "导出结果" "FAIL" "HTTP $http_code"
    fi
}

# 测试 6: 删除缓存文件
test_delete_cached_file() {
    print_header "测试 6: 删除缓存文件"
    
    local file_id=$(cat /tmp/test_file_id.txt 2>/dev/null)
    
    if [ -z "$file_id" ]; then
        print_result "删除缓存文件" "FAIL" "未找到 fileId"
        return
    fi
    
    local response=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/v1/ai-doc/upload/${file_id}" \
        -H "X-User-Token: ${TOKEN}")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        local success=$(echo "$body" | grep -o '"code":200' || true)
        
        if [ -n "$success" ]; then
            print_result "删除缓存文件" "PASS"
            echo "  已删除 fileId: $file_id"
        else
            print_result "删除缓存文件" "FAIL" "删除失败"
            echo "  响应: $body"
        fi
    else
        print_result "删除缓存文件" "FAIL" "HTTP $http_code"
        echo "  响应: $body"
    fi
}

# 清理
cleanup() {
    rm -f "$TEST_FILE"
    rm -f /tmp/test_file_id.txt
    rm -f /tmp/exported_file.md
}

# 主函数
main() {
    print_header "AI 文档助手功能测试"
    echo "  Base URL: $BASE_URL"
    echo "  测试时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    
    # 执行测试
    login
    create_test_file
    test_upload_file
    test_parse_status
    test_stream_chat
    test_non_stream_chat
    test_export_result
    test_delete_cached_file
    
    # 清理
    cleanup
    
    # 打印总结
    print_header "测试总结"
    echo "  总测试数: $TOTAL"
    echo -e "  ${GREEN}通过: $PASS${NC}"
    echo -e "  ${RED}失败: $FAIL${NC}"
    echo ""
    
    if [ $FAIL -eq 0 ]; then
        echo -e "${GREEN}✓ 所有测试通过！${NC}"
        exit 0
    else
        echo -e "${RED}✗ 有 $FAIL 个测试失败${NC}"
        exit 1
    fi
}

# 执行
main
