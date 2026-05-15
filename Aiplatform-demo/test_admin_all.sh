#!/bin/bash
BASE="http://localhost:8080"
AT="dc70b1603f7e4df99f7dafe6f8c5e933"

OK=0; FAIL=0
chk(){ local c=$(echo "$1"|grep -o '"code":[0-9]*'|head -1|cut -d: -f2); if [ "$c" = "200" ]||[ "$c" = "201" ];then echo "✅ $2"; ((OK++)); else echo "❌ $2 (code=$c) => $(echo "$1"|head -c 150)"; ((FAIL++)); fi; }
echo "=== 管理接口测试 ==="; echo ""

echo "--- 统计 ---"
chk "$(curl -s "$BASE/api/admin/stats" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "系统统计"
chk "$(curl -s "$BASE/api/admin/analytics/full?days=7&months=6" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "完整分析"
chk "$(curl -s "$BASE/api/admin/statistics/dashboard?period=7d" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "仪表盘统计"
chk "$(curl -s "$BASE/api/admin/statistics/users" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "用户统计"
chk "$(curl -s "$BASE/api/admin/statistics/conversations" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "对话统计"

echo "--- 用户管理 ---"
chk "$(curl -s "$BASE/api/admin/users?page=1&size=10" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "用户列表"
chk "$(curl -s "$BASE/api/admin/users/advanced?page=1&size=10" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "用户高级筛选"
chk "$(curl -s "$BASE/api/admin/user/54" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "用户详情"
chk "$(curl -s -X POST "$BASE/api/admin/user/status" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"status":"ACTIVE"}')" "切换用户状态"
chk "$(curl -s -X POST "$BASE/api/admin/user/recharge" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"amount":50}')" "管理员充值"
chk "$(curl -s -X POST "$BASE/api/admin/assign-role" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"role":"ADMIN"}')" "分配角色"
chk "$(curl -s -X POST "$BASE/api/admin/revoke-role" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54}')" "撤销角色"
chk "$(curl -s -X POST "$BASE/api/admin/reset-password" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"password":"NewPass@123"}')" "重置密码"

echo "--- 批量操作 ---"
chk "$(curl -s -X POST "$BASE/api/admin/batch/update-status" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userIds":[54],"status":1}')" "批量封禁/解封"
chk "$(curl -s -X POST "$BASE/api/admin/batch/grant-points" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userIds":[54],"points":10}')" "批量发放点数"
chk "$(curl -s -X POST "$BASE/api/admin/batch/activate-membership" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userIds":[54],"membership":"vip","days":30}')" "批量开通会员"

echo "--- 点数管理 ---"
chk "$(curl -s -X POST "$BASE/api/admin/points/adjust" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"points":30,"reason":"测试调整"}')" "手动调整点数"
chk "$(curl -s -X POST "$BASE/api/admin/points/set-free-count" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"count":20}')" "设置免费次数"
chk "$(curl -s -X POST "$BASE/api/admin/points/distribute-daily" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54}')" "手动发放每日点数"

echo "--- 会员管理 ---"
chk "$(curl -s -X POST "$BASE/api/admin/membership/update" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"membership":"vip"}')" "调整会员等级"
chk "$(curl -s -X POST "$BASE/api/admin/membership/set-expire-time" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"userId":54,"expireTime":"2027-12-31T23:59:59"}')" "设置会员有效期"

echo "--- 操作日志 ---"
chk "$(curl -s "$BASE/api/admin/logs?page=1&size=10" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "操作日志"

echo "--- 通知管理 ---"
chk "$(curl -s "$BASE/api/admin/notifications/unread-count" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "通知未读数"
chk "$(curl -s "$BASE/api/admin/notifications?page=1&size=10" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "通知列表"
chk "$(curl -s -X POST "$BASE/api/admin/notifications/1/read" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "标记通知已读"
#chk "$(curl -s -X POST "$BASE/api/admin/notifications/read-all" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "全部标记已读"
#chk "$(curl -s -X DELETE "$BASE/api/admin/notifications/1" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "删除通知"
chk "$(curl -s "$BASE/api/admin/notifications/latest?limit=5" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "最新通知"

echo "--- 统计分析 ---"
chk "$(curl -s "$BASE/api/admin/statistics/daily?startDate=2026-05-01&endDate=2026-05-15" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "按日统计"
chk "$(curl -s "$BASE/api/admin/statistics/weekly?weeks=4" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "按周统计"
chk "$(curl -s "$BASE/api/admin/statistics/monthly?months=6" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "按月统计"
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/admin/statistics/export?type=user&startDate=2026-05-01&endDate=2026-05-15" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "导出CSV"

echo "--- 优惠券与通知发送 ---"
chk "$(curl -s -X POST "$BASE/api/admin/coupons/send" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"amount":100,"thresholdAmount":0,"expireTime":"2026-12-31T23:59:59","target":"all"}')" "发放优惠券"
chk "$(curl -s -X POST "$BASE/api/admin/notifications/send" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"title":"测试通知","content":"这是一条测试通知","type":"system","target":"all"}')" "发送系统通知"

echo "--- 套餐模板管理 ---"
chk "$(curl -s "$BASE/api/admin/packages/list" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "套餐列表"
chk "$(curl -s "$BASE/api/admin/packages/1" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001")" "套餐详情"
chk "$(curl -s -X POST "$BASE/api/admin/packages/create" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"name":"TestPackage","code":"test-pkg","description":"Test","price":9.99,"pointsPerDay":100,"dailyCallLimit":200,"status":1}')" "创建套餐"
chk "$(curl -s -X PUT "$BASE/api/admin/packages/update" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"id":1,"name":"UpdatedPackage","description":"Updated desc"}')" "更新套餐"
chk "$(curl -s -X POST "$BASE/api/admin/packages/toggle-status" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"id":1,"status":0}')" "下架套餐"
chk "$(curl -s -X POST "$BASE/api/admin/packages/toggle-status" -H "X-User-Token:$AT" -H "X-Device-Id:admin-device-001" -H "Content-Type:application/json" -d '{"id":1,"status":1}')" "上架套餐"

echo ""
echo "=== 通过:$OK  失败:$FAIL  总计:$((OK+FAIL)) ==="
