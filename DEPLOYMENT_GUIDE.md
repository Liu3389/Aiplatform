# 凌点 AI 网关 - 服务器部署指南（Docker 版）

> 本文档用于指导如何将最新版本的前后端项目部署到远程服务器。

***

## 一、服务器信息

| 项目     | 值                                         |
| ------ | ----------------------------------------- |
| 公网 IP  | `YOUR_SERVER_IP`                             |
| 域名     | `lingdian-ai.top` / `www.lingdian-ai.top` |
| SSH 密钥 | `$HOME/Desktop/fortest.pem`        |
| 部署目录   | `/opt/Aiplatform/`                        |
| 前端静态目录 | `/usr/share/nginx/html/`                  |

***

## 二、服务器环境要求

### 已安装的环境

- **Java 21** (OpenJDK)
- **Maven 3.x**
- **Node.js 18+** / npm
- **Docker** + Docker Compose
- **MySQL 8.0** (Docker容器: `lingdian-mysql`)
- **Redis 7** (Docker容器: `lingdian-redis`)
- **Nginx**

### 验证环境

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP

# 验证Java版本
java -version  # 应为 21.x

# 验证Docker容器
docker ps  # 应看到 lingdian-mysql 和 lingdian-redis

# 验证Nginx
nginx -v
systemctl status nginx
```

***

## 三、本地构建

### 3.1 前端构建

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-web
npx vite build
```

构建产物输出到 `dist/` 目录。

### 3.2 后端构建

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo
mvn clean package -DskipTests
```

构建产物输出到 `target/ai-gateway-platform-1.0.0.jar`。

***

## 四、部署步骤

### 4.1 停止旧服务

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP

# 停止后端服务（systemd管理）
systemctl stop lingdian-backend.service

# 停止Nginx
systemctl stop nginx

# 清理残留Java进程
ps aux | grep java | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null

# 确认8080端口已释放
lsof -i:8080 || echo "端口8080已释放"
```

### 4.2 上传新文件

```bash
# 从本地执行

# 上传后端jar包
scp -i $HOME/Desktop/fortest.pem \
  $HOME/Documents/Aiplatform/Aiplatform-demo/target/ai-gateway-platform-1.0.0.jar \
  root@YOUR_SERVER_IP:/opt/Aiplatform/

# 上传前端构建文件
scp -i $HOME/Desktop/fortest.pem -r \
  $HOME/Documents/Aiplatform/Aiplatform-web/dist/* \
  root@YOUR_SERVER_IP:/usr/share/nginx/html/

# 上传数据库脚本（如需重建）
scp -i $HOME/Desktop/fortest.pem \
  $HOME/Documents/Aiplatform/Aiplatform-demo/src/main/resources/sql/rebuild.sql \
  root@YOUR_SERVER_IP:/opt/Aiplatform/
```

### 4.3 重建数据库（可选）

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP

# 执行rebuild.sql（幂等，可重复执行）
docker exec -i lingdian-mysql mysql -uroot -proot123 ai_gateway < /opt/Aiplatform/rebuild.sql

# 验证数据
docker exec lingdian-mysql mysql -uroot -proot123 ai_gateway -e "
  SELECT COUNT(*) AS user_count FROM user;
  SELECT COUNT(*) AS model_count FROM platform_model_config;
  SELECT COUNT(*) AS package_count FROM package_template;
"
```

### 4.4 配置Nginx

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP

# 从本地上传nginx配置
scp -i $HOME/Desktop/fortest.pem \
  $HOME/Documents/Aiplatform/Aiplatform-web/docker/nginx-server.conf \
  root@YOUR_SERVER_IP:/opt/Aiplatform/

# 在服务器上配置
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP '
  cat /opt/Aiplatform/nginx-server.conf | \
    sed "s/server_name _;/server_name lingdian-ai.top www.lingdian-ai.top;/" \
    > /etc/nginx/conf.d/lingdian.conf
  nginx -t
'
```

### 4.5 启动服务

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP

# 启动后端（systemd服务，自动重启）
systemctl restart lingdian-backend.service

# 启动Nginx
systemctl restart nginx

# 等待30秒让后端完全启动
sleep 30
```

***

## 五、验证部署

### 5.1 后端验证

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP

# 检查服务状态
systemctl status lingdian-backend.service

# 测试API
curl -s http://localhost:8080/api/platform-model/list | head -200

# 查看日志
journalctl -u lingdian-backend.service -n 50 --no-pager
tail -50 /opt/Aiplatform/logs/backend.log
```

### 5.2 前端验证

```bash
# 本地浏览器访问
http://lingdian-ai.top

# 或命令行验证
curl -s -o /dev/null -w "HTTP: %{http_code}\n" http://lingdian-ai.top/
curl -s -o /dev/null -w "HTTP: %{http_code}\n" http://lingdian-ai.top/api/platform-model/list
```

### 5.3 DNS验证

```bash
dig +short lingdian-ai.top
# 应返回: YOUR_SERVER_IP
```

***

## 六、关键配置文件位置

| 文件        | 服务器路径                                           | 说明            |
| --------- | ----------------------------------------------- | ------------- |
| 后端jar     | `/opt/Aiplatform/ai-gateway-platform-1.0.0.jar` | Spring Boot应用 |
| 后端日志      | `/opt/Aiplatform/logs/backend.log`              | 应用日志          |
| 后端systemd | `/etc/systemd/system/lingdian-backend.service`  | 服务管理          |
| Nginx配置   | `/etc/nginx/conf.d/lingdian.conf`               | 反向代理          |
| 前端静态文件    | `/usr/share/nginx/html/`                        | Vite构建产物      |
| 数据库脚本     | `/opt/Aiplatform/rebuild.sql`                   | 数据库重建         |

