# 后端重构更新说明 - 套餐与点数体系

## 📅 更新时间
2026-05-17

## ✅ 已完成工作（全部完成）

### 1. 数据库结构调整 ✅
- [x] `package_template` 表字段重构
- [x] `platform_model_config` 表添加 `access_level` 字段
- [x] `points_bill` 表添加 `is_temporary` 字段
- [x] 套餐数据更新为4级会员体系
- [x] 模型配置添加权限等级
- [x] schema.sql / rebuild.sql / testdata_large.sql 全部同步

### 2. 实体类更新 ✅
- [x] `PackageTemplate.java` 字段同步
- [x] `PlatformModelConfig.java` 添加 accessLevel 字段
- [x] `PointsBill.java` 添加 isTemporary 字段
- [x] 标记废弃字段（@Deprecated）

### 3. 核心服务开发 ✅
- [x] `PointsDistributionService` - 点数双发放服务（限时+永久）
- [x] `StatisticsSchedulerConfig` - 每日点数发放 + 订阅过期检查
- [x] `DailyPointsService` 已退役（保留手动发放功能）
- [x] `PointsService` 添加限时/永久点数方法 + isTemporary 账单标记
- [x] `OpenAiChatService` 集成模型权限校验 `validateModelAccess()`

### 4. 权限校验逻辑 ✅
- [x] 模型权限校验集成到 OpenAiChatService（chat + streamChat）
- [x] 对话前检查用户等级与模型等级
- [x] 订阅过期自动降级

### 5. Controller接口开发 ✅
- [x] `GET /platform-model/available` - 获取可用模型列表（含权限标记）
- [x] `GET /user/points/info` - 获取用户点数详情（限时/永久拆分）
- [x] `POST /user/subscriptions/purchase` - 购买套餐
- [x] `GET /user/points-bills` - 点数流水查询
- [x] `GET /user/subscriptions/packages` - 获取套餐列表

### 6. 订阅管理 ✅
- [x] `UserSubscriptionService` 已完成
- [x] 订阅到期检测定时任务（每天00:10）
- [x] 自动降级逻辑

### 7. VO对象创建 ✅
- [x] `PackageVO` - 套餐展示对象
- [x] `UserPointsInfoVO` - 用户点数信息
- [x] `AvailableModelVO` - 可用模型对象

### 8. SQL脚本同步 ✅
- [x] `schema.sql` - platform_model_config(access_level) + points_bill(is_temporary) + package_template(新字段)
- [x] `rebuild.sql` - points_bill(is_temporary) + daily_points注释修正
- [x] `testdata_large.sql` - 套餐和模型数据同步

### 9. 文档编写 ✅
- [x] `docs/reports/UPDATE1.md` 前端对接文档（v2.0 更新）

---

## 🎯 重构概述

根据《平台计费规则.md》全面重构套餐和点数体系，实现：
1. ✅ 4级会员套餐（免费/白银/黄金/钻石）
2. ✅ 多周期订阅（月/季/年）
3. ✅ 每日点数双发放（30限时 + 套餐永久点数可叠加）
4. ✅ 模型权限分级控制
5. ✅ Token输入输出限制
6. ✅ 点数分类管理（限时点数 vs 永久点数）
7. ✅ 订阅过期自动检测与降级
8. ✅ 限时/永久点数在账单中标记区分

---

## 📊 数据库变更

### package_template 表结构调整

**新增字段**：
- `points_granted` - 开通赠送点数（永久有效）
- `daily_points` - 每日发放永久点数（付费会员可叠加，免费套餐为限时30点）
- `price_monthly` - 月付价格
- `price_quarterly` - 季付价格（9折）
- `price_yearly` - 年付价格（7折）
- `max_input_tokens` - 单次最大输入Token数
- `max_output_tokens` - 单次最大输出Token数
- `model_access_level` - 模型访问等级（0-3）

**删除字段**：
- `points` - 替换为 points_granted + daily_points
- `price` - 替换为三周期价格
- `duration_days` - 由订阅时长决定
- `daily_call_limit` - 取消次数限制
- `monthly_call_limit` - 取消次数限制
- `max_tokens_per_call` - 保留但标记为向后兼容

**新套餐配置**：

