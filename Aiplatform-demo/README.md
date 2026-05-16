# AI Gateway Platform — 凌点AI模型调度网关与流式对话平台

> 一个基于 Spring Boot 3.2.x 的高性能 AI 平台，采用**点数制 + 模型商店**双业务线架构。
> 当前**模型商店已搁置**，全平台运行在**点数制 AI 对话模式**（所有模型映射至 DeepSeek）。

---

## 目录

- [1. 项目概览](#1-项目概览)
- [2. 系统架构](#2-系统架构)
- [3. 技术栈](#3-技术栈)
- [4. 核心业务流程](#4-核心业务流程)
- [5. 数据库设计](#5-数据库设计)
- [6. Redis 缓存架构](#6-redis-缓存架构)
- [7. 计费系统详解](#7-计费系统详解)
- [8. 安全体系](#8-安全体系)
- [9. 模块与包结构](#9-模块与包结构)
- [10. API 接口总览](#10-api-接口总览)
- [11. 快速开始](#11-快速开始)
- [12. 部署指南](#12-部署指南)
- [13. 开发规范](#13-开发规范)
- [14. 已知问题与待办](#14-已知问题与待办)
- [15. 常见问题 FAQ](#15-常见问题-faq)

---

## 1. 项目概览

### 1.1 业务定位

凌点AI平台是一个**SaaS 级 AI 对话服务网关**，核心商业模式为：
- 用户充值购买套餐 → 获得点数 → 消耗点数进行 AI 对话
- 平台将所有虚拟模型（GPT-4 / Claude-3 等）统一路由至 DeepSeek API
- 管理员可通过后台进行用户管理、点数调整、套餐配置、数据统计

### 1.2 当前运行状态

| 模块 | 状态 | 说明 |
|------|------|------|
| AI 对话平台 | ✅ 运行中 | 点数制，全模型映射至 DeepSeek |
| 模型商店 | ⏸️ 搁置 | API Key 资源有限，暂不开放 |
| AI 文档助手 | ✅ 运行中 | 上传文件与 AI 交互 |
| 管理员后台 | ✅ 运行中 | 用户管理、统计、套餐管理 |
| 订阅系统 | ✅ 运行中 | 新版 `/user/subscriptions/*` |
| 旧订阅接口 | ⚠️ 已废弃 | 请迁移到新版接口 |

### 1.3 关键数字

- **98+** 个 API 接口（50+ 用户接口 + 48 管理员接口）
- **24** 张数据库表
- **5** 个 Redis Lua 脚本（计费、限流、结算等原子操作）
- **14** 个 Controller
- **30+** 个 Service
- **30 天** 冷热数据分界线（热数据 `chat_message`，冷数据 `chat_message_history`）

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端 (Web/App)                       │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP / SSE
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot 3.2.x                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              WebMvcConfig (CORS/拦截器注册)            │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌────────────┐  ┌────────────┐  ┌──────────────────────┐  │
│  │ 拦截器层    │  │ 注解切面层  │  │   全局异常处理        │  │
│  │            │  │            │  │                      │  │
│  │ IpRateLimit│  │AdminAuth   │  │ GlobalExceptionHandler│  │
│  │ UserRateLim│  │Aspect      │  │                      │  │
│  │ ApiKeyAuth │  │            │  │                      │  │
│  └─────┬──────┘  └─────┬──────┘  └──────────┬───────────┘  │
│        │               │                     │              │
│        ▼               ▼                     ▼              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  Controller 层                        │   │
│  │  Auth | Chat | User | Admin | Subscription | ...     │   │
│  └────────────────────────┬─────────────────────────────┘   │
│                           │                                  │
│                           ▼                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  Service 层                           │   │
│  │  OpenAiChatService | BillingService | PointsService  │   │
│  │  UserService | AdminService | SubscriptionService    │   │
│  └────────────────────────┬─────────────────────────────┘   │
│                           │                                  │
│              ┌────────────┴────────────┐                     │
│              ▼                         ▼                     │
│  ┌───────────────────┐    ┌───────────────────────────┐     │
│  │  MySQL 8.0        │    │  Redis 7.0 + Lua 脚本      │     │
│  │  (持久化存储)      │    │  (缓存/限流/计费/会话)     │     │
│  └───────────────────┘    └───────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │  DeepSeek API        │
              │  (第三方 AI 提供商)   │
              └──────────────────────┘
```

### 2.2 请求生命周期（以 AI 对话为例）

```
1. 客户端 POST /api/chat/completions
   Header: X-User-Token, X-Device-Id
   Body:   { model, messages }

2. UserRateLimitInterceptor
   → Redis 检查用户请求频率（令牌桶算法）
   → 超限则直接返回 429

3. Controller 层
   → 从 httpRequest.getAttribute("userId") 获取用户ID
   → 参数校验（model, messages 非空）

4. OpenAiChatService.chat()
   → PointsService.preDeductPoints()  预扣点数（Redis Lua 原子操作）
   → 调用 DeepSeek API（Hutool HTTP）
   → 计算实际 Token 消耗
   → PointsService.settlePoints()     结算扣费（Redis Lua 原子操作）
   → 写入 call_log 表
   → 返回 AI 响应

5. ChatMessageService.saveMessage()
   → 写入 chat_message 表（热数据）
   → 清除 Redis 缓存

6. 返回 JSON 响应给客户端
```

### 2.3 SSE 流式响应流程

```
1. 客户端 POST /api/chat/stream
   Accept: text/event-stream

2. Controller 创建 SseEmitter（超时 5 分钟）

3. 异步线程中：
   → 预扣点数
   → 调用 DeepSeek API（流式）
   → 逐块 forward 到 SseEmitter
   → 结算扣费
   → 保存消息

4. 客户端实时接收 data: 事件
```

---

## 3. 技术栈

### 3.1 后端

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 核心框架 | Spring Boot | 3.2.x | Web 服务、IoC |
| 语言 | Java | 17+ | 业务逻辑 |
| ORM | MyBatis-Plus | 3.5.7 | 数据库操作、分页 |
| 数据库 | MySQL | 8.0 | 持久化存储 |
| 连接池 | HikariCP | 内置 | 数据库连接管理 |
| 缓存 | Redis | 7.0+ | 缓存、限流、计费 |
| Redis 客户端 | Lettuce | 内置 | Redis 连接 |
| 脚本 | Lua | — | 原子性操作（计费/限流） |
| HTTP 客户端 | Hutool HTTP | 5.8.24 | 调用第三方 API |
| 安全 | Spring Security + BCrypt | — | 密码加密 |
| 认证 | JWT (自定义 Token) | — | 用户身份验证 |
| 流式 | SseEmitter | Spring MVC | SSE 推送 |
| 验证 | Jakarta Validation | — | 参数校验 |
| 工具 | Lombok | — | 减少样板代码 |
| 对象映射 | Jackson | 内置 | JSON 序列化 |
| 对象存储 | 阿里云 OSS | — | 文件存储 |
| 加密 | AES-256-GCM | — | API Key 加密存储 |

### 3.2 前端（待开发）

| 类别 | 技术 | 用途 |
|------|------|------|
| 框架 | Next.js 14 + React 18 | SSR/SSG |
| 语言 | TypeScript 5+ | 类型安全 |
| 样式 | Tailwind CSS v3 | 响应式布局 |
| 渲染引擎 | Pretext 极速流式渲染 | 120FPS 长文本渲染 |
| 状态管理 | Zustand + React Query | 全局状态/数据获取 |
| 路由 | React Router v6 | 页面导航 |
| 图表 | Recharts | 数据可视化 |
| HTTP | Axios | API 请求 |

### 3.3 基础设施

| 类别 | 技术 | 用途 |
|------|------|------|
| 容器化 | Docker + Docker Compose | 部署编排 |
| 构建工具 | Maven 3.9+ | 依赖管理、编译 |
| 代码规范 | Alibaba Java 规范 | 编码标准 |
| 日志 | SLF4J + Logback | 日志记录 |

---

## 4. 核心业务流程

### 4.1 用户注册与登录

```
注册流程：
1. POST /api/auth/register { username, password, email? }
2. 校验用户名唯一性
3. BCrypt 加密密码
4. 写入 user 表（初始余额 0，角色 USER）
5. 返回成功

登录流程：
1. POST /api/auth/login { username, password }
2. 查询用户，验证密码（BCrypt.matches）
3. 检查账号状态（是否封禁/注销）
4. 生成 Token（UUID + Redis 存储）
5. 写入 session 记录（设备ID绑定，防止异地登录）
6. 返回 Token + 用户信息
```

### 4.2 点数计费流程

```
三层架构：预扣 → 调用 → 结算

预扣阶段（对话前）：
1. 查询模型点数配置（platform_model_config 表）
2. Redis Lua 脚本原子性检查 + 预扣点数
   - Key: points:user:{userId}
   - 检查余额是否充足
   - 写入预扣记录 points:pre_deduct:{userId}:{requestId}
   - 余额不足则拒绝

调用阶段：
3. 调用 DeepSeek API
4. 解析响应，计算实际 Token 消耗

结算阶段（对话后）：
5. Redis Lua 脚本原子性结算
   - 删除预扣记录
   - 扣除实际点数
   - 写入 points_bill 表（账单记录）
   - 写入 call_log 表（调用日志）

异常处理：
- 如果调用失败，预扣记录在 5 分钟后自动过期回滚
- Lua 脚本保证原子性，不会出现并发竞态
```

### 4.3 冷热数据分离

```
热数据表：chat_message（30天内）
冷数据表：chat_message_history（30天前）

归档流程（每天凌晨2点）：
1. DataArchiveService.archiveOldMessages()
2. 查询 chat_message 中 create_time < 30天前的记录（每批1000条）
3. 转换为 ChatMessageHistory 对象
4. 批量插入 chat_message_history 表（每批500条，原生SQL批量插入）
5. 删除 chat_message 中已归档的记录
6. 循环直到无更多数据

查询流程（游标分页）：
1. 先查热数据表（chat_message）
2. 如果数据不足，补充查冷数据表（chat_message_history）
3. 合并结果，按时间倒序返回
4. Redis 缓存 30 分钟
```

### 4.4 订阅与套餐

```
套餐模板（package_template 表）：
- 套餐编码（如 pro_monthly）
- 名称、描述
- 价格、赠送点数
- 有效期（天）
- 状态（上架/下架）

购买流程：
1. GET /api/user/subscriptions/packages — 获取可用套餐列表
2. POST /api/user/subscriptions/purchase { packageCode, couponId? }
3. 校验套餐状态和价格
4. 检查用户余额是否充足
5. 扣除余额，创建订阅记录（user_subscription 表）
6. 发放套餐点数到用户账户
7. 如果有优惠券，标记已使用

订阅状态：
- 当前订阅信息：GET /api/user/subscriptions/current
- 订阅历史：GET /api/user/subscriptions/history
- 取消订阅：POST /api/user/subscriptions/cancel/{subscriptionId}
```

### 4.5 每日点数发放

```
定时任务（每天 00:00）：
1. StatisticsSchedulerConfig.distributeDailyPoints()
2. DailyPointsService.distributeDailyPoints()
3. 查询所有正常状态的用户
4. 为每个用户发放 30 点（当日有效）
5. Redis Lua 原子性增加点数
6. 写入 points_bill 表（change_type = DAILY）

防重领机制：
- points_bill 表唯一索引：uk_user_date_type (user_id, business_id, create_date)
- business_id 格式：daily_{date}_{userId}
- 同一天同一用户只能领取一次
```

---

## 5. 数据库设计

### 5.1 表清单（24张）

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `user` | 用户表 | id, username, password, balance, points, role, status |
| `api_key` | API Key 表 | id, user_id, key_value(加密), name, rate_limit, status |
| `platform_model_config` | 平台模型配置 | id, display_name, actual_model, points_cost, status |
| `chat_message` | 聊天消息（热） | id, conversation_id, user_id, role, content, model, tokens |
| `chat_message_history` | 聊天消息（冷） | id, conversation_id, user_id, role, content, original_create_time, archive_time |
| `conversation` | 会话表 | id, user_id, title, model, message_count |
| `call_log` | 调用日志 | id, user_id, model, tokens_used, cost, status, response_time |
| `billing_record` | 计费记录 | id, user_id, amount, type, description |
| `points_bill` | 点数账单 | id, user_id, points_change, change_type, balance_before, balance_after |
| `user_subscription` | 用户订阅 | id, user_id, package_code, start_time, end_time, status |
| `package_template` | 套餐模板 | id, code, name, price, points, duration_days, status |
| `coupon` | 优惠券 | id, code, name, points, total_count, used_count, expire_time |
| `coupon_usage_record` | 优惠券使用记录 | id, coupon_id, user_id, use_time |
| `system_notification` | 系统通知 | id, title, content, type, target_type, publish_time |
| `user_notification` | 用户通知关联 | id, user_id, notification_id, is_read, read_time |
| `admin_operation_log` | 管理员操作日志 | id, admin_id, operation, target_type, target_id, detail |
| `daily_statistics` | 每日统计 | id, stat_date, new_users, active_users, total_calls, revenue |
| `statistics_counter` | 统计计数器 | id, counter_type, counter_key, count, stat_date |
| `platform_statistics` | 平台统计 | id, total_users, total_calls, total_revenue, last_update |
| `marketplace_provider` | 模型商店供应商 | id, name, description, status |
| `user_marketplace_key` | 用户商店 Key | id, user_id, provider_id, key_value, status |
| `marketplace_usage_log` | 商店使用日志 | id, key_id, tokens_used, cost |
| `conversation_version` | 会话版本 | id, conversation_id, version, content_hash |
| `user_points_daily` | 每日点数记录 | id, user_id, date, points_granted, points_used |

### 5.2 核心表关系

```
user (1) ──→ (N) api_key
user (1) ──→ (N) conversation
user (1) ──→ (N) chat_message
user (1) ──→ (N) points_bill
user (1) ──→ (N) user_subscription
user (1) ──→ (N) user_notification

conversation (1) ──→ (N) chat_message
conversation (1) ──→ (N) chat_message_history

package_template (1) ──→ (N) user_subscription

coupon (1) ──→ (N) coupon_usage_record
user (1) ──→ (N) coupon_usage_record

system_notification (1) ──→ (N) user_notification
```

### 5.3 关键索引

| 表 | 索引名 | 字段 | 用途 |
|----|--------|------|------|
| user | uk_username | username | 唯一约束，登录查询 |
| user | uk_email | email | 唯一约束，邮箱登录 |
| points_bill | uk_user_date_type | user_id, business_id, create_date | 防重复发放 |
| user_subscription | idx_user_status | user_id, status | 查询当前订阅 |
| chat_message | idx_conv_user | conversation_id, user_id | 会话查询 |
| chat_message | idx_create_time | create_time | 冷热分离查询 |
| call_log | idx_user_time | user_id, create_time | 用户日志查询 |

---

## 6. Redis 缓存架构

### 6.1 Key 命名规范

```
用户相关：
  user:token:{token}              → 用户ID（Token 验证）
  user:session:{userId}:{deviceId} → 会话信息（设备绑定）
  user:balance:{userId}            → 余额（元）
  user:points:{userId}             → 点数余额
  user:rate:{userId}               → 请求频率计数

计费相关：
  points:pre_deduct:{userId}:{requestId} → 预扣记录（5分钟过期）
  points:bill:seq                        → 账单序号生成器

限流相关：
  rate:limit:{userId}:{minute}     → 令牌桶计数

缓存相关：
  chat:messages:{convId}:{lastId}:{limit} → 聊天记录缓存（30分钟）
  cache:user:{userId}              → 用户信息缓存（10分钟）
  cache:dashboard:{period}         → 仪表盘数据缓存

每日点数：
  daily:points:granted:{date}:{userId} → 每日点数领取标记
```

### 6.2 Lua 脚本清单

| 脚本 | 文件 | 功能 |
|------|------|------|
| 限流 | `lua/rate_limit.lua` | 令牌桶算法，检查并扣减请求配额 |
| 点数计费 | `lua/points_billing.lua` | 预扣点数，检查余额充足性 |
| 结算 | `lua/settlement.lua` | 结算实际消耗，删除预扣记录 |
| 免费限制 | `lua/free_limit_check.lua` | 免费额度检查与扣减 |
| 通用计费 | `lua/billing.lua` | 余额扣费（已禁用，全平台使用点数） |

### 6.3 Lua 原子性保证

所有涉及余额、点数、限流的操作都通过 Lua 脚本执行，确保：
- **原子性**：Redis 单线程执行，Lua 脚本不会被中断
- **一致性**：检查和扣减在同一事务中完成
- **防并发**：高并发下不会出现超扣或重复扣费

---

## 7. 计费系统详解

### 7.1 点数模型

```
点数来源：
1. 充值购买（套餐赠送）
2. 每日免费领取（30点/天）
3. 管理员手动发放
4. 优惠券兑换

点数消耗：
1. AI 对话（按模型配置，如 1次 = 1点）
2. AI 文档处理（按文件大小）
3. 文件生成（按复杂度）

点数规则：
- 每日点数当日有效，过期清零
- 充值点数永久有效
- 点数不足时拒绝请求
```

### 7.2 模型点数配置

| 模型显示名 | 实际模型 | 每次对话点数 | 说明 |
|-----------|---------|------------|------|
| GPT-4 | deepseek-v4-flash | 1 | 虚拟映射 |
| Claude-3 | deepseek-v4-flash | 1 | 虚拟映射 |
| Gemini Pro | deepseek-v4-flash | 1 | 虚拟映射 |
| DeepSeek V4 | deepseek-v4-flash | 1 | 直连 |

> 所有模型当前映射至 DeepSeek，后续可扩展至真实厂商 API。

### 7.3 计费时序图

```
客户端                Controller           PointsService         Redis(Lua)        DeepSeek
  │                      │                     │                    │                 │
  │── POST /chat ──────→│                     │                    │                 │
  │                      │── preDeductPoints ─→│                    │                 │
  │                      │                     │── Lua 预扣 ───────→│                 │
  │                      │                     │←─ 预扣结果 ────────│                 │
  │                      │←─ 余额不足则拒绝 ──│                     │                 │
  │                      │                     │                    │                 │
  │                      │                     │                    │── HTTP 请求 ───→│
  │                      │                     │                    │←─ 流式响应 ─────│
  │                      │                     │                    │                 │
  │                      │                     │── settlePoints ───→│                 │
  │                      │                     │── Lua 结算 ───────→│                 │
  │                      │                     │←─ 结算结果 ────────│                 │
  │←─ JSON 响应 ─────────│                     │                    │                 │
```

---

## 8. 安全体系

### 8.1 认证机制

```
Token 认证流程：
1. 用户登录 → 生成 UUID Token
2. Token 存入 Redis（key: user:token:{token}, value: userId, TTL: 7天）
3. 后续请求携带 X-User-Token Header
4. 拦截器从 Redis 验证 Token 有效性
5. 将 userId 存入 httpRequest.setAttribute("userId")

设备绑定：
- 登录时记录 deviceId（X-Device-Id Header）
- 同一账号在新设备登录 → 旧设备 Token 失效
- 防止账号共享和异地登录
```

### 8.2 限流机制

```
IP 限流：
- 每分钟最多 100 次请求
- 基于 IP 地址计数
- 超限返回 429 Too Many Requests

用户限流：
- 每个用户每分钟最多 60 次请求
- 基于 Redis 令牌桶
- 独立于 IP 限流
```

### 8.3 数据安全

```
密码安全：
- BCrypt 加密存储（cost factor = 10）
- 明文密码永不存储

API Key 加密：
- AES-256-GCM 加密存储
- 密钥通过环境变量配置

SQL 安全：
- 全部使用 MyBatis-Plus LambdaQueryWrapper
- 禁止字符串拼接 SQL
- LIMIT/OFFSET 使用 Page 对象

XSS 防护：
- 输入参数校验
- 输出内容转义
```

### 8.4 管理员权限

```
@RequireAdmin 注解：
- 标记需要管理员权限的接口
- AdminAuthAspect 切面拦截
- 检查用户 role 字段是否为 ADMIN
- 非管理员返回 403 Forbidden

管理员操作日志：
- 所有管理操作记录到 admin_operation_log 表
- 包含操作类型、目标、详情、时间
- 90 天后自动清理
```

---

## 9. 模块与包结构

```
src/main/java/com/ai/gateway/
├── AiGatewayApplication.java          # 启动类
├── annotation/
│   └── RequireAdmin.java              # 管理员权限注解
├── aspect/
│   └── AdminAuthAspect.java           # 管理员权限切面
├── common/
│   ├── Constants.java                 # Redis Key 常量
│   ├── Result.java                    # 统一响应封装
│   └── ResultCode.java                # 响应状态码
├── config/
│   ├── AsyncConfig.java               # 异步线程池配置
│   ├── BusinessMetricsCollector.java  # 业务指标收集
│   ├── CorsConfig.java                # 跨域配置
│   ├── DatabaseInitializer.java       # 数据库自动初始化
│   ├── JacksonConfig.java             # JSON 序列化配置
│   ├── MyMetaObjectHandler.java       # MyBatis 自动填充
│   ├── MybatisPlusConfig.java         # MyBatis-Plus 配置
│   ├── RedisConfig.java               # Redis 配置
│   ├── StatisticsSchedulerConfig.java # 定时任务配置
│   └── WebMvcConfig.java              # Web MVC 配置（拦截器注册）
├── controller/                        # 14 个控制器
│   ├── AdminController.java           # 管理员后台（48个接口）
│   ├── AiDocController.java           # AI 文档助手
│   ├── ApiKeyController.java          # API Key 管理
│   ├── AuthController.java            # 用户认证
│   ├── ChatController.java            # AI 对话（流式/非流式）
│   ├── ConversationController.java    # 会话管理
│   ├── CouponController.java          # 优惠券
│   ├── MarketplaceController.java     # 模型商店（搁置）
│   ├── NotificationController.java    # 通知
│   ├── PackageAdminController.java    # 套餐管理（管理员）
│   ├── PlatformModelController.java   # 平台模型配置
│   ├── TestDataController.java        # 测试数据
│   ├── UserController.java            # 用户中心
│   └── UserSubscriptionController.java # 用户订阅（新版）
├── dto/                               # 数据传输对象
│   ├── ChatRequest.java               # 对话请求
│   ├── UserLoginRequest.java          # 登录请求
│   ├── UserRegisterRequest.java       # 注册请求
│   └── Admin*Request.java             # 各类管理员请求
├── entity/                            # 24 个实体类
├── exception/
│   ├── BusinessException.java         # 业务异常
│   └── GlobalExceptionHandler.java    # 全局异常处理
├── interceptor/
│   ├── ApiKeyAuthInterceptor.java     # API Key 认证拦截器
│   ├── IpRateLimitInterceptor.java    # IP 限流拦截器
│   └── UserRateLimitInterceptor.java  # 用户限流拦截器
├── mapper/                            # 24 个 Mapper 接口
├── service/                           # 30+ 个 Service
│   ├── OpenAiChatService.java         # AI 对话核心服务
│   ├── PointsService.java             # 点数管理（Lua 操作）
│   ├── BillingService.java            # 计费服务（余额同步）
│   ├── UserService.java               # 用户业务逻辑
│   ├── AdminService.java              # 管理员业务逻辑
│   ├── ChatMessageService.java        # 消息管理（冷热分离）
│   ├── DataArchiveService.java        # 数据归档定时任务
│   ├── UserSubscriptionService.java   # 订阅管理
│   ├── DailyPointsService.java        # 每日点数发放
│   ├── FreeLimitService.java          # 免费额度管理
│   ├── RateLimitService.java          # 限流管理
│   ├── TokenService.java              # Token 管理
│   ├── SessionService.java            # 会话管理
│   ├── SseConnectionManager.java      # SSE 连接管理
│   ├── OssService.java                # 阿里云 OSS
│   ├── ChatFileService.java           # 文件处理
│   ├── AiDocService.java              # AI 文档解析
│   ├── CouponService.java             # 优惠券
│   ├── NotificationService.java       # 通知
│   ├── StatisticsService.java         # 统计分析
│   └── ...                            # 其他 Service
├── util/
│   ├── AesGcmUtil.java                # AES-256-GCM 加密
│   └── DataMaskUtil.java              # 数据脱敏
└── vo/                                # 视图对象
    ├── UserInfoVO.java                # 用户信息
    ├── LoginResponseVO.java           # 登录响应
    ├── DashboardStatsVO.java          # 仪表盘统计
    └── ...
```

---

## 10. API 接口总览

### 10.1 接口分类

| 分组 | 路径前缀 | 接口数 | 认证 | 说明 |
|------|---------|--------|------|------|
| 用户认证 | `/api/auth/*` | 4 | 无 | 注册、登录、退出、刷新Token |
| 用户中心 | `/api/user/*` | 15+ | X-User-Token | 余额、点数、充值、个人信息 |
| 平台模型 | `/api/platform-model/*` | 2 | X-User-Token | 获取可用模型列表 |
| AI 对话 | `/api/chat/*` | 7 | X-User-Token | 流式/非流式对话、文件对话、文件生成 |
| 会话管理 | `/api/conversation/*` | 4 | X-User-Token | 创建、查询、保存、清空会话 |
| AI 文档 | `/api/v1/ai-doc/*` | 2 | X-User-Token | 文档对话、结果导出 |
| 订阅管理 | `/api/user/subscriptions/*` | 5 | X-User-Token | 套餐购买、订阅状态 |
| 通知 | `/api/notification/*` | 3 | X-User-Token | 未读数、通知列表、标记已读 |
| 优惠券 | `/api/coupon/*` | 2 | X-User-Token | 兑换优惠券 |
| 管理员 | `/api/admin/*` | 48 | X-User-Token + ADMIN | 用户管理、统计、套餐、通知 |
| 模型商店 | `/api/marketplace/*` | 2 | X-User-Token | ⏸️ 搁置中 |

### 10.2 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证（Token 无效） |
| 403 | 权限不足 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

### 10.3 测试集合

- **用户接口**: [API_TEST.postman_collection.json](API_TEST.postman_collection.json)
- **管理员接口**: [ADMIN_API.postman_collection.json](ADMIN_API.postman_collection.json)

---

## 11. 快速开始

### 11.1 环境要求

| 软件 | 最低版本 | 推荐版本 |
|------|---------|---------|
| JDK | 17 | 21 (Corretto) |
| Maven | 3.9 | 3.9.6 |
| MySQL | 8.0 | 8.0.36 |
| Redis | 6.2 | 7.2 |

### 11.2 安装步骤

```bash
# 1. 克隆项目
git clone <repository-url>
cd Aiplatform-demo

# 2. 创建数据库
mysql -u root -p -e "CREATE DATABASE ai_gateway CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. 初始化表结构
mysql -u root -p ai_gateway < src/main/resources/sql/schema.sql

# 4. 初始化基础数据
mysql -u root -p ai_gateway < src/main/resources/sql/init_data.sql

# 5. 配置 application.yml
# 编辑 src/main/resources/application.yml，修改：
#   - spring.datasource.url / username / password
#   - spring.data.redis.host / port / password

# 6. 编译运行
mvn clean package -DskipTests
java -jar target/ai-gateway-1.0.0.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

### 11.3 验证安装

```bash
# 健康检查
curl http://localhost:8080/api/

# 注册测试用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 登录获取 Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 使用 Token 进行 AI 对话
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-User-Token: YOUR_TOKEN" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}]}'
```

### 11.4 Docker 部署

```bash
# 使用 Docker Compose 一键启动
cd docker
docker-compose up -d

# 查看日志
docker-compose logs -f app
```

---

## 12. 部署指南

### 12.1 生产环境配置清单

- [ ] 修改 `application.yml` 中的数据库连接信息
- [ ] 修改 Redis 连接信息（使用密码）
- [ ] 设置 `API_ENCRYPTION_KEY` 环境变量
- [ ] 设置 `ALLOWED_ORIGINS` 为前端域名
- [ ] 将日志级别调整为 `WARN`（`logging.level.root: WARN`）
- [ ] 配置 HTTPS（Nginx 反向代理）
- [ ] 开启 MySQL 慢查询日志
- [ ] 配置 Redis 持久化（RDB + AOF）
- [ ] 设置服务器防火墙（仅开放 80/443 端口）
- [ ] 配置定时备份数据库

### 12.2 Nginx 配置示例

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # SSE 支持
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}
```

---

## 13. 开发规范

### 13.1 编码规范

- **缩进**: 4 个空格（Java）/ 2 个空格（前端）
- **分号**: Java 必须使用分号
- **命名**: 驼峰命名法，类名大驼峰，方法/变量小驼峰
- **常量**: 全大写 + 下划线
- **注释**: 公共方法必须写 JavaDoc，复杂逻辑写行内注释

### 13.2 数据库操作规范

- **禁止**字符串拼接 SQL
- **必须**使用 MyBatis-Plus `LambdaQueryWrapper`
- **LIMIT/OFFSET** 必须使用 `Page` 对象，禁止 `.last("LIMIT " + ...)`
- **批量插入** 使用 `insertBatch`（原生 SQL `<foreach>`），禁止 `forEach(insert)`
- **事务** 涉及多表写操作的方法必须加 `@Transactional(rollbackFor = Exception.class)`

### 13.3 计费操作规范

- **必须**使用 Redis Lua 脚本进行点数/余额操作
- **禁止**在 Java 层直接读写 Redis 进行计费
- 采用"预扣 → 调用 → 结算"三层架构
- 预扣记录设置 TTL，超时自动回滚

### 13.4 API 设计规范

- 遵循 RESTful 风格
- 统一使用 `Result<T>` 响应格式
- 所有用户接口使用 `X-User-Token` 认证
- 平台对话**不需要** `X-API-Key`
- 参数校验使用 `@Valid` + Jakarta Validation

### 13.5 Git 提交规范

遵循 conventional commits 规范：

```
<type>(<scope>): <description>

feat(chat): 添加流式对话 SSE 支持
fix(billing): 修复点数结算并发问题
docs(readme): 更新部署文档
refactor(service): 重构计费逻辑
```

---

## 14. 已知问题与待办

### 14.1 已修复

| # | 问题 | 修复方式 | 状态 |
|---|------|---------|------|
| 1 | SQL 注入风险（`.last("LIMIT " + ...)`） | 改用 MyBatis-Plus `Page` 对象 | ✅ 已修复 |
| 2 | DataArchiveService N+1 插入 | 改用原生 SQL `<foreach>` 批量插入 | ✅ 已修复 |
| 3 | API_TEST Postman 重复订阅分组 | 删除重复的 "📦 订阅管理（新版）" 和 "💰 点数账单" 分组 | ✅ 已修复 |
| 4 | CacheService 死代码 | 已删除（功能被 StatisticsCacheService 替代） | ✅ 已修复 |
| 5 | MarketplaceChatController 死代码 | 已删除（模型商店搁置） | ✅ 已修复 |
| 6 | 3个未使用的 Entity 类 | 已删除（ContentSafetyStatistics, HotQuestionsStatistics, UserRetentionStatistics） | ✅ 已修复 |
| 7 | 冗余文档和脚本 | 已清理 18 个文件 | ✅ 已修复 |

### 14.2 无法修复 / 建议跳过

| # | 问题 | 原因 | 建议 |
|---|------|------|------|
| 1 | StatisticsScheduler 4 个空方法 | 需要新增数据库表和 Service 实现，工作量大且不影响当前功能 | 保持现状，日志记录已足够 |
| 2 | BillingService 未删除 | 被 UserService 的 `syncUserBalanceToRedis` 方法引用，不能删除 | 保持现状，已标记 `@Deprecated` 方法 |
| 3 | UserService.generateTitleAsync 中 `.last("LIMIT 1")` | 硬编码字面量 `1`，无用户输入，不构成注入风险 | 保持现状 |

### 14.3 未来规划

- [ ] 扩展真实厂商 API（OpenAI / Anthropic / Google）
- [ ] 开放模型商店功能
- [ ] 前端页面开发（Next.js + Pretext 渲染）
- [ ] 补全 StatisticsScheduler 定时统计逻辑
- [ ] 添加 WebSocket 支持（实时通知推送）
- [ ] 多语言支持（i18n）
- [ ] 监控告警（Prometheus + Grafana）

---

## 15. 常见问题 FAQ

### Q1: 为什么所有模型都映射到 DeepSeek？
A: 当前模型商店搁置，平台仅有 DeepSeek API Key。虚拟模型名（GPT-4、Claude-3 等）仅用于前端展示，后端统一路由至 DeepSeek。

### Q2: 点数不够怎么办？
A: 用户可以通过以下方式获取点数：
- 购买套餐（充值）
- 每日免费领取 30 点
- 兑换优惠券
- 联系管理员发放

### Q3: 如何成为管理员？
A: 需要直接在数据库中修改 `user` 表的 `role` 字段为 `ADMIN`。

### Q4: 聊天记录最多保存多久？
A: 热数据保存 30 天（`chat_message` 表），之后自动归档到冷数据表（`chat_message_history`），冷数据永久保存。

### Q5: 如何重置测试数据？
A: 调用 `POST /api/admin/test-data/reset` 接口（需要管理员权限）。

### Q6: SSE 流式响应超时怎么办？
A: SseEmitter 默认超时时间为 5 分钟。如果 AI 响应时间过长，客户端会收到超时错误。可以在 `ChatController` 中调整超时时间。

### Q7: 如何查看点数消耗明细？
A: 调用 `GET /api/user/points-bills?page=1&size=20` 可以查看点数账单列表。

### Q8: 旧版订阅接口还能用吗？
A: 旧版接口（`/api/user/subscription/*`）已标记 `@Deprecated`，仍然可用但建议迁移到新版接口（`/api/user/subscriptions/*`）。

---

**最后更新**: 2026年5月16日  
**项目版本**: v3.1  
**接口总数**: 98+ 个  
**数据库表**: 24 张  
**Redis Lua 脚本**: 5 个
