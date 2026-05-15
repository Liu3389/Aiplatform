# AI Gateway Platform - AI 模型调度网关与流式对话平台

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)

一个基于 Spring Boot 3.2.x 的高性能 AI 模型统一接入网关，支持多厂商模型路由、SSE 流式响应、Redis Lua 分布式限流和**点数计费系统**。

## 🚀 核心特性

- ✅ **多厂商模型接入** - 支持 OpenAI、DeepSeek 等主流 AI 模型
- ✅ **统一点数结算** - deepseek-v4-flash=1点/次，deepseek-v4-pro=3点/次
- ✅ **每日配额机制** - 普通用户每天自动获得30点（当日有效）
- ✅ **统一 API 接口** - 兼容 OpenAI 格式，无缝切换模型提供商
- ✅ **SSE 流式响应** - 基于 SseEmitter 实现实时逐字输出
- ✅ **分布式限流** - Redis Lua 脚本实现令牌桶算法，按 API Key 粒度限流
- ✅ **API Key 管理** - 细粒度权限控制、启用/禁用、限流阈值配置
- ✅ **调用日志追踪** - 完整的请求记录与状态追踪
- ✅ **全局异常处理** - 统一的错误响应格式

## 🛠️ 技术栈

### 后端
- **框架**: Spring Boot 3.2.x
- **ORM**: MyBatis-Plus 3.5.7
- **数据库**: MySQL 8.0
- **缓存**: Redis 6.2.6 (Lettuce 客户端)
- **HTTP 客户端**: Hutool HTTP
- **工具库**: Hutool 5.8.24, Lombok
- **验证**: Jakarta Validation

### 前端（待开发）
- **框架**: Next.js 14 + React 18 + TypeScript
- **样式**: Tailwind CSS
- **动画**: Framer Motion
- **状态管理**: Zustand
- **HTTP 客户端**: Axios

## 📋 前置要求

- JDK 21+ (Corretto 21.0.10 推荐)
- Maven 3.9+
- MySQL 8.0+
- Redis 6.2+

## 🔧 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/YOUR_USERNAME/ai-gateway-platform.git
cd ai-gateway-platform
```

### 2. 配置数据库和 Redis

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://YOUR_MYSQL_HOST:3306/ai_gateway?useSSL=false&serverTimezone=UTC
    username: YOUR_MYSQL_USERNAME
    password: YOUR_MYSQL_PASSWORD
  
  data:
    redis:
      host: YOUR_REDIS_HOST
      port: 6379
      password: YOUR_REDIS_PASSWORD
```

### 3. 初始化数据库

```bash
mysql -h YOUR_MYSQL_HOST -u YOUR_MYSQL_USERNAME -p < src/main/resources/sql/schema.sql
```

### 4. 编译并运行

```bash
mvn clean package
java -jar target/ai-gateway-1.0.0.jar
```

或者使用 Maven 直接运行：

```bash
mvn spring-boot:run
```

应用启动后访问：http://localhost:8080/api

## 📖 文档导航

### 🚀 快速开始
- **[docs/guides/QUICK_START.md](docs/guides/QUICK_START.md)** - 5分钟快速上手
- **[docs/guides/MAC_QUICK_START.md](docs/guides/MAC_QUICK_START.md)** - Mac用户专用指南
- **[docs/guides/QUICK_REFERENCE.md](docs/guides/QUICK_REFERENCE.md)** - 常用命令速查

### 🧪 测试指南
- **[docs/guides/COMPLETE_TESTING_GUIDE.md](docs/guides/COMPLETE_TESTING_GUIDE.md)** ⭐ - **完整接口测试教程**
- **[docs/guides/API_TESTING_COMPLETE_GUIDE.md](docs/guides/API_TESTING_COMPLETE_GUIDE.md)** - 详细测试文档
- **[docs/guides/ADMIN_PERMISSION_TEST_QUICKSTART.md](docs/guides/ADMIN_PERMISSION_TEST_QUICKSTART.md)** - 权限测试快速开始

### 🔧 API文档
- **[docs/api/ADMIN_API_DOCUMENTATION.md](docs/api/ADMIN_API_DOCUMENTATION.md)** - 管理员后台API文档（38个接口）
- **[API_TEST.postman_collection.json](API_TEST.postman_collection.json)** - 用户接口Postman集合（40+接口）
- **[ADMIN_API.postman_collection.json](ADMIN_API.postman_collection.json)** - 管理员接口Postman集合（38接口）

### 🗄️ 数据库
- **[docs/database/DATABASE_SCRIPTS.md](docs/database/DATABASE_SCRIPTS.md)** - 数据库脚本说明
- **[docs/database/STATISTICS_SYSTEM_GUIDE.md](docs/database/STATISTICS_SYSTEM_GUIDE.md)** - 统计系统指南
- **[src/main/resources/sql/optimization_database.sql](src/main/resources/sql/optimization_database.sql)** - 数据库优化脚本 ⭐

### 🚀 部署
- **[docs/guides/DEPLOYMENT.md](docs/guides/DEPLOYMENT.md)** - 生产环境部署指南
- **[deploy_optimization.sh](deploy_optimization.sh)** - 一键优化部署脚本 ⭐

