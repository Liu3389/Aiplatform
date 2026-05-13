# 凌点 AI 网关 — 全栈部署与运维指南

> **一句话：拷到任何设备，`./start.sh` 一键启动全栈（前端 + 后端 + MySQL + Redis + 可选公网隧道）。**

---

## 目录

- [1. 项目文件结构](#1-项目文件结构)
- [2. 环境要求](#2-环境要求)
- [3. 三种部署模式](#3-三种部署模式)
- [4. 快速开始（30 秒）](#4-快速开始30-秒)
- [5. 配置说明（.env）](#5-配置说明env)
- [6. 部署模式详解](#6-部署模式详解)
  - [6.1 全栈一体](#61-全栈一体默认)
  - [6.2 仅后端 + 数据库](#62-仅后端--数据库)
  - [6.3 仅前端（连接远程后端）](#63-仅前端连接远程后端)
- [7. 公网部署（Cloudflare Tunnel）](#7-公网部署cloudflare-tunnel)
- [8. 数据运维](#8-数据运维)
  - [8.1 备份数据](#81-备份数据)
  - [8.2 恢复数据](#82-恢复数据)
  - [8.3 跨设备迁移](#83-跨设备迁移)
  - [8.4 清除所有数据重新开始](#84-清除所有数据重新开始)
- [9. DataGrip / 外部工具连接](#9-datagrip--外部工具连接)
- [10. 日常运维命令](#10-日常运维命令)
- [11. 常见问题](#11-常见问题)

---

## 1. 项目文件结构

```
Aiplatform/                          ← 项目根目录（所有操作在此目录下执行）
│
├── .env                             ← 【唯一配置入口】改端口/密码/域名都在这里
├── .env.example                     ← 配置模板（可提交 Git）
├── .gitignore                       ← 排除 .env + backups/
│
├── docker-compose.allinone.yml      ← 一体化全栈部署（MySQL+Redis+后端+前端+Tunnel）
├── docker-compose.backend.yml       ← 仅后端+数据库（MySQL+Redis+SpringBoot）
├── docker-compose.frontend.yml      ← 仅前端（Nginx反代到远程后端）
│
├── start.sh                         ← 一键部署启动（自动选模式）
├── stop.sh                          ← 停止所有服务
├── backup.sh                        ← 备份 MySQL + Redis 数据
├── restore.sh                       ← 恢复备份数据
├── migrate.sh                       ← 跨设备迁移数据
│
├── Aiplatform-web/                  ← React 前端源码
│   └── docker/
│       ├── Dockerfile               ← 前端镜像构建文件
│       ├── nginx.conf               ← Nginx 反向代理配置
│       └── docker-entrypoint.sh     ← 容器启动脚本
│
├── Aiplatform-demo/                 ← SpringBoot 后端源码
│   └── docker/
│       └── Dockerfile               ← 后端镜像构建文件
│
└── backups/                         ← 备份文件存储目录（自动创建）
```

---

## 2. 环境要求

| 依赖 | 版本要求 | 安装方式 |
|------|----------|----------|
| **Docker** | 24+ | [Docker Desktop](https://docs.docker.com/get-docker/) |
| **Docker Compose** | V2 | 内置在 Docker Desktop 中 |
| **Git**（可选） | 任意 | `brew install git` / `apt install git` |

> 不需要安装 Node.js、Java、Maven、Nginx——全部在 Docker 容器内。

---

## 3. 三种部署模式

| 模式 | 包含服务 | compose 文件 | 适用场景 |
|------|----------|-------------|----------|
| **allinone** | MySQL + Redis + 后端 + 前端 + Tunnel(可选) | `docker-compose.allinone.yml` | 一台设备搞定一切 |
| **backend** | MySQL + Redis + 后端 | `docker-compose.backend.yml` | 后端单独部署到服务器 |
| **frontend** | 前端（Nginx 反代到远程后端） | `docker-compose.frontend.yml` | 前端部署到 CDN/边缘节点 |

在 `.env` 中修改 `DEPLOY_MODE` 即可切换，`./start.sh` 自动选择对应文件。

---

## 4. 快速开始（30 秒）

```bash
# 1. 进入项目目录
cd ~/Documents/Aiplatform

# 2. 首次启动（自动构建镜像，约 5-10 分钟）
./start.sh

# 3. 打开浏览器
# http://localhost
# 登录: superadmin / admin123
```

第二次启动只需要 3-5 秒（镜像已缓存）。

---

## 5. 配置说明（.env）

所有可修改的配置集中在项目根目录的 `.env` 文件中。修改后执行 `./start.sh` 即可生效，**无需重新构建镜像**。

```bash
# -------------------- 数据库 --------------------
MYSQL_ROOT_PASSWORD=root123     # MySQL root 密码
MYSQL_DATABASE=ai_gateway       # 数据库名
MYSQL_PORT=3307                 # 宿主机 MySQL 端口映射

# -------------------- Redis --------------------
REDIS_PASSWORD=redis123          # Redis 密码
REDIS_PORT=6380                  # 宿主机 Redis 端口映射

# -------------------- 后端 --------------------
BACKEND_PORT=8080                # 宿主机后端端口

# -------------------- 前端 --------------------
FRONTEND_PORT=80                 # 宿主机前端端口
BACKEND_HOST=localhost:8080      # 前端反代的后端地址（仅 frontend 模式有效）

# -------------------- Cloudflare Tunnel 公网部署 --------------------
CF_TUNNEL_TOKEN=                 # 隧道 Token（留空则不启用公网隧道）

# -------------------- 部署模式 --------------------
DEPLOY_MODE=allinone             # allinone | backend | frontend

# -------------------- 数据备份 --------------------
BACKUP_DIR=./backups             # 备份文件存放目录
```

### 常见改法示例

```bash
# 前端跑在 8081 而不是 80
FRONTEND_PORT=8081

# 后端跑在 9090
BACKEND_PORT=9090

# 只部署后端（数据库密码改掉）
DEPLOY_MODE=backend
MYSQL_ROOT_PASSWORD=MySecurePass999
REDIS_PASSWORD=MyRedisPass999
```

---

## 6. 部署模式详解

### 6.1 全栈一体（默认）

```bash
./start.sh
```

| 服务 | 访问地址 | 说明 |
|------|---------|------|
| 前端页面 | http://localhost:80 | Nginx → 后端自动反代 /api/ |
| 后端 API | http://localhost:8080/api | Spring Boot |
| MySQL | localhost:3307 | root/root123 |
| Redis | localhost:6380 | 密码 redis123 |

### 6.2 仅后端 + 数据库

```bash
./start.sh --backend
# 或者在 .env 设 DEPLOY_MODE=backend
```

| 服务 | 访问地址 |
|------|---------|
| 后端 API | http://localhost:8080/api |
| MySQL | localhost:3307 |
| Redis | localhost:6380 |

### 6.3 仅前端（连接远程后端）

```bash
# 先修改 .env
BACKEND_HOST=10.211.55.10:8080    # 指向远程后端 IP
DEPLOY_MODE=frontend

./start.sh --frontend
```

前端 Nginx 会把所有 `/api/*` 请求反代到 `BACKEND_HOST` 指定的后端。SSE 流式对话也能正常工作。

---

## 7. 公网部署（Cloudflare Tunnel）

> 零费用、无需公网 IP、无需开放防火墙端口，适合 Edge/家庭宽带/内网环境。

### 7.1 前提条件

- 拥有一个域名（如 `lingdian-ai.top`），DNS 托管在 Cloudflare
- 在任意有 cloudflared 的设备上创建隧道并获取 Token

### 7.2 创建隧道（首次）

```bash
# 1. 安装 cloudflared
# macOS:
brew install cloudflare/cloudflare/cloudflared
# Linux:
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
chmod +x cloudflared && sudo mv cloudflared /usr/local/bin/

# 2. 登录 Cloudflare
cloudflared tunnel login
# 浏览器弹窗 → 选择域名

# 3. 创建隧道
cloudflared tunnel create lingdian-ai

# 4. 绑定域名
cloudflared tunnel route dns lingdian-ai lingdian-ai.top
cloudflared tunnel route dns lingdian-ai www.lingdian-ai.top

# 5. 获取 Token（关键步骤！）
cloudflared tunnel token lingdian-ai
# 复制输出的 token 字符串
```

### 7.3 填入 Token 并部署

```bash
# 1. 编辑 .env，粘贴 Token
CF_TUNNEL_TOKEN=eyJh...你的token字符串...

# 2. 启动（带隧道）
./start.sh --build --tunnel
```

### 7.4 Cloudflare 后台必须配置

| 位置 | 配置项 | 设置值 |
|------|--------|--------|
| SSL/TLS → 概述 | 加密模式 | **灵活 (Flexible)** |
| SSL/TLS → 边缘证书 | 始终使用 HTTPS | **开启** |
| SSL/TLS → 边缘证书 | TLS 1.3 | **开启** |
| 规则 → 页面规则 | URL: `lingdian-ai.top/*` | 缓存级别 → **绕过缓存** |

> ⚠️ **"绕过缓存"是SSE流式对话正常工作的关键**。不设此规则，AI 回复会一次性全部吐出而非逐字流式输出。

---

## 8. 数据运维

### 8.1 备份数据

```bash
./backup.sh
```

自动备份 MySQL（完整 dump）+ Redis（RDB 快照），打包为：
```
backups/lingdian_backup_20260113_143022.tar.gz
```

### 8.2 恢复数据

```bash
# 查看可用备份
ls -la backups/

# 恢复指定备份
./restore.sh backups/lingdian_backup_20260113_143022.tar.gz
```

恢复时会先确认（`[y/N]`），防止误操作。

### 8.3 跨设备迁移

**方式一：自动推送（推荐）**

```bash
# 源设备
./migrate.sh push user@新设备IP:~/Aiplatform

# 新设备
cd ~/Aiplatform
./start.sh            # 先启动服务（初始化数据库表结构）
./migrate.sh import   # 再导入数据
```

**方式二：手动拷贝**

```bash
# 源设备
./backup.sh
scp backups/lingdian_backup_xxx.tar.gz user@新设备:~/Aiplatform/backups/

# 新设备
cd ~/Aiplatform
./start.sh
./restore.sh backups/lingdian_backup_xxx.tar.gz
```

### 8.4 清除所有数据重新开始

```bash
# ⚠️ 此操作会删除所有数据库和 Redis 数据！
docker compose -f docker-compose.allinone.yml down -v
./start.sh
```

---

## 9. DataGrip / 外部工具连接

### MySQL

| 配置项 | 值 |
|--------|-----|
| Host | `localhost` |
| Port | `3307`（或你在 .env 设的自定义端口） |
| User | `root` |
| Password | `root123` |
| Database | `ai_gateway` |

DataGrip：`+` → Data Source → MySQL → 填上面 → Test Connection。

### Redis

| 配置项 | 值 |
|--------|-----|
| Host | `localhost` |
| Port | `6380` |
| Password | `redis123` |

DataGrip 2024+：`+` → Data Source → Redis → 填上面。

### 后端 API 调试

```bash
# 健康检查
curl http://localhost:8080/api/actuator/health

# 登录获取 Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"superadmin","password":"admin123"}'

# 带 Token 调接口
curl http://localhost:8080/api/user/info \
  -H "X-User-Token: <上面返回的token>"
```

---

## 10. 日常运维命令

| 操作 | 命令 |
|------|------|
| 启动 | `./start.sh` |
| 启动（含公网隧道） | `./start.sh --tunnel` |
| 重新构建+启动 | `./start.sh --build` |
| 停止 | `./stop.sh` |
| 查看容器状态 | `docker compose -f docker-compose.allinone.yml ps` |
| 查看全部日志 | `docker compose -f docker-compose.allinone.yml logs -f` |
| 查看后端日志 | `docker compose -f docker-compose.allinone.yml logs -f ai-gateway-app` |
| 查看前端日志 | `docker compose -f docker-compose.allinone.yml logs -f lingdian-frontend` |
| 重启全部 | `docker compose -f docker-compose.allinone.yml restart` |
| 仅重启后端 | `docker compose -f docker-compose.allinone.yml restart ai-gateway-app` |
| 进入 MySQL | `docker exec -it lingdian-mysql mysql -uroot -proot123 ai_gateway` |
| 进入 Redis | `docker exec -it lingdian-redis redis-cli -a redis123` |
| 备份 | `./backup.sh` |
| 恢复 | `./restore.sh backups/xxx.tar.gz` |
| 清除数据 | `docker compose -f docker-compose.allinone.yml down -v` |

---

## 11. 常见问题

### Q: 端口被占用怎么办？
修改 `.env` 中的端口号，例如改成 `FRONTEND_PORT=8081`，然后 `./start.sh`。

### Q: 镜像拉取太慢？
国内网络可预先从镜像站拉取基础镜像：
```bash
docker pull docker.m.daocloud.io/library/mysql:8.0 && docker tag docker.m.daocloud.io/library/mysql:8.0 mysql:8.0
docker pull docker.m.daocloud.io/library/redis:7-alpine && docker tag docker.m.daocloud.io/library/redis:7-alpine redis:7-alpine
docker pull docker.m.daocloud.io/library/node:20-alpine && docker tag docker.m.daocloud.io/library/node:20-alpine node:20-alpine
docker pull docker.m.daocloud.io/library/nginx:1.27-alpine && docker tag docker.m.daocloud.io/library/nginx:1.27-alpine nginx:1.27-alpine
docker pull docker.m.daocloud.io/library/amazoncorretto:21-alpine && docker tag docker.m.daocloud.io/library/amazoncorretto:21-alpine amazoncorretto:21-alpine
```
然后再 `./start.sh --build`。

### Q: 登录报"系统异常"？
通常是 Nginx 到后端的代理问题。检查：
1. 后端容器是否 healthy：`docker compose ps`
2. Nginx 配置是否匹配：`docker exec lingdian-frontend cat /etc/nginx/conf.d/default.conf | grep proxy_pass`

### Q: 想修改代码后部署？
```bash
# 前端改了
./start.sh --build
# 或仅重建前端
docker compose -f docker-compose.allinone.yml up --build -d lingdian-frontend

# 后端改了
docker compose -f docker-compose.allinone.yml up --build -d ai-gateway-app
```

### Q: Windows 上如何部署？
安装 Docker Desktop for Windows，打开 PowerShell：
```powershell
cd X:\path\to\Aiplatform
bash ./start.sh
# 或直接
docker compose -f docker-compose.allinone.yml up -d
```
所有 .sh 脚本在 Git Bash 或 WSL 中执行。