***

## 七、systemd服务配置

### lingdian-backend.service

```ini
[Unit]
Description=Lingdian AI Gateway Backend
After=network.target docker.service

[Service]
User=admin
WorkingDirectory=/opt/Aiplatform
ExecStart=/usr/bin/java -jar /opt/Aiplatform/ai-gateway-platform-1.0.0.jar \
  --spring.profiles.active=docker \
  --spring.datasource.url="jdbc:mysql://localhost:3306/ai_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  --spring.data.redis.host=localhost
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 常用命令

```bash
# 重启后端
systemctl restart lingdian-backend.service

# 查看状态
systemctl status lingdian-backend.service

# 查看日志
journalctl -u lingdian-backend.service -f

# 重载配置
systemctl daemon-reload
```

***

## 八、Nginx配置

### /etc/nginx/conf.d/lingdian.conf

```nginx
server {
    listen 80;
    server_name lingdian-ai.top www.lingdian-ai.top;
    root /usr/share/nginx/html;
    index index.html;

    # 前端静态资源
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 常用命令

```bash
# 测试配置
nginx -t

# 重启Nginx
systemctl restart nginx

# 查看状态
systemctl status nginx
```

***

## 九、数据库信息

| 项目           | 值                |
| ------------ | ---------------- |
| 数据库名         | `ai_gateway`     |
| MySQL root密码 | `root123`        |
| Docker容器     | `lingdian-mysql` |
| 端口           | `3306` (仅容器内)    |

### 常用命令

```bash
# 进入MySQL
docker exec -it lingdian-mysql mysql -uroot -proot123 ai_gateway

# 执行SQL文件
docker exec -i lingdian-mysql mysql -uroot -proot123 ai_gateway < /path/to/script.sql

# 查看表
docker exec lingdian-mysql mysql -uroot -proot123 ai_gateway -e "SHOW TABLES;"
```

***

## 十、常见问题

### 10.1 端口8080被占用

```bash
# 查找占用进程
lsof -i:8080

# 强制清理所有Java进程
ps aux | grep java | grep -v grep | awk '{print $2}' | xargs kill -9

# 等待3秒后重启
sleep 3
systemctl restart lingdian-backend.service
```

### 10.2 Java版本不兼容

```bash
# 检查当前版本
java -version

# 安装Java 21
dnf install java-21-openjdk-devel -y

# 设置默认版本
alternatives --config java
# 选择Java 21对应的路径
```

### 10.3 前端资源404

```bash
# 检查文件是否存在
ls -la /usr/share/nginx/html/assets/

# 重新上传
scp -i $HOME/Desktop/fortest.pem -r \
  $HOME/Documents/Aiplatform/Aiplatform-web/dist/* \
  root@YOUR_SERVER_IP:/usr/share/nginx/html/

# 重启Nginx
systemctl restart nginx
```

### 10.4 后端启动失败

```bash
# 查看详细日志
journalctl -u lingdian-backend.service -n 100 --no-pager

# 检查数据库连接
docker exec lingdian-mysql mysql -uroot -proot123 -e "SELECT 1"

# 检查Redis连接
docker exec lingdian-redis redis-cli ping
```

***

## 十一、一键部署脚本（参考）

```bash
#!/bin/bash
set -e

PEM="$HOME/Desktop/fortest.pem"
SERVER="root@YOUR_SERVER_IP"
LOCAL_BASE="$HOME/Documents/Aiplatform"

echo "=== 1. 本地构建 ==="
cd "$LOCAL_BASE/Aiplatform-web" && npx vite build
cd "$LOCAL_BASE/Aiplatform-demo" && mvn clean package -DskipTests

echo "=== 2. 停止旧服务 ==="
ssh -i $PEM $SERVER 'systemctl stop lingdian-backend.service && systemctl stop nginx && ps aux | grep java | grep -v grep | awk "{print \$2}" | xargs kill -9 2>/dev/null; sleep 3'

echo "=== 3. 上传新文件 ==="
scp -i $PEM "$LOCAL_BASE/Aiplatform-demo/target/ai-gateway-platform-1.0.0.jar" $SERVER:/opt/Aiplatform/
scp -i $PEM -r "$LOCAL_BASE/Aiplatform-web/dist/"* $SERVER:/usr/share/nginx/html/

echo "=== 4. 启动服务 ==="
ssh -i $PEM $SERVER 'systemctl restart lingdian-backend.service && systemctl restart nginx'

echo "=== 5. 等待后端启动 ==="
sleep 30

echo "=== 6. 验证 ==="
ssh -i $PEM $SERVER 'curl -s -o /dev/null -w "后端API HTTP: %{http_code}\n" http://localhost:8080/api/platform-model/list && curl -s -o /dev/null -w "前端 HTTP: %{http_code}\n" http://localhost/'

echo "=== 部署完成 ==="
```

***

## 十二、测试账户

| 用户名             | 角色    | 密码            |
| --------------- | ----- | ------------- |
| `superadmin`    | 超级管理员 | `Test@123456` |
| `testuser`      | 测试用户  | `Test@123456` |
| `silver_user`   | 白银会员  | `Test@123456` |
| `gold_user`     | 黄金会员  | `Test@123456` |
| `diamond_user`  | 钻石会员  | `Test@123456` |
| `free_user`     | 免费用户  | `Test@123456` |
| `disabled_user` | 禁用用户  | `Test@123456` |

***

> **最后更新**: 2026-05-20
> **维护者**: AI Gateway Team

