# 快速参考指南

## 📁 项目结构速览

```
Aiplatform-demo/
├── src/main/java/com/ai/gateway/
│   ├── annotation/          # 自定义注解（RequireAdmin）
│   ├── aspect/              # AOP切面（AdminAuthAspect）
│   ├── common/              # 通用类（Result、ResultCode、Constants）
│   ├── config/              # 配置类（Redis、MyBatis-Plus、Web MVC、CORS、AsyncConfig、DatabaseInitializer）
│   ├── controller/          # 控制器（Auth、ApiKey、Chat、User、Admin）
│   ├── dto/                 # 请求DTO
│   ├── entity/              # 数据库实体（含AdminOperationLog、UserNotification、PlatformStatistics）
│   ├── exception/           # 异常处理
│   ├── interceptor/         # 拦截器（API Key鉴权、IP限流）
│   ├── mapper/              # MyBatis Mapper
│   ├── service/             # 业务服务（含AdminOperationLogService、NotificationService、StatisticsService）
│   └── vo/                  # 响应VO
├── src/main/resources/
│   ├── lua/                 # Redis Lua脚本
│   ├── sql/                 # 数据库脚本（含migration_admin_enhanced.sql）
│   └── application.yml      # 配置文件
├── API_TEST.postman_collection.json        # Postman测试（用户接口）
├── ADMIN_API.postman_collection.json       # Postman测试（管理员接口）⭐ 新增
├── ADMIN_API_DOCUMENTATION.md              # 管理员API文档 ⭐ 新增
├── ADMIN_BACKEND_COMPLETION_SUMMARY.md     # 管理员后台完成总结 ⭐ 新增
├── README.md                # 项目说明
├── DEPLOYMENT.md            # 部署指南
├── CHECKLIST.md             # 检查清单
├── PROJECT_SUMMARY.md       # 项目总结
├── QUICK_REFERENCE.md       # 快速参考
├── AI_DEVELOPMENT_TASKS.md  # 开发任务
├── start.sh                 # 启动脚本
└── stop.sh                  # 停止脚本
```

## 🔑 核心接口速查

> **认证方式**: 登录后获取 Token，后续请求通过 `X-User-Token` Header 传递。
> **聊天鉴权**: 需要 `X-API-Key` Header（从 `/api/api-key/generate` 获取）。

### 用户认证
```bash
# 注册
POST /api/auth/register
Body: { "username":"test", "password":"Abc@12345", "email":"test@test.com" }

# 登录（返回 token）
POST /api/auth/login
Body: { "username":"test", "password":"Abc@12345" }

# 查询用户信息
GET /api/auth/userinfo
Header: X-User-Token: {token}

# 查询余额
GET /api/auth/balance
Header: X-User-Token: {token}

# 刷新 Token
POST /api/auth/refresh-token
Header: X-User-Token: {token}

# 退出登录
POST /api/auth/logout
Header: X-User-Token: {token}
```

### API Key管理
```bash
# 生成API Key（返回明文 Key，仅此一次）
POST /api/api-key/generate
Header: X-User-Token: {token}
Body: { "name":"MyKey", "rateLimit":100 }

# 查询API Key列表（apiKey字段为null，安全隐藏）
GET /api/api-key/list
Header: X-User-Token: {token}

# 启用/禁用
PUT /api/api-key/{id}/enable
PUT /api/api-key/{id}/disable
Header: X-User-Token: {token}

# 更新限流
PUT /api/api-key/{id}/rate-limit?rateLimit={num}
Header: X-User-Token: {token}

# 删除
DELETE /api/api-key/{id}
Header: X-User-Token: {token}
```

### 聊天对话

**三种模式**:
- `free` - 免费模式（每日30次/50K tokens，无需余额）
- `custom` - 自定义模型（使用用户存储的第三方API Key）
- `paid` - 付费模式（按Token从余额扣费）

**可用免费模型**: deepseek-chat、deepseek-reasoner、deepseek-v4-pro、deepseek-v4-flash
（全部代理到 DeepSeek API，免费使用）

**超时设置**:
- 非流式对话: 30秒
- 流式对话: 120秒（支持更长对话）

