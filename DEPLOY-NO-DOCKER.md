# 凌点 AI 网关 - 无 Docker 部署文档

## 环境概览

| 项目     | 说明                               |
| ------ | -------------------------------- |
| 服务器    | 阿里云 ECS，公网 IP: YOUR_SERVER_IP       |
| 操作系统   | Rocky Linux 9.5                  |
| SSH 密钥 | $HOME/Desktop/fortest.pem |
| 域名     | lingdian-ai.top（已备案）             |

## 架构说明

```
用户浏览器 → Nginx(:80) → 前端静态文件 (/opt/lingdian-ai/frontend-dist/)
                         → /api/* 代理 → Java 后端 (:8080)
                         → /chat/* 代理 → Java 后端 (:8080) (SSE 流式)
                         → /v1/*  代理 → Java 后端 (:8080) (文件上传)
                         
Java 后端 (:8080) → MySQL (Docker容器, :3306)
                 → Redis (Docker容器, :6379)
```

> 注意：MySQL 和 Redis 目前使用 Docker 容器运行（非 systemd 服务），后端 Java 是直接 jar 包运行。

***

## 一、环境要求

服务器需要安装：

- **Java 21+**：运行 Spring Boot 后端
- **Nginx**：前端静态文件 + 反向代理
- **Docker**：仅用于运行 MySQL 和 Redis（可改为直接安装）
- **Node.js 18+**（仅构建机）：前端构建

***

## 二、基础设施配置

### 2.1 MySQL (Docker 容器)

```bash
# 镜像: mysql:8.0
# 容器名: lingdian-mysql
# 端口映射: 3306:3306
# root 密码: root123
# 数据库: ai_gateway

# 重启容器
docker restart lingdian-mysql

# 进入 MySQL
docker exec -it lingdian-mysql mysql -uroot -proot123 ai_gateway
```

### 2.2 Redis (Docker 容器)

```bash
# 镜像: redis:7-alpine
# 容器名: lingdian-redis
# 端口映射: 6379:6379
# 密码: redis123

# 重启容器
docker restart lingdian-redis

# 测试连接
docker exec lingdian-redis redis-cli -a redis123 PING
# 应返回: PONG
```

***

## 三、数据库初始化

```bash
# 上传重建脚本到服务器
scp -i $HOME/Desktop/fortest.pem \
  $HOME/Documents/Aiplatform/Aiplatform-demo/src/main/resources/sql/rebuild.sql \
  root@YOUR_SERVER_IP:/opt/Aiplatform/rebuild.sql

# SSH 到服务器后执行
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP
docker exec -i lingdian-mysql mysql -uroot -proot123 ai_gateway < /opt/Aiplatform/rebuild.sql
```

***

## 四、后端部署

### 4.1 构建后端 JAR

在本地项目目录执行：

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo
mvn clean package -DskipTests
```

### 4.2 上传 JAR 到服务器

```bash
scp -i $HOME/Desktop/fortest.pem \
  target/ai-gateway-platform-1.0.0.jar \
  root@YOUR_SERVER_IP:/opt/Aiplatform/
```

### 4.3 启动后端

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP

# 先停掉旧进程
kill $(ps aux | grep 'ai-gateway-platform' | grep -v grep | awk '{print $2}')

# 启动（使用 docker profile 配置）
cd /opt/Aiplatform
nohup java -jar ai-gateway-platform-1.0.0.jar \
  --spring.profiles.active=docker \
  --spring.datasource.url="jdbc:mysql://localhost:3306/ai_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  --spring.data.redis.host=localhost \
  --spring.data.redis.password=redis123 \
  > app.log 2>&1 &

# 查看日志
tail -f /opt/Aiplatform/app.log
```

### 4.4 数据库连接配置

| 配置项     | 值              |
| ------- | -------------- |
| 数据库地址   | localhost:3306 |
| 数据库名    | ai\_gateway    |
| 用户名     | root           |
| 密码      | root123        |
| Profile | docker         |

### 4.5 Redis 连接配置

| 配置项 | 值              |
| --- | -------------- |
| 地址  | localhost:6379 |
| 密码  | redis123       |
| 数据库 | 0              |

### 4.6 验证后端

```bash
curl http://127.0.0.1:8080/api/platform-model/list
# 应返回 code:200 和模型列表JSON
```

***

## 五、前端部署

### 5.1 本地构建

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-web
npm install
npm run build
# 产物在 dist/ 目录
```

### 5.2 上传到服务器

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP \
  "mkdir -p /opt/lingdian-ai/frontend-dist"

scp -i $HOME/Desktop/fortest.pem \
  -r $HOME/Documents/Aiplatform/Aiplatform-web/dist/* \
  root@YOUR_SERVER_IP:/opt/lingdian-ai/frontend-dist/
```

***

## 六、Nginx 配置

### 6.1 配置文件位置

```
/etc/nginx/conf.d/lingdian.conf
```

### 6.2 配置内容

