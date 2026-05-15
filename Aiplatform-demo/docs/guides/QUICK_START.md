# AI Gateway Platform - 快速启动指南

## 📋 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 5.7+ (端口 3307)
- Redis (端口 6380, 密码: redis123)

## 🚀 启动步骤

### 第一步：初始化数据库

```bash
# 连接到本地 MySQL
mysql -h localhost -P 3307 -u root -p123456

# 在 MySQL 命令行中执行：
CREATE DATABASE IF NOT EXISTS ai_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_gateway;
SOURCE $HOME/Documents/Aiplatform/Aiplatform-demo/src/main/resources/sql/schema.sql;

# 验证表是否创建成功
SHOW TABLES;

# 应该看到以下表：
# - user
# - api_key
# - model_config
# - call_log
# - billing_record

# 查看模型配置数据
SELECT model_name, provider, status FROM model_config;

# 退出 MySQL
EXIT;
```

### 第二步：编译项目

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo
mvn clean package -DskipTests
```

### 第三步：启动应用

**方式一：使用 start.sh 脚本（推荐）**
```bash
chmod +x start.sh
./start.sh
```

**方式二：直接运行 JAR**
```bash
java -jar target/ai-gateway-platform-1.0.0.jar
```

**方式三：使用 Maven 运行（开发模式）**
```bash
mvn spring-boot:run
```

### 第四步：验证服务启动

等待应用启动完成后，访问：
```bash
curl http://localhost:8080/api/actuator/health
```

应该返回：
```json
{"status":"UP"}
```

## 🧪 接口测试

### 1. 用户注册

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456",
    "email": "test@example.com"
  }'
```

### 2. 用户登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456"
  }'
```

会返回类似：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "testuser",
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

**保存返回的 token，后续请求需要使用！**

### 3. 获取用户信息

```bash
curl -X GET http://localhost:8080/api/auth/userinfo?userId=1 \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 4. 生成 API Key

```bash
curl -X POST http://localhost:8080/api/user/apikey/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "name": "测试API Key",
    "rateLimit": 100
  }'
```

会返回生成的 API Key，**请保存这个 API Key！**

### 5. 流式对话测试（使用 API Key）

```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY_HERE" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "你好，请介绍一下你自己"}
    ],
    "stream": true
  }'
```

### 6. 非流式对话测试

```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY_HERE" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "stream": false
  }'
```

### 7. 查看调用日志

```bash
curl -X GET http://localhost:8080/api/user/calllog?page=1&size=10 \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 8. 查看点数余额

```bash
curl -X GET http://localhost:8080/api/user/info \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## 📝 Postman 测试

项目根目录提供了 Postman 集合文件：
- `API_TEST.postman_collection.json` - 普通用户 API 测试
- `ADMIN_API.postman_collection.json` - 管理员 API 测试

导入到 Postman 后可以直接使用。

## 🔍 常见问题

### 1. 数据库连接失败
检查 MySQL 是否运行：
```bash
mysql -h localhost -P 3307 -u root -p123456 -e "SELECT 1;"
```

### 2. Redis 连接失败
检查 Redis 是否运行：
```bash
redis-cli -h localhost -p 6380 -a redis123 ping
```

### 3. 端口被占用
修改 `application.yml` 中的 `server.port`

### 4. 查看日志
```bash
tail -f logs/ai-gateway.log
```

## 🛑 停止应用

```bash
# 如果使用 start.sh 启动
./stop.sh

# 或者找到进程并杀死
ps aux | grep ai-gateway-platform
kill -9 <PID>
```

## 📊 数据库管理

### 查看所有用户
```sql
USE ai_gateway;
SELECT id, username, email, points, balance, status FROM user;
```

### 查看 API Keys
```sql
SELECT id, name, user_id, rate_limit, status FROM api_key;
```

### 查看模型配置
```sql
SELECT model_name, provider, input_price, output_price, points_mode, status FROM model_config;
```

### 重置测试数据（谨慎使用！）
```bash
# 删除并重新创建数据库
mysql -h localhost -P 3307 -u root -p123456 -e "DROP DATABASE IF EXISTS ai_gateway; CREATE DATABASE ai_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/schema.sql
```

## ✨ 提示

1. **首次使用建议先注册用户**，然后生成 API Key
2. **免费模型**（如 deepseek-chat）不消耗点数
3. **标准模型**每次消耗 1 点
4. **高级模型**每次消耗 3 点
5. 新用户默认有 100 点点数

祝你使用愉快！🎉