### 📊 项目报告
- **[docs/reports/PROJECT_SUMMARY.md](docs/reports/PROJECT_SUMMARY.md)** - 项目总结和技术亮点
- **[docs/reports/CHAT_OPTIMIZATION_REPORT.md](docs/reports/CHAT_OPTIMIZATION_REPORT.md)** - 聊天优化报告
- **[SYSTEM_OPTIMIZATION_REPORT.md](SYSTEM_OPTIMIZATION_REPORT.md)** - 系统全面优化报告 ⭐

### 💡 功能指南
- **[DAILY_POINTS_SETUP.md](DAILY_POINTS_SETUP.md)** - 每日点数配置指南 ⭐
- **[POINTS_BASED_CHAT_GUIDE.md](POINTS_BASED_CHAT_GUIDE.md)** - 点数结算说明 ⭐
- **[DEEPSEEK_SETUP_GUIDE.md](DEEPSEEK_SETUP_GUIDE.md)** - DeepSeek API配置 ⭐
- **[REDIS_OPTIMIZATION.md](REDIS_OPTIMIZATION.md)** - Redis缓存优化 ⭐

---

## 📊 接口统计

- **用户接口**: 40+ 个（认证、API Key管理、聊天、会话、通知等）
- **管理员接口**: 38 个（用户管理、批量操作、点数管理、会员管理、统计分析等）
- **总计**: 86+ 个API接口
- **测试覆盖**: ✅ 100% (自动化测试)

---

## 📖 API 文档

### 用户管理

#### 注册
```bash
curl -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456",
    "email": "test@example.com"
  }'
```

#### 登录
```bash
curl -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456"
  }'
```

#### 充值
```bash
curl -X POST "http://localhost:8080/api/user/recharge?userId=1&amount=100"
```

#### 查询余额
```bash
curl http://localhost:8080/api/user/balance?userId=1
```

### API Key 管理

#### 生成 API Key
```bash
curl -X POST http://localhost:8080/api/key/generate \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My API Key",
    "rateLimit": 100
  }'
```

### 对话接口

#### 非流式对话
```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {
        "role": "user",
        "content": "你好，请介绍一下你自己"
      }
    ]
  }'
```

#### 流式对话（SSE）
```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {
        "role": "user",
        "content": "写一首关于春天的诗"
      }
    ],
    "stream": true
  }'
```

## 🏗️ 项目结构

```
ai-gateway-platform/
├── src/main/java/com/ai/gateway/
│   ├── common/              # 通用常量、结果封装
│   ├── config/              # 配置类（Redis、MyBatis等）
│   ├── controller/          # 控制器层
│   ├── dto/                 # 数据传输对象
│   ├── entity/              # 实体类
│   ├── exception/           # 异常处理
│   ├── interceptor/         # 拦截器（鉴权、限流、计费）
│   ├── mapper/              # MyBatis Mapper
│   └── service/             # 业务逻辑层
├── src/main/resources/
│   ├── lua/                 # Redis Lua 脚本
│   │   ├── billing.lua      # 计费脚本
│   │   ├── settlement.lua   # 结算脚本
│   │   └── rate_limit.lua   # 限流脚本
│   ├── sql/                 # 数据库脚本
│   └── application.yml      # 配置文件
└── pom.xml
---

## 🧪 快速测试

### 自动化测试（推荐）⭐

```bash
# 1. 初始化测试数据
./scripts/database/init_test_data.sh

# 2. 运行全量API测试（86个接口）
chmod +x test_all_apis.sh
./test_all_apis.sh

# 3. 运行权限测试
chmod +x test_admin_permission.sh
./test_admin_permission.sh
```

**预期结果**:
- 全量测试: 86/86 通过 (100%)
- 权限测试: 8/8 通过 (100%)

详见: [docs/guides/COMPLETE_TESTING_GUIDE.md](docs/guides/COMPLETE_TESTING_GUIDE.md)

---

## 🔐 核心架构

### 请求流程

```
客户端请求
    ↓
ApiKeyAuthInterceptor（拦截器）
    ├─ 1. 验证 API Key
    ├─ 2. 查询用户信息
    ├─ 3. 限流检查（Redis Lua）
    ├─ 4. 余额预扣（Redis Lua）
    └─ 5. 放行请求
    ↓
ChatController
    ↓
OpenAiChatService
    ├─ 调用第三方 AI API
    ├─ 计算实际费用
    └─ 结算余额（Redis Lua）
    ↓
返回响应
```

### 计费流程

```
1. 预扣阶段（请求前）
   - 估算最大费用（如 0.1 美元）
   - Redis Lua 原子性扣款
   - 记录预扣信息（用于回滚）

2. 结算阶段（请求后）
   - 计算实际 Token 费用
   - 退还差额（预扣 - 实际）
   - 删除预扣记录
```

## 📊 数据库表结构

- **user** - 用户表（余额、状态、角色）
- **api_key** - API Key 表（限流阈值、有效期）
- **model_config** - 模型配置表（价格、API 地址）
- **call_log** - 调用日志表（Token 数、费用、状态）
- **billing_record** - 计费记录表
- **admin_operation_log** - 管理员操作日志表
- **system_notification** - 系统通知表
- **user_notification** - 用户通知关联表
- **platform_statistics** - 平台统计表

---

**最后更新**: 2026年5月15日  
**文档整理**: 已整合为清晰的文档结构，删除冗余文件  
**接口总数**: 86+ 个（40+ 用户接口 + 38 管理员接口）  
**测试状态**: ✅ 100% 自动化测试覆盖

