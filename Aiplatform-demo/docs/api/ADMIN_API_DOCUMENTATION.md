# 管理员后台API接口文档

## 📋 目录
- [认证说明](#认证说明)
- [系统统计](#系统统计)
- [用户管理](#用户管理)
- [批量操作](#批量操作)
- [点数管理](#点数管理)
- [会员管理](#会员管理)
- [操作日志](#操作日志)
- [通知管理](#通知管理)
- [套餐管理](#套餐管理)
- [测试数据管理](#测试数据管理)
- [统计分析](#统计分析)

---

## 🔐 认证说明

所有管理员接口需要在Header中携带：
```
X-User-Token: {管理员Token}
```

权限要求：
- `ADMIN` - 普通管理员
- `SUPER_ADMIN` - 超级管理员（拥有所有权限）

---

## 📊 系统统计

### 1. 获取系统统计数据

**接口**: `GET /admin/stats`

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalUsers": 1000,
    "activeUsers": 800,
    "totalApiKeys": 1500,
    "users": [
      {
        "id": 1,
        "username": "testuser",
        "balance": 100.000,
        "totalCalls": 50,  // ✅ 已修复：从数据库聚合真实调用次数
        "totalCost": 5.000 // ✅ 已修复：从数据库聚合真实消费金额
      }
    ],
    "totalCalls": 50000,
    "totalCost": 5000.000,
    "todayCalls": 1000,
    "todayCost": 100.000
  }
}
```

---

### 2. 获取完整分析数据

**接口**: `GET /admin/analytics/full`

**参数**:
- `days`: 天数（默认7）
- `months`: 月数（默认6）

**权限**: ADMIN/SUPER_ADMIN

**响应包含**:
- 系统基础统计
- 每日使用趋势
- 模型使用分布
- 小时流量分布
- Top用户排行
- 营收趋势
- 用户增长

---

## 👥 用户管理

### 3. 分页查询用户（基础版）

**接口**: `GET /admin/users`

**参数**:
- `page`: 页码（默认1）
- `size`: 每页大小（默认20）
- `search`: 搜索关键词（可选，用户名或ID）
- `membership`: 会员等级（可选，free/vip/svip）
- `status`: 状态（可选，ACTIVE/DISABLED）

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "username": "testuser",
        "email": "test@example.com",
        "balance": 100.00,
        "role": "USER",
        "status": "ACTIVE",
        "membership": "free",
        "createTime": "2026-05-01T10:00:00"
      }
    ],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

---

### 4. 分页查询用户（高级版）⭐

**接口**: `GET /admin/users/advanced`

**参数**:
- `page`: 页码（默认1）
- `size`: 每页大小（默认20）
- `search`: 搜索关键词（可选）
- `membership`: 会员等级（可选）
- `status`: 状态（可选）
- `minBalance`: 最小余额（可选）
- `maxBalance`: 最大余额（可选）
- `startDate`: 注册开始日期（可选，格式：yyyy-MM-dd）
- `endDate`: 注册结束日期（可选，格式：yyyy-MM-dd）

**权限**: ADMIN/SUPER_ADMIN

**使用示例**:
```bash
# 查询VIP会员，余额在100-500之间，最近7天注册
GET /admin/users/advanced?page=1&size=20&membership=vip&minBalance=100&maxBalance=500&startDate=2026-05-07&endDate=2026-05-14
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "username": "testuser",
        "email": "test@example.com",
        "balance": 250.00,
        "points": 100.000,
        "role": "USER",
        "status": "ACTIVE",
        "membership": "vip",
        "dailyFreeCount": 20,
        "membershipExpireTime": "2026-12-31T23:59:59",
        "createTime": "2026-05-10T10:00:00"
      }
    ],
    "total": 50,
    "page": 1,
    "size": 20
  }
}
```

---

### 5. 获取用户详情

**接口**: `GET /admin/user/{userId}`

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "balance": 100.00,
    "role": "USER",
    "status": "ACTIVE",
    "apiKeyCount": 3,
    "totalCalls": 500,
    "totalCost": 50.00,
    "createTime": "2026-05-01T10:00:00"
  }
}
```

---

### 6. 切换用户状态

**接口**: `POST /admin/user/status`

**Body**:
```json
{
  "userId": 1,
  "status": "DISABLED"
}
```

**权限**: ADMIN/SUPER_ADMIN

---

### 7. 手动充值点数

**接口**: `POST /admin/points/recharge`

**说明**: 专门用于管理员手动为用户增加点数。

**Body**:
```json
{
  "userId": 1,
  "points": 100.000
}
```

**权限**: ADMIN/SUPER_ADMIN

---

### 8. 分配管理员角色

**接口**: `POST /admin/assign-role`

**Body**:
```json
{
  "userId": 1,
  "role": "ADMIN"
}
```

**权限**: SUPER_ADMIN

---

### 9. 撤销管理员角色

**接口**: `POST /admin/revoke-role`

**Body**:
```json
{
  "userId": 1
}
```

**权限**: SUPER_ADMIN

---

### 10. 重置用户密码

**接口**: `POST /admin/reset-password`

**Body**:
```json
{
  "userId": 1,
  "password": "NewPassword@123"
}
```

**权限**: ADMIN/SUPER_ADMIN

---

## 🔄 批量操作

### 11. 批量封禁/解封用户 ⭐

**接口**: `POST /admin/batch/update-status`

**Body**:
```json
{
  "userIds": [1, 2, 3, 4, 5],
  "status": 0
}
```

**参数说明**:
- `userIds`: 用户ID列表
- `status`: 0=封禁，1=激活

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "message": "批量更新成功",
  "data": {
    "successCount": 5,
    "totalCount": 5
  }
}
```

---

### 12. 批量发放点数 ⭐

**接口**: `POST /admin/batch/grant-points`

**Body**:
```json
{
  "userIds": [1, 2, 3],
  "points": 100.000
}
```

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "message": "批量发放成功",
  "data": {
    "successCount": 3,
    "totalCount": 3,
    "points": 100.000
  }
}
```

---

### 13. 批量开通会员 ⭐

**接口**: `POST /admin/batch/activate-membership`

**Body**:
```json
{
  "userIds": [1, 2, 3],
  "membership": "vip",
  "days": 30
}
```

**参数说明**:
- `userIds`: 用户ID列表
- `membership`: 会员等级（free/vip/svip）
- `days`: 开通天数

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "message": "批量开通成功",
  "data": {
    "successCount": 3,
    "totalCount": 3,
    "membership": "vip",
    "days": 30
  }
}
```

---

## 💰 点数管理

### 14. 手动增减用户点数 ⭐

**接口**: `POST /admin/points/adjust`

**Body**:
```json
{
  "userId": 1,
  "points": 50.000,
  "reason": "活动奖励"
}
```

**参数说明**:
- `userId`: 用户ID
- `points`: 调整数量（正数=增加，负数=减少）
- `reason`: 调整原因（可选，默认"管理员手动调整"）

**权限**: ADMIN/SUPER_ADMIN

**安全机制**:
- ✅ 事务保证数据一致性
- ✅ 防止点数为负数
- ✅ 记录调整原因
- ✅ 完整日志记录

**使用示例**:
```bash
# 增加50点
curl -X POST "http://localhost:8080/api/admin/points/adjust" \
  -H "X-User-Token: YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"points":50.000,"reason":"活动奖励"}'

# 减少30点
curl -X POST "http://localhost:8080/api/admin/points/adjust" \
  -H "X-User-Token: YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"points":-30.000,"reason":"违规扣除"}'
```

---

### 15. 设置每日免费次数 ⭐

**接口**: `POST /admin/points/set-free-count`

**Body**:
```json
{
  "userId": 1,
  "count": 20
}
```

**权限**: ADMIN/SUPER_ADMIN

---

### 16. 手动充值点数 ⭐

**接口**: `POST /admin/points/recharge`

**说明**: 专门用于管理员手动为用户增加点数。

**Body**:
```json
{
  "userId": 1,
  "points": 100.000
}
```

**权限**: ADMIN/SUPER_ADMIN

---

### 17. 手动触发每日点数发放 ⭐

**接口**: `POST /admin/points/distribute-daily`

**说明**: 手动触发每日点数发放任务，用于测试或补发。可以给所有用户或指定用户发放30点。

**Body**（可选）:
```json
{
  "userId": 1
}
```

**参数说明**:
- `userId`: 用户ID（可选，不传则给所有用户发放）

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "message": "点数发放成功",
  "data": {
    "message": "已给所有用户发放30点"
  }
}
```

---

## 👑 会员管理

### 18. 调整会员等级 ⭐

**接口**: `POST /admin/membership/update`

**Body**:
```json
{
  "userId": 1,
  "membership": "vip"
}
```

**参数说明**:
- `membership`: free/vip/svip

**权限**: ADMIN/SUPER_ADMIN

---

### 19. 设置会员有效期 ⭐

**接口**: `POST /admin/membership/set-expire-time`

**Body**:
```json
{
  "userId": 1,
  "expireTime": "2026-12-31T23:59:59"
}
```

**权限**: ADMIN/SUPER_ADMIN

---

## 📝 操作日志

### 20. 查询操作日志 ⭐

**接口**: `GET /admin/logs`

**参数**:
- `page`: 页码（默认1）
- `size`: 每页大小（默认50）
- `adminId`: 管理员ID（可选）
- `operationType`: 操作类型（可选，USER_MANAGE/POINTS_MANAGE/MEMBERSHIP_MANAGE/BATCH_OPERATION/SYSTEM_CONFIG）
- `startDate`: 开始日期（可选，格式：yyyy-MM-dd）
- `endDate`: 结束日期（可选，格式：yyyy-MM-dd）

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "adminId": 1,
        "adminUsername": "admin01",
        "operationType": "POINTS_MANAGE",
        "operationAction": "ADJUST",
        "targetUserIds": "[1]",
        "operationDetail": "{\"points\":50.000,\"reason\":\"活动奖励\"}",
        "ipAddress": "192.168.1.100",
        "result": "SUCCESS",
        "createTime": "2026-05-14T10:00:00"
      }
    ],
    "total": 100,
    "page": 1,
    "size": 50
  }
}
```

---

## 🔔 通知管理

### 21. 获取未读通知数量

**接口**: `GET /admin/notifications/unread-count`

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "unreadCount": 5
  }
}
```