```nginx
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:32m max_size=128m inactive=60m use_temp_path=off;

server {
    listen 80;
    server_name lingdian-ai.top www.lingdian-ai.top;
    root /opt/lingdian-ai/frontend-dist;
    index index.html;

    sendfile on;
    tcp_nopush on;
    aio threads;

    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 5;
    gzip_min_length 256;
    gzip_types text/plain text/css text/xml application/json application/javascript text/javascript image/svg+xml;

    # SPA 路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # index.html 不缓存
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
    }

    # JS/CSS 长期缓存（Vite 生成 hash 文件名）
    location ~* \.(js|css)$ {
        add_header Cache-Control "public, max-age=31536000, immutable" always;
    }

    # API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
        proxy_connect_timeout 5s;
    }

    # SSE 流式请求
    location /chat/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }

    # 文件上传
    location /v1/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 50M;
    }
}
```

### 6.3 生效配置

```bash
nginx -t && systemctl reload nginx
```

***

## 七、完整部署流程（一键更新）

当本地代码有更新时，执行以下步骤：

### 步骤1：构建前端

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-web
npm run build
```

### 步骤2：上传前端到服务器

```bash
scp -i $HOME/Desktop/fortest.pem \
  -r $HOME/Documents/Aiplatform/Aiplatform-web/dist/* \
  root@YOUR_SERVER_IP:/opt/lingdian-ai/frontend-dist/
```

### 步骤3：构建并部署后端（如果后端有更改）

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo
mvn clean package -DskipTests

scp -i $HOME/Desktop/fortest.pem \
  target/ai-gateway-platform-1.0.0.jar \
  root@YOUR_SERVER_IP:/opt/Aiplatform/

ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP << 'REMOTE'
  kill $(ps aux | grep 'ai-gateway-platform' | grep -v grep | awk '{print $2}') 2>/dev/null
  sleep 2
  cd /opt/Aiplatform
  nohup java -jar ai-gateway-platform-1.0.0.jar \
    --spring.profiles.active=docker \
    --spring.datasource.url="jdbc:mysql://localhost:3306/ai_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
    --spring.data.redis.host=localhost \
    --spring.data.redis.password=redis123 \
    > app.log 2>&1 &
  echo "Backend started, PID: $!"
REMOTE
```

### 步骤4：如果需要重建数据库

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP \
  "docker exec -i lingdian-mysql mysql -uroot -proot123 ai_gateway < /opt/Aiplatform/rebuild.sql"
```

***

## 八、常用运维命令

### SSH 连接

```bash
ssh -i $HOME/Desktop/fortest.pem root@YOUR_SERVER_IP
```

### 查看服务状态

```bash
# 后端进程
ps aux | grep java | grep -v grep

# 后端日志
tail -f /opt/Aiplatform/app.log

# Nginx 状态
systemctl status nginx

# MySQL Docker
docker ps | grep mysql

# Redis Docker
docker ps | grep redis
```

### 重启服务

```bash
# 重启 Nginx
systemctl reload nginx

# 重启 MySQL
docker restart lingdian-mysql

# 重启 Redis
docker restart lingdian-redis

# 重启后端
kill $(ps aux | grep 'ai-gateway-platform' | grep -v grep | awk '{print $2}')
cd /opt/Aiplatform && nohup java -jar ai-gateway-platform-1.0.0.jar \
  --spring.profiles.active=docker \
  --spring.datasource.url="jdbc:mysql://localhost:3306/ai_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  --spring.data.redis.host=localhost \
  --spring.data.redis.password=redis123 \
  > app.log 2>&1 &
```

### 检测服务健康

```bash
# 前端
curl -s -o /dev/null -w '%{http_code}' http://lingdian-ai.top/

# 后端 API
curl -s http://127.0.0.1:8080/api/platform-model/list | python3 -m json.tool | head -10

# MySQL
docker exec lingdian-mysql mysql -uroot -proot123 -e "SELECT 1"

# Redis
docker exec lingdian-redis redis-cli -a redis123 PING
```

***

## 九、密码汇总

| 服务    | 类型      | 密码/密钥                            |
| ----- | ------- | -------------------------------- |
| SSH   | 密钥文件    | $HOME/Desktop/fortest.pem |
| MySQL | root 密码 | root123                          |
| Redis | 连接密码    | redis123                         |
| 数据库名  | -       | ai\_gateway                      |

## 十、故障排查

### 后端启动失败

1. 检查 MySQL 是否运行：`docker ps | grep mysql`
2. 检查 Redis 是否运行：`docker ps | grep redis`
3. 查看后端日志：`tail -100 /opt/Aiplatform/app.log`
4. 检查端口是否被占用：`ss -tlnp | grep 8080`

### 前端页面空白

1. 检查 Nginx 是否运行：`systemctl status nginx`
2. 检查前端文件是否存在：`ls /opt/lingdian-ai/frontend-dist/index.html`
3. 检查 Nginx 配置：`nginx -t`
4. 检查后端是否可用：`curl http://127.0.0.1:8080/api/platform-model/list`

### 数据库连接失败

1. 进入容器检查：`docker exec -it lingdian-mysql mysql -uroot -proot123`
2. 检查数据库是否存在：`SHOW DATABASES;`
3. 重建数据库：执行 rebuild.sql

***

*最后更新: 2026-05-20*
