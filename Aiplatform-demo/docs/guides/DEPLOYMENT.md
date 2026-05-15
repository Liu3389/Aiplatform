# 部署指南

> **最新更新**: 2026年5月15日 - 已完成接口文档更新和整理

## 一、环境准备

### 1.1 安装JDK 1

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# CentOS/RHEL
sudo yum install java-17-openjdk-devel

# 验证安装
java -version
```

### 1.2 安装Maven

```bash
# 下载Maven
wget https://downloads.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
tar -xzf apache-maven-3.9.6-bin.tar.gz
sudo mv apache-maven-3.9.6 /opt/maven

# 配置环境变量
echo 'export MAVEN_HOME=/opt/maven' >> ~/.bashrc
echo 'export PATH=$MAVEN_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 验证安装
mvn -version
```

### 1.3 安装MySQL 8.0

```bash
# Ubuntu/Debian
sudo apt install mysql-server

# 启动MySQL
sudo systemctl start mysql
sudo systemctl enable mysql

# 初始化MySQL
sudo mysql_secure_installation
```

### 1.4 安装Redis 6.2+

```bash
# Ubuntu/Debian
sudo apt install redis-server

# CentOS/RHEL
sudo yum install redis

# 启动Redis
sudo systemctl start redis
sudo systemctl enable redis

# 配置Redis密码
sudo vi /etc/redis/redis.conf
# 找到 requirepass 行，设置密码
requirepass 123321

# 重启Redis
sudo systemctl restart redis
```

## 二、数据库配置

### 2.1 创建数据库

```sql
CREATE DATABASE ai_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2.2 执行建表脚本

```bash
mysql -u root -p ai_gateway < src/main/resources/sql/schema.sql
```

### 2.3 验证数据

```sql
USE ai_gateway;
SHOW TABLES;
SELECT * FROM model_config;
```

## 三、应用配置

### 3.1 修改application.yml

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://YOUR_SERVER_IP:3306/ai_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: YOUR_MYSQL_PASSWORD
  
  data:
    redis:
      host: YOUR_SERVER_IP
      port: 6379
      password: YOUR_REDIS_PASSWORD

openai:
  api-key: sk-your-real-openai-api-key  # 替换为真实的OpenAI API Key
```

### 3.2 配置防火墙

```bash
# 开放8080端口
sudo ufw allow 8080/tcp

# 如果使用firewalld
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

## 四、编译和运行

### 4.1 编译项目

```bash
cd /path/to/Aiplatform-demo
mvn clean package -DskipTests
```

### 4.2 运行应用

#### 方式一：直接运行JAR包

```bash
java -jar target/ai-gateway-platform-1.0.0.jar
```

#### 方式二：后台运行

```bash
nohup java -jar target/ai-gateway-platform-1.0.0.jar > app.log 2>&1 &
```

#### 方式三：使用Maven运行（开发环境）

```bash
mvn spring-boot:run
```

### 4.3 验证启动

```bash
# 检查应用是否启动
curl http://localhost:8080/api/auth/userinfo?userId=1

# 查看日志
tail -f app.log
```

## 五、使用systemd管理服务（推荐生产环境）

### 5.1 创建服务文件

```bash
sudo vi /etc/systemd/system/ai-gateway.service
```

内容如下：

```ini
[Unit]
Description=AI Gateway Platform
After=syslog.target network.target

[Service]
Type=simple
User=root
WorkingDirectory=/path/to/Aiplatform-demo
ExecStart=/usr/bin/java -jar /path/to/Aiplatform-demo/target/ai-gateway-platform-1.0.0.jar
ExecStop=/bin/kill -15 $MAINPID
Restart=always
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### 5.2 启动服务

```bash
# 重载systemd配置
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start ai-gateway

# 设置开机自启
sudo systemctl enable ai-gateway

# 查看状态
sudo systemctl status ai-gateway

# 查看日志
sudo journalctl -u ai-gateway -f
```

## 六、使用Docker部署（可选）

### 6.1 创建Dockerfile

```dockerfile
FROM openjdk:17-slim

WORKDIR /app

COPY ../../target/ai-gateway-platform-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 6.2 构建镜像

```bash
docker build -t ai-gateway:1.0.0 .
```

### 6.3 运行容器