| 套餐代码 | 名称 | 开通赠送 | 每日点数 | 月付 | 季付 | 年付 | 输入Token | 输出Token | 模型等级 |
|---------|------|---------|---------|------|------|------|----------|----------|---------|
| free | 免费普通用户 | 0 | 30 | ¥0 | - | - | 6000 | 6000 | 0(基础) |
| silver | 白银月卡会员 | 600 | 80 | ¥15 | ¥40 | ¥100 | 12000 | 12000 | 1(中端) |
| gold | 黄金进阶会员 | 1800 | 150 | ¥30 | ¥80 | ¥200 | 18000 | 18000 | 2(高端) |
| diamond | 钻石旗舰会员 | 5000 | 300 | ¥60 | ¥160 | ¥420 | 32000 | 32000 | 3(全部) |

---

## 🔧 核心功能实现

### 1. 定时任务 - 每日点数双发放

**执行时间**：
- 每天凌晨 00:00 - 每日点数双发放
- 每天凌晨 00:10 - 订阅过期检查

**逻辑**：
```java
// StatisticsSchedulerConfig 统一管理定时任务
@Scheduled(cron = "0 0 0 * * ?")
public void distributeDailyPoints() {
    pointsDistributionService.distributeDailyPoints();
}

@Scheduled(cron = "0 10 0 * * ?")
public void checkSubscriptionExpiry() {
    // 查询所有 ACTIVE 但已过期的订阅
    // 标记为 EXPIRED，用户降级为免费
}
```

**点数发放规则（PointsDistributionService）**：
- **免费用户**：发放30限时点数（当日有效）
- **白银会员**：发放30限时点数 + 80永久点数（可叠加）
- **黄金会员**：发放30限时点数 + 150永久点数（可叠加）
- **钻石会员**：发放30限时点数 + 300永久点数（可叠加）

**点数类型**：
- **限时点数**：每日发放，当日23:59过期，is_temporary=1
- **永久点数**：每日套餐发放 + 开通赠送 + 充值获得，永不过期，is_temporary=0

**消费顺序**：优先消耗限时点数，再消耗永久点数

---

### 2. 模型权限校验

**权限校验位置**：`OpenAiChatService.validateModelAccess()`

**校验时机**：每次对话请求（chat + streamChat）开始时

**等级映射**：
- 0 - 基础模型（DeepSeek-V4-Flash）
- 1 - 中端模型（DeepSeek-V4-Pro）
- 2 - 高端模型（GPT-4o, Claude-3）
- 3 - 旗舰模型（所有模型）

**校验逻辑**：
```java
// 对话前校验
if (userModelAccessLevel < modelRequiredLevel) {
    throw new BusinessException("当前会员等级无法使用该模型，请升级套餐");
}
```

---

### 3. Token限制检查

**后台静默检查**（不告诉用户具体限制）：
- 输入Token超限 → 提示"请求内容过长，请缩短输入"
- 输出Token超限 → 自动调整max_tokens参数

**不同套餐限制**：
- 免费：输入6000 / 输出6000
- 白银：输入12000 / 输出12000
- 黄金：输入18000 / 输出18000
- 钻石：输入32000 / 输出32000

---

## 📡 接口规范

### 1. 获取套餐列表

**接口**：`GET /api/user/subscriptions/packages`

**返回**：
```json
{
  "code": 200,
  "data": [
    {
      "packageCode": "free",
      "packageName": "免费普通用户",
      "identityLabel": "普通用户",
      "pointsGranted": 0,
      "dailyPoints": 30,
      "priceMonthly": 0,
      "priceQuarterly": null,
      "priceYearly": null,
      "maxInputTokens": 6000,
      "maxOutputTokens": 6000,
      "modelAccessLevel": 0,
      "availableModels": ["deepseek-v4-flash"],
      "features": ["SSE流式输出", "图片对话"]
    }
  ]
}
```

---

### 2. 获取用户点数信息

**接口**：`GET /api/user/points/info`

**返回**：
```json
{
  "code": 200,
  "data": {
    "totalPoints": 1250.000,
    "temporaryPoints": 30.000,
    "permanentPoints": 1220.000,
    "todayReceived": 30.000,
    "nextDistributeTime": "2026-05-17 00:00:00",
    "currentPackage": {
      "packageCode": "silver",
      "packageName": "白银月卡会员",
      "expireTime": "2026-06-16 23:59:59"
    }
  }
}
```