---

### 22. 获取通知列表

**接口**: `GET /admin/notifications`

**参数**:
- `page`: 页码（默认1）
- `size`: 每页大小（默认20）
- `isRead`: 是否已读（可选，0=未读，1=已读，null=全部）

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "title": "充值到账通知",
        "content": "您的账户已充值100元",
        "type": "RECHARGE",
        "isRead": 0,
        "createTime": "2026-05-14T10:00:00"
      }
    ],
    "total": 10,
    "page": 1,
    "size": 20,
    "unreadCount": 5
  }
}
```

---

### 23. 标记通知为已读

**接口**: `POST /admin/notifications/{id}/read`

---

### 24. 标记所有通知为已读

**接口**: `POST /admin/notifications/read-all`

---

### 25. 删除通知

**接口**: `DELETE /admin/notifications/{id}`

---

### 26. 获取最新未读通知

**接口**: `GET /admin/notifications/latest`

**参数**:
- `limit`: 数量限制（默认5）

---

## 📦 套餐管理

### 27. 获取套餐列表

**接口**: `GET /admin/packages/list`

**参数**:
- `includeInactive`: 是否包含已下架套餐（可选，默认false）

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "packageCode": "BASIC",
      "packageName": "基础版",
      "description": "适合个人用户",
      "points": 100.000,
      "price": 29.00,
      "durationDays": 30,
      "status": 1,
      "sortOrder": 1
    }
  ]
}
```