```bash
# 免费模式 - 非流式对话
POST /api/chat/completions
Headers: X-API-Key: {apiKey}, X-User-Token: {token}
Body: { "model":"deepseek-v4-flash", "messages":[{"role":"user","content":"你好"}], "mode":"free" }

# 免费模式 - 流式对话（SSE）
POST /api/chat/stream
Headers: X-API-Key: {apiKey}, X-User-Token: {token}
Body: { "model":"deepseek-v4-flash", "messages":[{"role":"user","content":"你好"}], "stream":true, "mode":"free" }

# 自定义模型（使用存储的第三方Key）
POST /api/chat/completions
Headers: X-API-Key: {apiKey}, X-User-Token: {token}
Body: { "model":"deepseek-chat", "messages":[...], "mode":"custom", "userApiKeyId":1 }
```

### 模型配置
```bash
# 查看可用模型列表
GET /api/model-config/list
```

### 用户第三方API Key
```bash
# 添加第三方Key
POST /api/user/api-keys
Header: X-User-Token: {token}
Body: { "provider":"deepseek", "displayName":"我的DeepSeek", "apiKey":"sk-xxx", "baseUrl":"https://api.deepseek.com" }

# 查看列表
GET /api/user/api-keys
Header: X-User-Token: {token}

# 删除
DELETE /api/user/api-keys/{id}
Header: X-User-Token: {token}
```

### 管理员后台 ⭐ 新增
```bash
# 获取系统统计
GET /admin/stats
Header: X-User-Token: {adminToken}

# 分页查询用户（高级版）
GET /admin/users/advanced?page=1&size=20&membership=vip&minBalance=100&maxBalance=500
Header: X-User-Token: {adminToken}

# 批量封禁用户
POST /admin/batch/update-status
Header: X-User-Token: {adminToken}
Body: { "userIds":[1,2,3], "status":0 }

# 批量发放点数
POST /admin/batch/grant-points
Header: X-User-Token: {adminToken}
Body: { "userIds":[1,2,3], "points":100.000 }

# 手动增减点数
POST /admin/points/adjust
Header: X-User-Token: {adminToken}
Body: { "userId":1, "points":50.000, "reason":"活动奖励" }

# 调整会员等级
POST /admin/membership/update
Header: X-User-Token: {adminToken}
Body: { "userId":1, "membership":"vip" }

# 查询操作日志
GET /admin/logs?page=1&size=50
Header: X-User-Token: {adminToken}

# 按日查询统计
GET /admin/statistics/daily?startDate=2026-05-01&endDate=2026-05-14
Header: X-User-Token: {adminToken}

# 导出CSV报表
GET /admin/statistics/export?type=user&startDate=2026-05-01&endDate=2026-05-14
Header: X-User-Token: {adminToken}
```

## 💡 常用命令

### 编译运行
```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/ai-gateway-platform-1.0.0.jar

# 或使用脚本
./start.sh
./stop.sh
```

### 数据库操作
```bash
# 连接数据库
mysql -u root -p ai_gateway

# 导入数据
mysql -u root -p ai_gateway < src/main/resources/sql/schema.sql

# 备份
mysqldump -u root -p ai_gateway > backup.sql
```

### Redis操作
```bash
# 连接Redis
redis-cli -h YOUR_IP -a YOUR_PASSWORD

# 查看所有Key
KEYS *

# 查看限流Key
KEYS ai_gateway:rate_limit:*

# 查看余额Key
KEYS ai_gateway:user_balance:*

# 清除所有限流
EVAL "return redis.call('DEL', unpack(redis.call('KEYS', 'ai_gateway:rate_limit:*')))" 0
```

### 日志查看
```bash
# 实时查看日志
tail -f app.log

# 查看错误日志
grep ERROR app.log

# 查看最近100行
tail -n 100 app.log
```

## 🔧 配置修改位置

### 数据库配置
文件：`src/main/resources/application.yml`
```yaml
spring:
  datasource:
    url: jdbc:mysql://IP:3306/ai_gateway?...
    username: root
    password: YOUR_PASSWORD
```

### Redis配置
文件：`src/main/resources/application.yml`
```yaml
spring:
  data:
    redis:
      host: YOUR_IP
      port: 6379
      password: YOUR_PASSWORD
```

### 模型API Key
数据库表 `model_config` 中配置各模型的 api_key。
目前 DeepSeek 系列模型使用统一密钥。