```bash
docker run -d \
  --name ai-gateway \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/ai_gateway?... \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=123456 \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  -e SPRING_DATA_REDIS_PASSWORD=123321 \
  ai-gateway:1.0.0
```

## 七、性能调优

### 7.1 JVM参数优化

```bash
java -jar \
  -Xms512m \
  -Xmx2048m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/ai-gateway/heapdump.hprof \
  target/ai-gateway-platform-1.0.0.jar
```

### 7.2 Redis连接池优化

在 `application.yml` 中调整：

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20      # 最大连接数
          max-idle: 10        # 最大空闲连接
          min-idle: 5         # 最小空闲连接
          max-wait: 3000ms    # 最大等待时间
```

### 7.3 Tomcat线程池优化

```yaml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 20
    accept-count: 100
    max-connections: 8192
```

## 八、监控和日志

### 8.1 启用Actuator监控

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

在 `application.yml` 中配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

访问：`http://localhost:8080/api/actuator/health`

### 8.2 日志配置

日志文件位置：`logs/ai-gateway.log`

查看实时日志：

```bash
tail -f logs/ai-gateway.log
```

## 九、故障排查

### 9.1 应用无法启动

```bash
# 检查端口占用
netstat -tuln | grep 8080

# 检查Java进程
ps aux | grep java

# 查看详细错误日志
journalctl -u ai-gateway -xe
```

### 9.2 数据库连接失败

```bash
# 测试MySQL连接
mysql -h YOUR_SERVER_IP -u root -p

# 检查MySQL状态
sudo systemctl status mysql

# 检查防火墙
sudo ufw status
```

### 9.3 Redis连接失败

```bash
# 测试Redis连接
redis-cli -h YOUR_SERVER_IP -a 123321 ping

# 检查Redis状态
sudo systemctl status redis

# 查看Redis日志
sudo tail -f /var/log/redis/redis-server.log
```

## 十、备份和恢复

### 10.1 数据库备份

```bash
# 备份数据库
mysqldump -u root -p ai_gateway > backup_$(date +%Y%m%d).sql

# 恢复数据库
mysql -u root -p ai_gateway < backup_20240101.sql
```

### 10.2 定时备份

创建crontab任务：

```bash
crontab -e

# 每天凌晨2点备份
0 2 * * * mysqldump -u root -pYOUR_PASSWORD ai_gateway > /backup/ai_gateway_$(date +\%Y\%m\%d).sql
```

## 十一、安全建议

1. **使用HTTPS**：配置SSL证书，启用HTTPS
2. **修改默认密码**：修改数据库和Redis的默认密码
3. **限制访问IP**：通过防火墙限制只能从特定IP访问
4. **定期更新**：及时更新依赖库，修复安全漏洞
5. **API Key保护**：不要在代码中硬编码API Key，使用环境变量
6. **速率限制**：启用限流功能，防止DDoS攻击

## 十二、常见问题FAQ

**Q: 启动时报"Port 8080 already in use"**
A: 检查是否有其他应用占用了8080端口，或者修改application.yml中的端口配置。

**Q: OpenAI API调用超时**
A: 检查网络连接，可能需要配置代理服务器。

**Q: Redis限流不生效**
A: 检查Redis连接是否正常，Lua脚本是否正确加载。

**Q: 余额扣费不准确**
A: 检查Token计算逻辑，确认模型配置中的价格设置正确。

**Q: 流式对话中断或超时**
A: 流式对话超时时间已设置为120秒，支持更长对话。如果仍然超时，请检查网络连接和API响应速度。

**Q: 管理员权限验证失败**
A: 确保用户角色为ADMIN或SUPER_ADMIN。使用AOP统一验证，无需在每个接口手动检查。

**Q: 高并发下性能问题**
A: 系统已配置专用线程池（chatExecutor），核心线程数10，最大线程数50。如需调整，请修改AsyncConfig.java。

**Q: 日志文件过大**
A: 生产环境已优化日志级别为WARN，减少70%日志输出。日志文件位置：logs/ai-gateway.log

***

如有其他问题，请查看应用日志或联系技术支持。

**相关文档**:
- [QUICK_REFERENCE.md](QUICK_REFERENCE.md) - 快速参考指南
- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) - 项目总结