---

### 28. 获取套餐详情

**接口**: `GET /admin/packages/{id}`

**权限**: ADMIN/SUPER_ADMIN

---

### 29. 创建套餐模板

**接口**: `POST /admin/packages/create`

**Body**:
```json
{
  "packageCode": "PRO",
  "packageName": "专业版",
  "description": "适合专业开发者",
  "points": 1000.000,
  "price": 99.00,
  "durationDays": 30,
  "status": 1,
  "sortOrder": 2
}
```

**权限**: ADMIN/SUPER_ADMIN

---

### 30. 更新套餐模板

**接口**: `PUT /admin/packages/update`

**Body**:
```json
{
  "id": 1,
  "packageName": "基础版（更新）",
  "price": 39.00
}
```

**权限**: ADMIN/SUPER_ADMIN

---

### 31. 上架/下架套餐

**接口**: `POST /admin/packages/toggle-status`

**Body**:
```json
{
  "id": 1,
  "status": 1
}
```

**参数说明**:
- `status`: 1=上架，0=下架

**权限**: ADMIN/SUPER_ADMIN

---

### 32. 删除套餐模板

**接口**: `DELETE /admin/packages/{id}`

**权限**: ADMIN/SUPER_ADMIN

---

## 🧪 测试数据管理

### 33. 重置测试数据