### 服务器端口
文件：`src/main/resources/application.yml`
```yaml
server:
  port: 8080
  servlet:
    context-path: /api
```

## 📊 数据库表速查

### user - 用户表
- id, username, password(BCrypt), email, balance, status, role, free_api_strategy

### api_key - API Key表
- id, api_key(AES加密), user_id, name, status, rate_limit

### model_config - 模型配置表
- id, model_name, provider, base_url, api_key, input_price, output_price

### call_log - 调用日志表
- id, api_key, user_id, model, input_tokens, output_tokens, cost, duration, status

### billing_record - 计费记录表
- id, user_id, call_log_id, amount, type, balance_before, balance_after

## 🔍 问题排查速查

### 应用无法启动
```bash
# 检查端口
netstat -tuln | grep 8080

# 查看详细错误
java -jar target/ai-gateway-platform-1.0.0.jar 2>&1 | tail -50
```

### 数据库连接失败
```bash
# 测试连接
mysql -h YOUR_IP -u root -p
```

### Redis连接失败
```bash
# 测试连接
redis-cli -h YOUR_IP -a YOUR_PASSWORD ping
```

### 查看限流状态
```bash
redis-cli -h YOUR_IP -a YOUR_PASSWORD
> GET ai_gateway:rate_limit:{apiKey}
```

### 查看用户余额
```bash
redis-cli -h YOUR_IP -a YOUR_PASSWORD
> GET ai_gateway:user_balance:{userId}
```

### 查看免费额度使用情况
```bash
redis-cli -h YOUR_IP -a YOUR_PASSWORD
> GET free:calls:{userId}:{date}
> GET free:tokens:{userId}:{date}
```

### 查看线程池状态
```bash
# 在应用中添加监控端点或使用JMX工具
# 线程池名称: chat-async-
```

## 🚀 快速测试流程

```bash
# 1. 注册用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"Abc@12345","email":"test@test.com"}'

# 2. 登录获取Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"Abc@12345"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 3. 生成API Key
APIKEY=$(curl -s -X POST http://localhost:8080/api/api-key/generate \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -d '{"name":"Test Key","rateLimit":100}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['apiKey'])")

# 4. 免费模式对话测试
curl -s -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $APIKEY" \
  -H "X-User-Token: $TOKEN" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"mode":"free"}'

# 5. 流式对话测试
curl -s -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $APIKEY" \
  -H "X-User-Token: $TOKEN" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"stream":true,"mode":"free"}'
```

## 📝 扩展开发指南

### 添加新模型厂商

1. 在数据库添加配置
```sql
INSERT INTO model_config (model_name, provider, base_url, api_key, input_price, output_price)
VALUES ('claude-2', 'anthropic', 'https://api.anthropic.com/v1/messages', 'YOUR_KEY', 0.008, 0.024);
```

2. 创建Service类
```java
@Service
public class AnthropicChatService {
    // 实现对话逻辑
}
```

3. 在Controller中路由
```java
if (modelName.startsWith("claude")) {
        return anthropicChatService.chat(...);
}
```

### 自定义限流策略

修改：`src/main/resources/lua/rate_limit.lua`

### 调整计费策略

修改：`ModelConfigService.calculateCost()` 方法

## 🔗 相关链接

- Spring Boot文档: https://spring.io/projects/spring-boot
- MyBatis-Plus文档: https://baomidou.com/
- Redis文档: https://redis.io/documentation
- DeepSeek API文档: https://platform.deepseek.com/docs

## 📞 获取帮助

1. 查看完整文档: README.md
2. 查看部署指南: DEPLOYMENT.md
3. 查看检查清单: CHECKLIST.md
4. **查看优化总结: OPTIMIZATION_SUMMARY.md** ⭐ 新增
5. 查看应用日志: tail -f logs/ai-gateway.log

---

**提示**: 将此文件加入书签，方便快速查阅！

**最新更新**:
- ✅ 免费额度逻辑修复
- ✅ Token计算优化（更精确的估算）
- ✅ 管理员权限AOP统一处理
- ✅ 数据库查询性能优化（批量查询）
- ✅ SSE连接内存泄漏修复
- ✅ 专用线程池配置
- ✅ 日志级别优化
- ✅ 错误提示优化
