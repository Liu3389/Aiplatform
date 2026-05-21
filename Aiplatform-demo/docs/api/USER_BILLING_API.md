# 用户端计费/会员/点数 API 接口文档

## 📋 目录

- [认证说明](#认证说明)
- [计费规则](#计费规则)
- [套餐一览](#套餐一览)
- [会员状态](#会员状态)
- [点数体系](#点数体系)
- [订阅管理](#订阅管理)
- [账单与记录](#账单与记录)
- [充值相关](#充值相关)
- [接口总览](#接口总览)

---

## 🔐 认证说明

所有用户端计费接口需要在请求 Header 中携带：

```
X-User-Token: {用户Token}
```

统一返回格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

---

## 💰 计费规则

### 核心概念

- **点数（Points）** ≠ 余额（Balance）。点数只用于 AI 对话消耗，余额用于购买套餐
- **余额（元）** → 购买套餐 → 获得 **点数** + 会员权益
- 所有 AI 对话统一按点数扣费，不直接扣余额

### 套餐定价

| 档位   | 月付  | 季付(9折) | 年付(7折) |
|------|-----|--------|--------|
| 免费   | 0元  | -      | -      |
| 白银会员 | 15元 | 40元    | 100元   |
| 黄金会员 | 30元 | 80元    | 200元   |
| 钻石会员 | 60元 | 160元   | 420元   |

### 生效规则

- 套餐开通立即生效
- 会员到期后自动降级为免费普通用户
- 支持会员升级、续费、时长叠加购买

---

## 📦 套餐一览

### 1. 免费普通用户

- 每日自动发放点数：**30 点**（每日清空，限时点数）
- 开通一次性赠送：**0 点**
- 可使用模型：仅基础文字模型（DeepSeek 基础版）
- 开放功能：SSE 极速流式输出、支持图片对话
- 不支持：长文件解析
- Token 限制：输入输出 6000 token

### 2. 白银会员

- 每日自动发放点数：**80 点**（永久有效、可叠加）
- 开通一次性赠送：**600 点**
- 可使用模型：全部基础模型 + 全量中端模型
- 开放权限：SSE 极速流式输出、图片对话、单/多文件上传解析、优先响应
- Token 限制：输入输出 12000 token

### 3. 黄金会员

- 每日自动发放点数：**150 点**（永久有效、可叠加）
- 开通一次性赠送：**1800 点**
- 可使用模型：全量中端模型 + 部分顶级旗舰模型
- 开放权限：SSE 极速流式输出、对话云端存储、多文件解析、长文本连续对话、后台优先队列
- Token 限制：输入输出 18000 token

### 4. 钻石旗舰会员

- 每日自动发放点数：**300 点**（永久有效、可叠加）
- 开通一次性赠送：**5000 点**
- 可使用模型：平台内所有大模型无权限限制（含高清识图、旗舰推理）
- 开放权限：SSE 极速流式输出、对话云端存储、超大体积文件解析、长文本连续对话、最高优先队列
- Token 限制：输入输出 32000 token

---

## 👑 会员状态

### 获取会员状态详情

**接口**: `GET /user/membership/status`

**权限**: 需要登录

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "identityLabel": "silver",
    "identityLabelDisplay": "白银会员",
    "membershipExpireTime": "2026-06-19T00:00:00",
    "balance": 500.000,
    "points": 1358.000,
    "hasActiveSubscription": true,
    "subscription": {
      "id": 1,
      "packageCode": "silver",
      "packageName": "白银月卡会员",
      "identityLabel": "白银会员",
      "startTime": "2026-05-09T00:00:00",
      "expireTime": "2026-06-19T00:00:00",
      "pointsGranted": 600,
      "pricePaid": 15.00
    },
    "dailyPoints": 80.000
  }
}
```

**字段说明**:

| 字段                    | 类型       | 说明                            |
|-----------------------|----------|-------------------------------|
| identityLabel         | string   | 会员标识：free/silver/gold/diamond |
| identityLabelDisplay  | string   | 显示名称：普通用户/白银会员/黄金会员/钻石会员      |
| membershipExpireTime  | datetime | 会员到期时间，null 表示免费用户            |
| balance               | decimal  | 余额（元），用于购买套餐                  |
| points                | decimal  | 点数余额，用于 AI 对话                 |
| hasActiveSubscription | boolean  | 是否有活跃订阅                       |
| subscription          | object   | 订阅详情（见下表）                     |
| dailyPoints           | decimal  | 每日发放点数                        |

**Subscription 字段**:

| 字段            | 说明        |
|---------------|-----------|
| id            | 订阅记录ID    |
| packageCode   | 套餐代码      |
| packageName   | 套餐名称      |
| identityLabel | 身份标识      |
| startTime     | 生效时间      |
| expireTime    | 过期时间      |
| pointsGranted | 开通时赠送的点数  |
| pricePaid     | 实际支付金额（元） |

**免费用户响应**:

```json
{
  "code": 200,
  "data": {
    "identityLabel": "free",
    "identityLabelDisplay": "普通用户",
    "membershipExpireTime": null,
    "balance": 100.000,
    "points": 300.000,
    "hasActiveSubscription": false,
    "dailyPoints": 30.000
  }
}
```

---

## 🔢 点数体系

### 1. 查询点数余额

**接口**: `GET /user/points`

**响应**:

```json
{
  "code": 200,
  "data": 1358.000
}
```

### 2. 获取点数消费汇总

**接口**: `GET /user/points/consumption-summary`

**权限**: 需要登录

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "todayConsumed": 5.000,
    "monthConsumed": 120.000,
    "totalConsumed": 456.789,
    "remainingPoints": 1358.000
  }
}
```

**字段说明**:

| 字段              | 说明                 |
|-----------------|--------------------|
| todayConsumed   | 今日消费的点数（DEDUCT 类型） |
| monthConsumed   | 本月消费的点数            |
| totalConsumed   | 累计消费的点数            |
| remainingPoints | 当前剩余点数             |

### 3. 查询点数账单明细

**接口**: `GET /user/points-bills`

**参数**:

| 参数         | 类型     | 必填 | 默认   | 说明                                  |
|------------|--------|----|------|-------------------------------------|
| page       | int    | 否  | 1    | 页码                                  |
| size       | int    | 否  | 20   | 每页大小                                |
| changeType | string | 否  | null | 变动类型筛选：RECHARGE/DEDUCT/GRANT/EXPIRE |

**变动类型说明**:

| 类型                 | 含义   | 场景             |
|--------------------|------|----------------|
| RECHARGE           | 充值   | 用户用余额购买套餐后获得点数 |
| SUBSCRIPTION_GRANT | 套餐赠送 | 订阅套餐赠送的点数      |
| DEDUCT             | 消费   | AI 对话消耗点数      |
| GRANT              | 赠送   | 每日点数发放、管理员赠送等  |
| EXPIRE             | 过期   | 限时点数过期清零       |

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1,
        "changeType": "DEDUCT",
        "pointsChange": -3.000,
        "balanceBefore": 1000.000,
        "balanceAfter": 997.000,
        "businessId": "conv-001",
        "description": "AI对话消耗 - deepseek-v4-flash",
        "modelName": "deepseek-v4-flash",
        "isTemporary": false,
        "createTime": "2026-05-19T10:30:00"
      }
    ],
    "total": 50,
    "page": 1,
    "size": 20
  }
}
```

### 4. 每日点数发放逻辑

系统每日自动为用户发放点数，发放数量根据会员等级确定：

| 会员等级 | 每日发放  | 点数类型 | 是否过期 |
|------|-------|------|------|
| 免费用户 | 30 点  | 限时点数 | 每日清空 |
| 白银会员 | 80 点  | 永久点数 | 可叠加  |
| 黄金会员 | 150 点 | 永久点数 | 可叠加  |
| 钻石会员 | 300 点 | 永久点数 | 可叠加  |

**注意**：付费会员除了每日发放永久点数外，仍然可以获得 30 点限时点数（用于当日未用完的对话）。

---

## 📋 订阅管理

### 1. 获取可用套餐列表

**接口**: `GET /user/subscriptions/packages`

**权限**: 需要登录

**响应示例**:

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "packageCode": "free",
      "packageName": "免费普通用户",
      "description": "基础免费套餐，每日赠送30点数",
      "identityLabel": "普通用户",
      "pointsGranted": 0,
      "dailyPoints": 30,
      "priceMonthly": 0,
      "priceQuarterly": null,
      "priceYearly": null,
      "maxInputTokens": 6000,
      "maxOutputTokens": 6000,
      "modelAccessLevel": 0,
      "status": 1,
      "sortOrder": 1
    },
    {
      "id": 2,
      "packageCode": "silver",
      "packageName": "白银月卡会员",
      "description": "适合个人日常使用，每日80永久点数",
      "identityLabel": "白银会员",
      "pointsGranted": 600,
      "dailyPoints": 80,
      "priceMonthly": 15,
      "priceQuarterly": 40,
      "priceYearly": 100,
      "maxInputTokens": 12000,
      "maxOutputTokens": 12000,
      "modelAccessLevel": 1,
      "status": 1,
      "sortOrder": 2
    }
  ]
}
```

### 2. 获取当前订阅状态

**接口**: `GET /user/subscriptions/current`

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "id": 1,
    "packageCode": "silver",
    "packageName": "白银月卡会员",
    "identityLabel": "白银会员",
    "startTime": "2026-05-09T00:00:00",
    "expireTime": "2026-06-19T00:00:00",
    "status": "ACTIVE",
    "autoRenew": false,
    "pointsGranted": 600,
    "pricePaid": 15.00,
    "daysRemaining": 31
  }
}
```

### 3. 获取订阅历史

**接口**: `GET /user/subscriptions/history`

**参数**:

| 参数   | 类型  | 必填 | 默认 | 说明   |
|------|-----|----|----|------|
| page | int | 否  | 1  | 页码   |
| size | int | 否  | 20 | 每页大小 |

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "packageCode": "silver",
        "packageName": "白银月卡会员",
        "pricePaid": 15.00,
        "startTime": "2026-05-09T00:00:00",
        "expireTime": "2026-06-09T00:00:00",
        "status": "ACTIVE"
      }
    ],
    "total": 3,
    "page": 1,
    "size": 20
  }
}
```

### 4. 购买套餐

**接口**: `POST /user/subscriptions/purchase`

**请求体**:

```json
{
  "packageCode": "silver",
  "couponId": null
}
```

**参数说明**:

| 参数          | 类型     | 必填 | 说明                       |
|-------------|--------|----|--------------------------|
| packageCode | string | 是  | 套餐代码：silver/gold/diamond |
| couponId    | long   | 否  | 使用的优惠券ID（可选）             |

**响应示例**:

```json
{
  "code": 200,
  "message": "购买成功",
  "data": {
    "subscriptionId": 5,
    "packageCode": "silver",
    "packageName": "白银月卡会员",
    "pointsGranted": 600,
    "pricePaid": 15.00,
    "expireTime": "2026-06-19T00:00:00",
    "membershipLevel": "silver"
  }
}
```

**业务逻辑**:

1. 从用户 `balance`（余额）扣款
2. 给用户 `points`（点数）增加对应赠送点数
3. 更新用户 `membership` 字段（身份标识）
4. 创建 `user_subscription` 订阅记录
5. 创建 `points_bill` 点数账单记录（SUBSCRIPTION_GRANT 类型）
6. 更新平台统计数据
7. 升级时自动延长会员有效期（时长叠加）
8. 续费时同样延长有效期

**升级规则**:

| 当前      | 升级后     | 行为        |
|---------|---------|-----------|
| free    | silver  | 开通白银会员    |
| silver  | gold    | 升级为黄金会员   |
| gold    | diamond | 升级为钻石会员   |
| silver  | silver  | 续费（延长有效期） |
| gold    | gold    | 续费（延长有效期） |
| diamond | diamond | 续费（延长有效期） |

### 5. 取消订阅

**接口**: `POST /user/subscriptions/cancel/{subscriptionId}`

**说明**: 取消自动续费，会员权益保留到过期时间。

---

## 📊 账单与记录

### 1. 获取充值/购买历史

**接口**: `GET /user/recharge-history`

**参数**:

| 参数   | 类型  | 必填 | 默认 | 说明   |
|------|-----|----|----|------|
| page | int | 否  | 1  | 页码   |
| size | int | 否  | 20 | 每页大小 |

**响应示例**:

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": "SUB_1715760000001",
        "type": "SUBSCRIPTION",
        "title": "购买 白银月卡会员",
        "amount": 15.00,
        "time": "2026-05-19T10:00:00",
        "detail": "白银会员 | 赠送600.000点",
        "status": "SUCCESS"
      },
      {
        "id": 5,
        "type": "SUBSCRIPTION_GRANT",
        "title": "白银会员套餐赠送",
        "amount": 600.000,
        "time": "2026-05-19T10:00:00",
        "detail": "套餐赠送",
        "status": "SUCCESS"
      },
      {
        "id": 3,
        "type": "RECHARGE",
        "title": "账户充值",
        "amount": 100.000,
        "time": "2026-05-18T15:00:00",
        "detail": "余额充值",
        "status": "SUCCESS"
      }
    ],
    "total": 25,
    "page": 1,
    "size": 20,
    "totalPages": 2
  }
}
```

**记录类型说明**:

| 类型                 | 说明          |
|--------------------|-------------|
| SUBSCRIPTION       | 订阅购买记录（扣余额） |
| SUBSCRIPTION_GRANT | 套餐赠送点数记录    |
| RECHARGE           | 余额充值记录      |

### 2. 获取充值记录（旧版，兼容）

**接口**: `GET /user/recharge-records`

**参数**: `page`, `size`（可选）

**说明**: 查询点数账单中的 RECHARGE 和 SUBSCRIPTION_GRANT 类型记录。

---

## 💵 充值相关

### 1. 充值（增加余额）

**接口**: `POST /user/recharge`

**参数**:

| 参数     | 类型      | 必填 | 说明             |
|--------|---------|----|----------------|
| amount | decimal | 是  | 充值金额（元），必须 > 0 |

**请求示例**: `POST /user/recharge?amount=100`

**响应**:

```json
{
  "code": 200,
  "message": "充值成功",
  "data": {
    "success": true,
    "amount": 100,
    "balanceBefore": 500.000,
    "balanceAfter": 600.000
  }
}
```

**说明**:

- 充值只增加 `balance`（余额，单位：元）
- **不直接增加点数**，需要使用余额购买套餐才能获得点数
- 套餐制度：越贵的套餐越划算

### 2. 使用优惠券充值

**接口**: `POST /user/recharge-with-coupon`

**请求体**:

```json
{
  "amount": 100,
  "couponId": 1
}
```

**响应**:

```json
{
  "code": 200,
  "data": {
    "success": true,
    "amount": 100,
    "actualAmount": 90,
    "discount": 10,
    "balanceBefore": 500.000,
    "balanceAfter": 590.000,
    "couponUsed": true,
    "orderId": "RECHARGE_1715760000000"
  }
}
```

---

## 📋 接口总览

| #  | 方法   | 路径                                 | 说明                   |
|----|------|------------------------------------|----------------------|
| 1  | GET  | `/user/membership/status`          | 获取会员状态详情（身份标识、到期时间等） |
| 2  | GET  | `/user/points`                     | 查询点数余额               |
| 3  | GET  | `/user/points/consumption-summary` | 获取点数消费汇总（今日/本月/总计）   |
| 4  | GET  | `/user/points-bills`               | 查询点数账单明细（分页+类型筛选）    |
| 5  | GET  | `/user/subscriptions/packages`     | 获取可用套餐列表             |
| 6  | GET  | `/user/subscriptions/current`      | 获取当前订阅状态             |
| 7  | GET  | `/user/subscriptions/history`      | 获取订阅历史               |
| 8  | POST | `/user/subscriptions/purchase`     | 购买套餐（支持升级/续费）        |
| 9  | POST | `/user/subscriptions/cancel/{id}`  | 取消订阅                 |
| 10 | GET  | `/user/recharge-history`           | 获取充值/购买历史（完整版）       |
| 11 | GET  | `/user/recharge-records`           | 获取充值记录（兼容旧版）         |
| 12 | POST | `/user/recharge`                   | 充值（增加余额）             |
| 13 | POST | `/user/recharge-with-coupon`       | 使用优惠券充值              |
| 14 | GET  | `/user/balance`                    | 查询余额                 |
| 15 | GET  | `/user/info`                       | 获取用户信息               |
| 16 | GET  | `/user/free-quota`                 | 获取免费额度信息             |

---

## 📌 注意事项

1. **点数 ≠ 余额**：点数用于 AI 对话消耗，余额用于购买套餐
2. **升级逻辑**：高级别会员自动覆盖低级别身份标识，有效期延长
3. **续费逻辑**：同级别购买会延长有效期（时长叠加）
4. **到期处理**：会员到期后自动降级为免费用户，永久点数保留，限时点数清空
5. **每日点数**：系统定时任务每日发放，根据当前生效的会员等级确定数量
6. **消费记录**：`/user/points/consumption-summary` 只统计 `change_type='DEDUCT'` 的记录
7. **所有接口**都需要携带 `X-User-Token` 请求头进行身份认证

---

**文档版本**: v1.0  
**创建日期**: 2026-05-19  
**接口总数**: 16 个计费相关接口