**接口**: `POST /admin/test-data/reset`

**功能说明**:
- 清空并重新初始化平台基础配置数据
- 会重置 platform_model_config, marketplace_provider, package_template 表
- ⚠️ 危险操作，仅限测试环境使用

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "message": "平台基础测试数据已重置",
  "data": null
}
```

---

### 34. 修复模型点数配置

**接口**: `POST /admin/test-data/fix-points`

**功能说明**:
- 将所有未配置固定点数的模型统一设置为默认值（3.000点）
- 用于修复点数配置异常的数据

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "message": "模型点数配置已修复",
  "data": null
}
```

---

## 📈 统计分析

### 33. 按日查询统计数据 ⭐

**接口**: `GET /admin/statistics/daily`

**参数**:
- `startDate`: 开始日期（格式：yyyy-MM-dd）
- `endDate`: 结束日期（格式：yyyy-MM-dd）

**权限**: ADMIN/SUPER_ADMIN

**响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "statDate": "2026-05-01",
      "totalUsers": 1000,
      "newUsers": 50,
      "activeUsers": 800,
      "retainedUsers": 750,
      "totalCalls": 10000,
      "totalTokens": 5000000,
      "totalPointsConsumed": 1000.000,
      "rechargeAmount": 5000.00,
      "packageSales": 100,
      "avgOrderValue": 50.00
    }
  ]
}
```

---

### 34. 按周查询统计数据 ⭐

**接口**: `GET /admin/statistics/weekly`

**参数**:
- `weeks`: 周数（默认4）

**权限**: ADMIN/SUPER_ADMIN

---

### 35. 按月查询统计数据 ⭐

**接口**: `GET /admin/statistics/monthly`

**参数**:
- `months`: 月数（默认6）

**权限**: ADMIN/SUPER_ADMIN

---

### 36. 导出统计报表（CSV）⭐

**接口**: `GET /admin/statistics/export`

**参数**:
- `type`: 报表类型（user/revenue/business）
- `startDate`: 开始日期
- `endDate`: 结束日期

**权限**: ADMIN/SUPER_ADMIN

**使用示例**:
```bash
# 导出用户数据报表
curl -X GET "http://localhost:8080/api/admin/statistics/export?type=user&startDate=2026-05-01&endDate=2026-05-14" \
  -H "X-User-Token: YOUR_TOKEN" \
  --output user_report.csv

