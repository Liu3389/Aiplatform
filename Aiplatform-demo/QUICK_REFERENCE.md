# 快速参考指南

## 📁 项目结构速览

```
Aiplatform-demo/
├── src/main/java/com/ai/gateway/
│   ├── common/              # 通用类（Result、ResultCode、Constants）
│   ├── config/              # 配置类（Redis、MyBatis-Plus、Web MVC）
│   ├── controller/          # 控制器（Auth、ApiKey、Chat）
│   ├── dto/                 # 请求DTO
│   ├── entity/              # 数据库实体
│   ├── exception/           # 异常处理
│   ├── interceptor/         # 拦截器（API Key鉴权）
│   ├── mapper/              # MyBatis Mapper
│   ├── service/             # 业务服务
│   └── vo/                  # 响应VO
├── src/main/resources/
│   ├── lua/                 # Redis Lua脚本
│   ├── sql/                 # 数据库脚本
│   └── application.yml      # 配置文件
├── API_TEST.postman_collection.json  # Postman测试
├── README.md                # 项目说明
├── DEPLOYMENT.md            # 部署指南
├── CHECKLIST.md             # 检查清单
├── start.sh                 # 启动脚本
└── stop.sh                  # 停止脚本
```

## 🔑 核心接口速查

### 用户认证
```bash
# 注册
POST /api/auth/register

# 登录
POST /api/auth/login

# 查询用户信息
GET /api/auth/userinfo?userId={id}

# 查询余额
GET /api/auth/balance?userId={id}
```

### API Key管理
```bash
# 生成API Key
POST /api/api-key/generate?userId={id}

# 查询列表
GET /api/api-key/list?userId={id}

# 启用/禁用
PUT /api/api-key/{id}/enable?userId={id}
PUT /api/api-key/{id}/disable?userId={id}

# 更新限流
PUT /api/api-key/{id}/rate-limit?userId={id}&rateLimit={num}

# 删除
DELETE /api/api-key/{id}?userId={id}
```

### 聊天对话
```bash
# 非流式对话
POST /api/chat/completions
Headers: X-API-Key: {your_api_key}

# 流式对话（SSE）
POST /api/chat/stream
Headers: X-API-Key: {your_api_key}
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

### OpenAI API Key
文件：`src/main/resources/application.yml`
```yaml
openai:
  api-key: sk-your-api-key
```

### 服务器端口
文件：`src/main/resources/application.yml`
```yaml
server:
  port: 8080
```

## 📊 数据库表速查

### user - 用户表
- id, username, password, email, balance, status

### api_key - API Key表
- id, api_key, user_id, name, status, rate_limit

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

# 检查服务
sudo systemctl status mysql
```

### Redis连接失败
```bash
# 测试连接
redis-cli -h YOUR_IP -a YOUR_PASSWORD ping

# 检查服务
sudo systemctl status redis
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

## 🚀 快速测试流程

```bash
# 1. 注册用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"test@test.com"}'

# 2. 登录获取userId
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 3. 生成API Key（假设userId=1）
curl -X POST "http://localhost:8080/api/api-key/generate?userId=1" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Key","rateLimit":100}'

# 4. 复制返回的apiKey，进行对话测试
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-xxxxxxxxx" \
  -d '{"model":"gpt-3.5-turbo","messages":[{"role":"user","content":"你好"}]}'
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
- OpenAI API文档: https://platform.openai.com/docs

## 📞 获取帮助

1. 查看完整文档: README.md
2. 查看部署指南: DEPLOYMENT.md
3. 查看检查清单: CHECKLIST.md
4. 查看应用日志: tail -f app.log

---

**提示**: 将此文件加入书签，方便快速查阅！
