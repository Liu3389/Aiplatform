#!/bin/bash
# =============================================================================
# 凌点AI平台 一体化部署脚本 (四大模块优化版)
# 步骤: 构建JAR → 构建dist → 构建amd64镜像 → 传输部署 → 数据库迁移
# =============================================================================
set -e
cd "$(dirname "$0")/.."
PROJECT_DIR="$(pwd)"
KEY="${AI_PLATFORM_DEPLOY_SSH_KEY:-}"
HOST="${AI_PLATFORM_DEPLOY_HOST:-}"
SSH_OPTS="-o StrictHostKeyChecking=yes -o ConnectTimeout=30"
REMOTE_DIR=/root/lingdian

if [[ -z "$HOST" ]]; then
  echo "缺少 AI_PLATFORM_DEPLOY_HOST；未连接远程服务器。" >&2
  exit 1
fi
if [[ -z "$KEY" || ! -f "$KEY" ]]; then
  echo "AI_PLATFORM_DEPLOY_SSH_KEY 未配置或文件不可读；未连接远程服务器。" >&2
  exit 1
fi

echo "############################################################"
echo "# 凌点AI平台 - 全量部署开始"
echo "# 项目目录: $PROJECT_DIR"
echo "# 目标服务器: $HOST"
echo "############################################################"

# ============================================================
# Step 1: 构建后端 JAR
# ============================================================
echo ""
echo "===== [1/6] 构建后端 JAR ====="
cd "$PROJECT_DIR/Aiplatform-demo"
mvn clean package -DskipTests -q
JAR_PATH="target/ai-gateway-platform.jar"
if [ ! -f "$JAR_PATH" ]; then
  echo "ERROR: JAR 构建失败，未找到 $JAR_PATH"
  exit 1
fi
JAR_SIZE=$(du -h "$JAR_PATH" | cut -f1)
echo "  -> JAR 构建成功: $JAR_PATH ($JAR_SIZE)"

# ============================================================
# Step 2: 构建前端 dist
# ============================================================
echo ""
echo "===== [2/6] 构建前端 dist ====="
cd "$PROJECT_DIR/Aiplatform-web"
npm run build 2>&1 | tail -5
if [ ! -d "dist" ]; then
  echo "ERROR: 前端构建失败，未找到 dist 目录"
  exit 1
fi
DIST_SIZE=$(du -sh dist | cut -f1)
echo "  -> dist 构建成功: dist/ ($DIST_SIZE)"

# ============================================================
# Step 3: 构建 AMD64 Docker 镜像
# ============================================================
echo ""
echo "===== [3/6] 构建 AMD64 Docker 镜像 ====="
cd "$PROJECT_DIR"
bash scripts/build-amd64-images.sh

# ============================================================
# Step 4: 传输镜像到服务器并重启容器
# ============================================================
echo ""
echo "===== [4/6] 传输镜像并部署到服务器 ====="
bash scripts/server-deploy.sh

# ============================================================
# Step 5: 数据库增量迁移
# ============================================================
echo ""
echo "===== [5/6] 数据库增量迁移 (migration-v2.sql) ====="
# 密码留在服务器容器环境内，不回传到本机或拼入命令行参数。
scp $SSH_OPTS -i "$KEY" "$PROJECT_DIR/scripts/migration-v2.sql" "$HOST:/tmp/migration-v2.sql"
ssh $SSH_OPTS -i "$KEY" "$HOST" \
  "docker exec -i lingdian-mysql sh -c 'MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot lingdian_ai' < /tmp/migration-v2.sql" 2>&1 || \
  echo "WARNING: 迁移脚本执行有警告(可能是列/索引已存在)，检查上方输出"
echo "  -> 数据库迁移已提交执行"

# ============================================================
# Step 6: 验证部署
# ============================================================
echo ""
echo "===== [6/6] 验证部署 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cd $REMOTE_DIR && \
  echo '--- 容器状态 ---' && \
  docker compose -f docker-compose.server.yml ps && \
  echo '' && \
  echo '--- 后端健康检查 ---' && \
  curl -s -o /dev/null -w 'HTTP %{http_code}' http://localhost:8080/api/auth/balance 2>/dev/null || echo '后端未响应' && \
  echo '' && \
  echo '--- 前端健康检查 ---' && \
  curl -s -o /dev/null -w 'HTTP %{http_code}' http://localhost:8088 2>/dev/null || echo '前端未响应' && \
  echo '' && \
  echo '--- HTTPS 域名检查 ---' && \
  curl -s -o /dev/null -w 'HTTPS %{http_code}' https://lingdian-ai.top 2>/dev/null || echo 'HTTPS未响应'"

echo ""
echo "############################################################"
echo "# 部署完成!"
echo "# 访问: https://lingdian-ai.top"
echo "############################################################"
