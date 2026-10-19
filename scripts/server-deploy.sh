#!/bin/bash
# =============================================================================
# 部署: 先验收，再同步 Compose/镜像；复用现有 Redis/backend/frontend，MySQL 保持不动
# =============================================================================
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEY="${AI_PLATFORM_DEPLOY_SSH_KEY:-}"
HOST="${AI_PLATFORM_DEPLOY_HOST:-}"
SSH_OPTS="-o StrictHostKeyChecking=yes -o ConnectTimeout=30"
REMOTE_DIR=/root/lingdian
TAR_DIR=/tmp/ld-build
DEPLOY_STAMP="$(date +%Y%m%d%H%M%S)-$$"

if [[ -z "$HOST" ]]; then
  echo "缺少 AI_PLATFORM_DEPLOY_HOST；未连接远程服务器。" >&2
  exit 1
fi
if [[ -z "$KEY" || ! -f "$KEY" ]]; then
  echo "AI_PLATFORM_DEPLOY_SSH_KEY 未配置或文件不可读；未连接远程服务器。" >&2
  exit 1
fi

echo "===== 本地发布前置验收（通过后才允许传输镜像） ====="
bash "$ROOT/scripts/local-release-preflight.sh" --local-only

echo "===== 只读校验服务器密钥配置和现有容器（不输出配置值） ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" \
  "docker inspect --format '{{.State.Running}}' lingdian-mysql | grep -qx true && \
   docker inspect --format '{{.State.Running}}' lingdian-redis | grep -qx true && \
   docker inspect --format '{{.State.Running}}' lingdian-backend | grep -qx true && \
   docker inspect --format '{{.State.Running}}' lingdian-frontend | grep -qx true && \
   cd '$REMOTE_DIR' && python3 - --server --env-file .env.prod" \
  < "$ROOT/scripts/check-production-env.py"

echo "===== 使用服务器 .env.prod 只读校验本地 Compose（不输出配置值） ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" \
  "cd '$REMOTE_DIR' && docker compose --env-file .env.prod -f - config -q" \
  < "$ROOT/docker-compose.server.yml"

echo "===== 1. 传输镜像 tar 到服务器 (后端217M + 前端27M) ====="
scp $SSH_OPTS -i "$KEY" \
  "$TAR_DIR/aiplatform-backend:prod.tar" \
  "$TAR_DIR/aiplatform-frontend:prod.tar" \
  "$HOST:$REMOTE_DIR/"
echo "  -> 传输完成"

echo "===== 暂存并校验本地服务器 Compose 配置 ====="
scp $SSH_OPTS -i "$KEY" \
  "$ROOT/docker-compose.server.yml" \
  "$HOST:$REMOTE_DIR/.docker-compose.server.yml.next"
ssh $SSH_OPTS -i "$KEY" "$HOST" "cd '$REMOTE_DIR' && \
  docker compose -f .docker-compose.server.yml.next --env-file .env.prod config -q"
echo "  -> 暂存配置解析通过"

echo ""
echo "===== 2. 服务器加载镜像并重标记为 :latest ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cd $REMOTE_DIR && \
  docker load -i 'aiplatform-backend:prod.tar' && \
  docker load -i 'aiplatform-frontend:prod.tar' && \
  docker tag aiplatform-backend:prod aiplatform-backend:latest && \
  docker tag aiplatform-frontend:prod aiplatform-frontend:latest && \
  echo '--- 镜像列表 ---' && \
  docker images | grep aiplatform"

echo "===== 原子更新服务器 Compose（先留备份，再重启现有服务） ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cd '$REMOTE_DIR' && \
  cp -p docker-compose.server.yml 'docker-compose.server.yml.backup.$DEPLOY_STAMP' && \
  mv -f .docker-compose.server.yml.next docker-compose.server.yml"
echo "  -> 旧 Compose 已备份并原子替换"

echo ""
echo "===== 3. 更新现有 redis + backend + frontend (保留 MySQL 和数据卷) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cd $REMOTE_DIR && \
  docker compose -f docker-compose.server.yml --env-file .env.prod up -d redis backend frontend && \
  echo '--- 等待 8 秒后查看状态 ---' && sleep 8 && \
  docker compose -f docker-compose.server.yml ps"

echo ""
echo "===== 4. 后端启动日志(最近40行) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker logs lingdian-backend --tail 40 2>&1"