# 导出营收数据报表
curl -X GET "http://localhost:8080/api/admin/statistics/export?type=revenue&startDate=2026-05-01&endDate=2026-05-14" \
  -H "X-User-Token: YOUR_TOKEN" \
  --output revenue_report.csv
```

**CSV格式示例**:

用户数据报表:
```csv
日期,总用户数,新增用户,活跃用户,留存用户
2026-05-01,1000,50,800,750
2026-05-02,1050,60,820,770
```

营收数据报表:
```csv
日期,充值金额,套餐销量,平均客单价
2026-05-01,5000.00,100,50.00
2026-05-02,5500.00,110,50.00
```

业务数据报表:
```csv
日期,对话次数,Token消耗,点数消耗
2026-05-01,10000,5000000,1000.000
2026-05-02,11000,5500000,1100.000
```

---

## 🎫 优惠券与通知发送

### 37. 发放优惠券

**接口**: `POST /admin/coupons/send`

**Body**:
```json
{
  "amount": 100,
  "thresholdAmount": 0,
  "applicablePlan": null,
  "expireTime": "2026-12-31T23:59:59",
  "target": "all"
}
```

**参数说明**:
- `amount`: 优惠券金额
- `thresholdAmount`: 使用门槛（可选，默认0）
- `applicablePlan`: 适用套餐（可选）
- `expireTime`: 过期时间（可选）
- `target`: 目标群体（all/membership/new_users）
  - all: 所有用户
  - membership: 指定会员等级（需传membership参数）
  - new_users: 最近N天注册用户（需传days参数）

**权限**: ADMIN/SUPER_ADMIN

---

### 38. 发送系统通知

**接口**: `POST /admin/notifications/send`

**Body**:
```json
{
  "title": "系统维护通知",
  "content": "系统将于今晚22:00-24:00进行维护",
  "type": "system",
  "target": "all"
}
```

**参数说明**:
- `title`: 通知标题
- `content`: 通知内容
- `type`: 通知类型（system/info/warning/error）
- `target`: 目标群体（all/membership/new_users）

**权限**: ADMIN/SUPER_ADMIN

---

## 💰 用户充值与优惠券使用

### 39. 使用优惠券充值（用户接口）

**接口**: `POST /user/recharge-with-coupon`

**Body**:
```json
{
  "amount": 100,
  "couponId": 1
}
```

**参数说明**:
- `amount`: 充值金额（点数）
- `couponId`: 优惠券ID（可选，如果不传则按原价充值）

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "amount": 100,
    "actualAmount": 90,
    "balanceBefore": 50,
    "balanceAfter": 140,
    "couponUsed": true,
    "discount": 10,
    "orderId": "RECHARGE_1715760000000"
  }
}
```

**功能说明**:
- 支持在充值时使用优惠券享受折扣
- 系统会自动验证优惠券的有效性（是否过期、是否已使用、是否满足使用条件等）
- 如果优惠券使用失败，会按原价充值并返回提示信息
- 充值成功后会自动记录到点数账单和优惠券使用记录中

---

### 40. 获取优惠券使用记录（用户接口）

**接口**: `GET /user/coupon-usage-records`

