#!/bin/bash

# 管理员接口全面测试脚本

BASE_URL="http://localhost:8080/api"
TOKEN="5e8521985271401db73441999e86dc87"
ADMIN_ID=1
TEST_USER_ID=2

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass_count=0
fail_count=0
total_count=0

# 测试函数
test_api() {
    local name=$1
    local method=$2
    local url=$3
    local data=$4
    
    total_count=$((total_count + 1))
    echo -e "\n${YELLOW}[$total_count] 测试: ${name}${NC}"
    echo "URL: ${method} ${url}"
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X ${method} "${BASE_URL}${url}" \
            -H "Content-Type: application/json" \
            -H "X-User-Token: ${TOKEN}" \
            -d "$data")
    else
        response=$(curl -s -w "\n%{http_code}" -X ${method} "${BASE_URL}${url}" \
            -H "X-User-Token: ${TOKEN}")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    echo "HTTP状态码: ${http_code}"
    echo "响应: $(echo $body | head -c 200)"
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✅ 通过${NC}"
        pass_count=$((pass_count + 1))
    else
        echo -e "${RED}❌ 失败 (HTTP ${http_code})${NC}"
        fail_count=$((fail_count + 1))
    fi
}

echo "========================================="
echo "管理员接口全面测试"
echo "========================================="

# 1. 基础统计接口
test_api "GET /admin/stats" "GET" "/admin/stats"

# 2. 用户列表
test_api "GET /admin/users" "GET" "/admin/users?page=1&size=10"

# 3. 用户详情
test_api "GET /admin/user/1" "GET" "/admin/user/1"

# 4. 切换用户状态
test_api "POST /admin/user/status" "POST" "/admin/user/status" \
    '{"userId":2,"status":"1"}'

# 5. 点数充值
test_api "POST /admin/points/recharge" "POST" "/admin/points/recharge" \
    '{"userId":2,"points":100}'

# 6. 分配角色（需要SUPER_ADMIN）
test_api "POST /admin/assign-role" "POST" "/admin/assign-role" \
    '{"userId":2,"role":"ADMIN"}'

# 7. 撤销角色
test_api "POST /admin/revoke-role" "POST" "/admin/revoke-role" \
    '{"userId":2}'

# 8. 重置密码
test_api "POST /admin/reset-password" "POST" "/admin/reset-password" \
    '{"userId":2,"password":"Test123456"}'

# 9. 完整分析
test_api "GET /admin/analytics/full" "GET" "/admin/analytics/full?days=7&months=6"

# 10. 高级用户查询
test_api "GET /admin/users/advanced" "GET" "/admin/users/advanced?page=1&size=10"

# 11. 仪表盘统计
test_api "GET /admin/statistics/dashboard" "GET" "/admin/statistics/dashboard?period=7d"

# 12. 用户统计
test_api "GET /admin/statistics/users" "GET" "/admin/statistics/users"

# 13. 对话统计
test_api "GET /admin/statistics/conversations" "GET" "/admin/statistics/conversations"

# 14. 批量更新状态
test_api "POST /admin/batch/update-status" "POST" "/admin/batch/update-status" \
    '{"userIds":[2],"status":1}'

# 15. 批量发放点数
test_api "POST /admin/batch/grant-points" "POST" "/admin/batch/grant-points" \
    '{"userIds":[2],"points":50}'

# 16. 每日点数发放
test_api "POST /admin/points/distribute-daily" "POST" "/admin/points/distribute-daily" \
    '{"userId":2}'

# 17. 调整点数
test_api "POST /admin/points/adjust" "POST" "/admin/points/adjust" \
    '{"userId":2,"points":10,"reason":"测试调整"}'

# 18. 设置免费次数
test_api "POST /admin/points/set-free-count" "POST" "/admin/points/set-free-count" \
    '{"userId":2,"count":30}'

# 19. 更新会员
test_api "POST /admin/membership/update" "POST" "/admin/membership/update" \
    '{"userId":2,"membership":"silver"}'

# 20. 设置会员过期时间
EXPIRE_TIME=$(date -v+30d +"%Y-%m-%dT%H:%M:%S" 2>/dev/null || date -d "+30 days" +"%Y-%m-%dT%H:%M:%S" 2>/dev/null)
test_api "POST /admin/membership/set-expire-time" "POST" "/admin/membership/set-expire-time" \
    "{\"userId\":2,\"expireTime\":\"${EXPIRE_TIME}\"}"

# 21. 批量开通会员
test_api "POST /admin/batch/activate-membership" "POST" "/admin/batch/activate-membership" \
    '{"userIds":[2],"membership":"gold","days":30}'

# 22. 操作日志
test_api "GET /admin/logs" "GET" "/admin/logs?page=1&size=10"

# 23. 未读通知
test_api "GET /admin/notifications/unread-count" "GET" "/admin/notifications/unread-count"

# 24. 通知列表
test_api "GET /admin/notifications" "GET" "/admin/notifications?page=1&size=10"

# 25. 标记通知已读（假设通知ID=1）
test_api "POST /admin/notifications/1/read" "POST" "/admin/notifications/1/read"

# 26. 标记所有通知已读
test_api "POST /admin/notifications/read-all" "POST" "/admin/notifications/read-all"

# 27. 删除通知（假设通知ID=1）
test_api "DELETE /admin/notifications/1" "DELETE" "/admin/notifications/1"

# 28. 最新通知
test_api "GET /admin/notifications/latest" "GET" "/admin/notifications/latest"

# 29. 每日统计
START_DATE="2026-05-01"
END_DATE="2026-05-18"
test_api "GET /admin/statistics/daily" "GET" "/admin/statistics/daily?startDate=${START_DATE}&endDate=${END_DATE}"

# 30. 每周统计
test_api "GET /admin/statistics/weekly" "GET" "/admin/statistics/weekly?weeks=4"

# 31. 每月统计
test_api "GET /admin/statistics/monthly" "GET" "/admin/statistics/monthly?months=6"

# 32. 导出统计报表
test_api "GET /admin/statistics/export" "GET" "/admin/statistics/export?type=user&startDate=${START_DATE}&endDate=${END_DATE}"

# 33. 用户增长
test_api "GET /admin/statistics/user-growth" "GET" "/admin/statistics/user-growth?months=6"

# 34. 收入趋势
test_api "GET /admin/statistics/revenue" "GET" "/admin/statistics/revenue?months=6"

# 35. 封禁/解封用户
test_api "PUT /admin/user/2/ban" "PUT" "/admin/user/2/ban" \
    '{"reason":"测试封禁"}'

# 总结
echo -e "\n========================================="
echo "测试完成！"
echo "总计: ${total_count} 个接口"
echo -e "${GREEN}通过: ${pass_count}${NC}"
echo -e "${RED}失败: ${fail_count}${NC}"
echo "========================================="