---

### 3. 获取可用模型列表

**接口**：`GET /api/platform-model/available`

**返回**：
```json
{
  "code": 200,
  "data": {
    "availableModels": [
      {
        "displayName": "DeepSeek-V4-Flash",
        "actualModel": "deepseek-v4-flash",
        "fixedPoints": 1.000,
        "supportsVision": false,
        "accessible": true
      },
      {
        "displayName": "GPT-4o",
        "actualModel": "gpt-4o",
        "fixedPoints": 5.000,
        "supportsVision": true,
        "accessible": false,
        "requiredLevel": 2,
        "upgradeHint": "升级至黄金会员即可使用"
      }
    ],
    "currentUserLevel": 1,
    "currentPackageName": "白银月卡会员"
  }
}
```

---

### 4. 购买套餐

**接口**：`POST /api/user/subscriptions/purchase`

**请求**：
```json
{
  "packageCode": "gold",
  "cycle": "MONTHLY",
  "couponId": null
}
```

**cycle枚举**：
- `MONTHLY` - 按月
- `QUARTERLY` - 按季
- `YEARLY` - 按年

**返回**：
```json
{
  "code": 200,
  "data": {
    "success": true,
    "orderId": "SUB_1234567890",
    "packageName": "黄金进阶会员",
    "cycle": "MONTHLY",
    "price": 30.00,
    "pointsGranted": 1800,
    "startTime": "2026-05-16 10:00:00",
    "expireTime": "2026-06-16 23:59:59"
  }
}
```

---

### 5. 点数流水查询

**接口**：`GET /api/user/points-bills?page=1&size=20`

**返回**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "changeType": "DAILY_GRANT",
        "pointsChange": 30.000,
        "balanceBefore": 1220.000,
        "balanceAfter": 1250.000,
        "description": "每日点数发放（限时，今日有效）",
        "createTime": "2026-05-16 00:00:00",
        "isTemporary": true
      },
      {
        "id": 2,
        "changeType": "SUBSCRIPTION_GRANT",
        "pointsChange": 1800.000,
        "balanceBefore": 0,
        "balanceAfter": 1800.000,
        "description": "购买黄金会员赠送点数（永久有效）",
        "createTime": "2026-05-15 10:00:00",
        "isTemporary": false
      }
    ],
    "total": 2
  }
}
```

---

## 🔐 权限双重校验

### 后端校验点

1. **对话接口** (`/api/chat/completions`)
   - 校验用户会员等级 >= 模型所需等级
   - 校验输入Token <= 套餐限制
   - 校验点数余额充足

2. **模型列表接口** (`GET /api/platform-model/available`)
   - 标记每个模型的 accessible 状态
   - 不可用模型返回 upgradeHint

3. **点数发放接口**
   - 仅定时任务可调用
   - 防止重复发放

---

## 📝 前端对接要点

### 1. 套餐展示

**必须显示**：
- 套餐名称和身份标识
- 三种周期价格（月/季/年）
- 开通赠送点数（永久）
- 每日点数（限时）
- Token限制（输入/输出）
- 可用模型列表

**优惠标签**：
- 季付显示"省10%"
- 年付显示"省30%"

---

### 2. 点数展示

**分开显示**：
```
总点数：1250
├─ 限时点数：30（今日有效）
└─ 永久点数：1220
```

**倒计时**：限时点数显示"今日23:59过期"

---

### 3. 模型选择

**可用模型**：正常显示，可选择

**不可用模型**：
- 灰显或加锁图标
- 显示"升级至XX会员解锁"
- 点击跳转套餐页面

---

### 4. 错误提示

**点数不足**：
```
"点数不足（当前：30），请充值或等待明日点数发放"
```

**模型权限不足**：
```
"当前会员等级无法使用该模型，请升级至黄金会员"
```

**Token超限**：
```
"请求内容过长，请缩短输入内容"
```

---

## 🔄 数据流转规则

### 点数发放流程

```
定时任务触发（每日00:00）
    ↓
PointsDistributionService.distributeDailyPoints()
    ↓
查询所有活跃用户
    ↓
遍历用户：发放30限时点数（addTemporaryPoints，is_temporary=1）
    ↓
检查用户是否有有效付费订阅
    ↓
