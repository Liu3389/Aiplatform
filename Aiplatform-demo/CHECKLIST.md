# 项目启动检查清单

## 📋 启动前检查

### 1. 环境检查
- [ ] JDK 17+ 已安装
  ```bash
  java -version
  ```

- [ ] Maven 3.6+ 已安装
  ```bash
  mvn -version
  ```

- [ ] MySQL 8.0+ 已安装并运行
  ```bash
  mysql --version
  sudo systemctl status mysql
  ```

- [ ] Redis 6.2+ 已安装并运行
  ```bash
  redis-server --version
  sudo systemctl status redis
  ```

### 2. 数据库配置
- [ ] 创建数据库 `ai_gateway`
  ```sql
  CREATE DATABASE ai_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

- [ ] 执行建表脚本
  ```bash
  mysql -u root -p ai_gateway < src/main/resources/sql/schema.sql
  ```

- [ ] 验证表是否创建成功
  ```sql
  USE ai_gateway;
  SHOW TABLES;
  -- 应该看到: user, api_key, model_config, call_log, billing_record
  ```

- [ ] 验证初始数据
  ```sql
  SELECT * FROM model_config;
  -- 应该看到 gpt-3.5-turbo 和 gpt-4o 两条记录
  ```

### 3. 配置文件修改
- [ ] 编辑 `src/main/resources/application.yml`

- [ ] 修改MySQL连接配置
  ```yaml
  spring:
    datasource:
      url: jdbc:mysql://YOUR_IP:3306/ai_gateway?...
      username: root
      password: YOUR_PASSWORD
  ```

- [ ] 修改Redis连接配置
  ```yaml
  spring:
    data:
      redis:
        host: YOUR_IP
        port: 6379
        password: YOUR_PASSWORD
  ```

- [ ] 配置OpenAI API Key
  ```yaml
  openai:
    api-key: sk-your-real-openai-api-key
  ```

### 4. 网络检查
- [ ] MySQL可访问
  ```bash
  mysql -h YOUR_IP -u root -p
  ```

- [ ] Redis可访问
  ```bash
  redis-cli -h YOUR_IP -a YOUR_PASSWORD ping
  # 应返回: PONG
  ```

- [ ] 端口8080未被占用
  ```bash
  netstat -tuln | grep 8080
  ```

- [ ] OpenAI API可访问（可能需要代理）
  ```bash
  curl https://api.openai.com/v1/models \
    -H "Authorization: Bearer YOUR_API_KEY"
  ```

## 🚀 启动应用

### 方式一：使用启动脚本（推荐）
```bash
chmod +x start.sh
./start.sh
```

### 方式二：使用Maven
```bash
mvn spring-boot:run
```

### 方式三：打包后运行
```bash
mvn clean package -DskipTests
java -jar target/ai-gateway-platform-1.0.0.jar
```

## ✅ 启动验证

### 1. 检查应用日志
```bash
tail -f app.log
```
应该看到类似输出：
```
========================================
AI Gateway Platform 启动成功！
访问地址: http://localhost:8080/api
========================================
```

### 2. 测试健康检查
```bash
curl http://localhost:8080/api/auth/userinfo?userId=1
```

### 3. 测试接口

#### 注册用户
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456",
    "email": "test@example.com"
  }'
```

#### 用户登录
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456"
  }'
```

#### 生成API Key
```bash
curl -X POST "http://localhost:8080/api/api-key/generate?userId=1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test API Key",
    "rateLimit": 100
  }'
```

#### 非流式对话
```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "user",
        "content": "你好"
      }
    ]
  }'
```

#### 流式对话
```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "user",
        "content": "写一首诗"
      }
    ],
    "stream": true
  }'
```

## 🔍 问题排查

### 问题1：应用无法启动
**检查项：**
- [ ] Java版本是否为17+
- [ ] 端口8080是否被占用
- [ ] 配置文件是否正确

**解决方案：**
```bash
# 查看端口占用
netstat -tuln | grep 8080

# 查看详细错误
java -jar target/ai-gateway-platform-1.0.0.jar 2>&1 | tail -50
```

### 问题2：数据库连接失败
**检查项：**
- [ ] MySQL服务是否运行
- [ ] 数据库是否存在
- [ ] 用户名密码是否正确
- [ ] 防火墙是否开放3306端口

**解决方案：**
```bash
# 测试连接
mysql -h YOUR_IP -u root -p

# 检查MySQL状态
sudo systemctl status mysql
```

### 问题3：Redis连接失败
**检查项：**
- [ ] Redis服务是否运行
- [ ] 密码是否正确
- [ ] 防火墙是否开放6379端口

**解决方案：**
```bash
# 测试连接
redis-cli -h YOUR_IP -a YOUR_PASSWORD ping

# 检查Redis状态
sudo systemctl status redis
```

### 问题4：OpenAI API调用失败
**检查项：**
- [ ] API Key是否正确
- [ ] 是否有足够配额
- [ ] 网络是否可达

**解决方案：**
```bash
# 测试API
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer YOUR_API_KEY"

# 如果需要代理，添加环境变量
export http_proxy=http://proxy_ip:port
export https_proxy=http://proxy_ip:port
```

### 问题5：限流不生效
**检查项：**
- [ ] Redis是否正常
- [ ] Lua脚本是否加载
- [ ] 查看应用日志

**解决方案：**
```bash
# 检查Redis中的限流Key
redis-cli -h YOUR_IP -a YOUR_PASSWORD
> KEYS ai_gateway:rate_limit:*
```

### 问题6：余额扣费异常
**检查项：**
- [ ] Redis余额是否正确
- [ ] 模型配置价格是否设置
- [ ] 查看计费日志

**解决方案：**
```bash
# 检查Redis中的余额
redis-cli -h YOUR_IP -a YOUR_PASSWORD
> GET ai_gateway:user_balance:1

# 检查数据库中的余额
mysql -u root -p ai_gateway -e "SELECT * FROM user WHERE id=1;"
```

## 📊 性能检查

### 1. 检查JVM内存
```bash
jstat -gc PID 1000
```

### 2. 检查Redis性能
```bash
redis-cli -h YOUR_IP -a YOUR_PASSWORD info stats
```

### 3. 检查MySQL慢查询
```sql
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
```

## 🔐 安全检查

- [ ] 修改默认密码
- [ ] 启用HTTPS（生产环境）
- [ ] 配置防火墙规则
- [ ] 不要在代码中硬编码敏感信息
- [ ] 定期更新依赖包

## 📝 其他检查

- [ ] 日志文件是否正常生成
- [ ] 定时备份数据库
- [ ] 监控应用状态
- [ ] 设置告警规则

## ✨ 完成

如果以上所有检查项都通过，恭喜！你的AI Gateway Platform已经成功部署并运行。

接下来可以：
1. 导入Postman测试集合进行完整测试
2. 阅读README.md了解详细API文档
3. 根据业务需求进行定制开发

---

**需要帮助？**
- 查看 README.md
- 查看 DEPLOYMENT.md
- 查看应用日志: tail -f app.log