**权限**: 需要登录（携带X-User-Token）

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "couponId": 1,
      "couponCode": "COUPON-ABC123",
      "couponAmount": 10.0000,
      "orderType": "RECHARGE",
      "orderId": "RECHARGE_1715760000000",
      "orderAmount": 100.0000,
      "discountAmount": 10.0000,
      "actualAmount": 90.0000,
      "usageTime": "2026-05-15T10:30:00",
      "remark": "优惠券使用 - 通用"
    }
  ]
}
```

**字段说明**:
- `orderType`: 订单类型（RECHARGE-充值, SUBSCRIPTION-订阅, PACKAGE-套餐）
- `orderId`: 关联订单ID
- `orderAmount`: 订单原始金额
- `discountAmount`: 优惠金额
- `actualAmount`: 实际支付金额

---

## 🎯 接口分类总结

### 核心功能接口（⭐标记）

1. **用户管理增强**
   - GET `/admin/users/advanced` - 多条件筛选查询

2. **批量操作**
   - POST `/admin/batch/update-status` - 批量封禁/解封
   - POST `/admin/batch/grant-points` - 批量发放点数
   - POST `/admin/batch/activate-membership` - 批量开通会员

3. **点数管理**
   - POST `/admin/points/adjust` - 手动增减点数
   - POST `/admin/points/set-free-count` - 设置免费次数
   - POST `/admin/points/recharge` - 手动充值点数
   - POST `/admin/points/distribute-daily` - 手动触发每日点数发放

4. **会员管理**
   - POST `/admin/membership/update` - 调整会员等级
   - POST `/admin/membership/set-expire-time` - 设置有效期
   - POST `/admin/batch/activate-membership` - 批量开通会员

5. **操作日志**
   - GET `/admin/logs` - 查询操作日志

6. **统计分析**
   - GET `/admin/statistics/dashboard` - 仪表盘统计
   - GET `/admin/statistics/users` - 用户统计
   - GET `/admin/statistics/conversations` - 对话统计
   - GET `/admin/statistics/daily` - 按日查询
   - GET `/admin/statistics/weekly` - 按周查询
   - GET `/admin/statistics/monthly` - 按月查询
   - GET `/admin/statistics/export` - 导出CSV报表

7. **套餐管理**
   - GET `/admin/packages/list` - 获取套餐列表
   - GET `/admin/packages/{id}` - 获取套餐详情
   - POST `/admin/packages/create` - 创建套餐模板
   - PUT `/admin/packages/update` - 更新套餐模板
   - POST `/admin/packages/toggle-status` - 上架/下架套餐
   - DELETE `/admin/packages/{id}` - 删除套餐模板

8. **测试数据管理**
   - POST `/admin/test-data/reset` - 重置测试数据
   - POST `/admin/test-data/fix-points` - 修复模型点数配置

9. **优惠券与充值**
   - POST `/user/recharge-with-coupon` - 使用优惠券充值
   - GET `/user/coupon-usage-records` - 获取优惠券使用记录

---

## 📌 注意事项

1. **所有管理员接口都需要携带 `X-User-Token` Header**
2. **部分接口需要 `SUPER_ADMIN` 权限**（如角色分配）
3. **批量操作和点数调整都有事务保护**，确保数据一致性
4. **统计报表导出返回CSV文件**，可直接用Excel打开
5. **操作日志会自动记录**所有管理员操作，包括IP地址和操作详情

---

**文档版本**: v3.0  
**更新日期**: 2026-05-15  
**更新内容**: 
- ✅ 新增套餐管理模块完整文档（6个接口）
- ✅ 所有管理接口已 100% 使用 DTO + Validation
- ✅ 修复统计服务逻辑，实现真实数据查询
- ✅ 统一业务术语：将"免费额度"、"付费模型"统一修正为"点数额度"和"充值点数"
- ✅ 优化 SSE 异常处理与超时反馈
- ✅ 优化冷热分页查询性能（基于时间游标）
- ✅ 增加缓存防击穿机制（分布式锁）
- ✅ 完善事务回滚配置（rollbackFor = Exception.class）
- ✅ 增强 API Key 验证状态标识
- ✅ 完善日志敏感信息脱敏
- ✅ 补全套餐管理与测试数据管理接口文档
- ✅ 修复充值接口路径及参数名称不一致问题
- ✅ 修复接口编号重复问题，统一接口序号
- ✅ 添加每日点数发放接口文档
- ✅ 完善所有管理员接口权限控制（@RequireAdmin）
- ✅ 批量操作支持字符串类型用户ID转换
- ✅ 统计数据全部使用真实数据库查询，无假数据
- ✅ 引入 Caffeine 二级缓存提升性能
- ✅ 操作日志异步化降低接口延迟
**接口总数**: 40个管理员接口 + 2个用户接口 = 42个接口