├─ 无订阅 → 完成
└─ 有订阅 → 发放套餐 daily_points 永久点数（addPermanentPoints，is_temporary=0）
    ↓
记录点数流水
```

### 点数消费流程

```
用户发起对话
    ↓
预扣点数（优先扣限时点数）
    ↓
调用AI模型
    ↓
结算点数（固定点数）
    ↓
记录点数流水（change_type=CHAT_DEDUCT）
    ↓
保存对话消息
```

### 套餐到期流程

```
定时任务触发（每日00:10）
    ↓
StatisticsSchedulerConfig.checkSubscriptionExpiry()
    ↓
查询 status=ACTIVE 且 expireTime < now 的订阅
    ↓
标记为 EXPIRED
    ↓
自动降级为免费用户
    ↓
收回模型高级权限（accessLevel降为0）
```

---

## 📌 注意事项

1. **点数区分**：
   - 限时点数：每日发放，当日有效
   - 永久点数：开通赠送、充值获得

2. **消费顺序**：
   - 优先消耗限时点数
   - 再消耗永久点数

3. **权限校验**：
   - 后端必须校验，不依赖前端
   - 所有对话接口都要检查

4. **Token限制**：
   - 后台静默检查
   - 不告诉用户具体数值
   - 超限时给出通用提示

5. **模型权限**：
   - 前端显示可用/不可用
   - 后端双重校验
   - 不可用模型返回升级提示

---

## 🚀 部署步骤

1. **执行SQL脚本**：
   ```bash
   mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/rebuild.sql
   ```

2. **重启应用**：
   ```bash
   ./scripts/application/stop.sh
   ./scripts/application/start.sh
   ```

3. **验认定时任务**：
   - 查看日志确认每日点数发放任务已注册
   - 手动触发测试：调用 `DailyPointsService.manualDistributePoints()` 或 `PointsDistributionService.distributeDailyPoints()`

---

## 📄 文件解析流式接口（新增）

### 接口说明

**接口**：`POST /v1/ai-doc/chat/stream`

**Content-Type**：`multipart/form-data`

**请求参数**：
- `file` - 上传的文件（MultipartFile）
- `question` - 用户问题（String）

**SSE 事件流程**：

```javascript
const eventSource = new EventSource('/v1/ai-doc/chat/stream');

eventSource.addEventListener('status', (event) => {
  const data = JSON.parse(event.data);
  if (data.stage === 'parsing') {
    console.log('正在解析文件...');
    // 显示加载动画
  } else if (data.stage === 'parsed') {
    console.log('文件解析完成，正在与AI对话...');
    // 隐藏加载动画，准备接收AI回复
  }
});

eventSource.addEventListener('content', (event) => {
  const data = JSON.parse(event.data);
  console.log('AI回复:', data.text);
  // 渲染 AI 回复内容
});

eventSource.addEventListener('done', (event) => {
  console.log('对话完成');
  eventSource.close();
});

eventSource.addEventListener('error', (event) => {
  const data = JSON.parse(event.data);
  console.error('错误:', data.message);
  eventSource.close();
});
```

**事件类型**：

| 事件名 | 说明 | 数据格式 |
|--------|------|----------|
| `status` | 状态更新 | `{"stage": "parsing/parsed", "message": "..."}` |
| `content` | AI回复内容 | `{"text": "AI回复的完整内容"}` |
| `done` | 对话完成 | `{"message": "对话完成"}` |
| `error` | 错误信息 | `{"message": "错误描述"}` |

**前端处理逻辑**：

1. **用户上传文件后** → 调用 `/v1/ai-doc/chat/stream`
2. **收到 `parsing` 事件** → 显示“正在解析文件...”加载动画
3. **收到 `parsed` 事件** → 隐藏加载动画，显示“AI正在思考..."
4. **收到 `content` 事件** → 渲染 AI 回复内容（Markdown格式）
5. **收到 `done` 事件** → 关闭连接，显示完成状态
6. **收到 `error` 事件** → 显示错误提示，关闭连接

**优势**：
- ✅ 前端可以实时看到文件解析进度
- ✅ 等后端解析完毕后再发送对话请求
- ✅ 用户体验更好，不会长时间等待无响应
- ✅ 支持大文件解析的场景

---

**文档版本**：v2.0  
**最后更新**：2026-05-17  
**维护者**：后端开发团队
