#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_ROOT="/root/lingdian"
LEGAL_SOURCE="$ROOT/Aiplatform-web/src/pages/LegalDocumentPage.tsx"
MODE="${1:---plan}"

if (( $# > 1 )) || [[ "$MODE" != "--plan" && "$MODE" != "--apply" ]]; then
  echo "用法: $0 [--plan|--apply]" >&2
  exit 2
fi

legal_draft_markers_present() {
  rg -q '本草案|待运营方提供|尚待确认|仍待确认|正式发布前请补充|本地审阅草案' "$LEGAL_SOURCE"
}

if [[ "$MODE" == "--plan" ]]; then
  echo "计划模式：不会连接服务器、传输文件或修改容器。"
  echo "目标：原 lingdian-backend 与 lingdian-frontend 容器；仅替换 JAR/静态文件并重启原 backend。"
  echo "保持 lingdian-mysql、lingdian-redis 及其余现有容器不变；不加载镜像、不创建或替换容器。"
  if legal_draft_markers_present; then
    echo "当前阻塞：法律页仍含草案/待补充标记，正式页面完成前禁止应用。"
  else
    echo "法律页未检测到已知草案标记；应用仍要求 AI_PLATFORM_LEGAL_PAGES_APPROVED=true。"
  fi
  exit 0
fi

if [[ "${AI_PLATFORM_LEGAL_PAGES_APPROVED:-}" != "true" ]]; then
  echo "发布已停止：请在运营主体、公开联系邮箱和法律正文最终确认后显式设置 AI_PLATFORM_LEGAL_PAGES_APPROVED=true。" >&2
  exit 2
fi
if legal_draft_markers_present; then
  echo "发布已停止：法律页仍包含草案或待补充内容；未连接服务器。" >&2
  exit 2
fi

HOST="${AI_PLATFORM_DEPLOY_HOST:-}"
KEY="${AI_PLATFORM_DEPLOY_SSH_KEY:-}"
if [[ -z "$HOST" ]]; then
  echo "缺少 AI_PLATFORM_DEPLOY_HOST；未连接服务器。" >&2
  exit 2
fi
if [[ -z "$KEY" || ! -f "$KEY" ]]; then
  echo "AI_PLATFORM_DEPLOY_SSH_KEY 未配置或不可读；未连接服务器。" >&2
  exit 2
fi

SSH_OPTS=(-o StrictHostKeyChecking=yes -o ConnectTimeout=30 -i "$KEY")
STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
LOCAL_STAGE="$(mktemp -d "${TMPDIR:-/tmp}/lingdian-existing-deploy.XXXXXX")"
REMOTE_STAGE="$REMOTE_ROOT/releases/$STAMP"
trap 'rm -rf "$LOCAL_STAGE"' EXIT

echo "===== 本地发布验收 ====="
bash "$ROOT/scripts/local-release-preflight.sh" --local-only

echo "===== 构建发布 JAR ====="
(
  cd "$ROOT/Aiplatform-demo"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.runtime/local.env"
  set +a
  mvn -q -DskipTests package
)

BACKEND_JAR="$ROOT/Aiplatform-demo/target/ai-gateway-platform.jar"
FRONTEND_DIST="$ROOT/Aiplatform-web/dist"
[[ -s "$BACKEND_JAR" && -s "$FRONTEND_DIST/index.html" ]] || {
  echo "发布产物缺失；未连接服务器。" >&2
  exit 1
}

cp "$BACKEND_JAR" "$LOCAL_STAGE/backend.jar"
tar -C "$FRONTEND_DIST" -czf "$LOCAL_STAGE/frontend.tar.gz" .
(cd "$LOCAL_STAGE" && shasum -a 256 backend.jar frontend.tar.gz > SHA256SUMS)

echo "===== 上传前只读核验现有服务 ====="
ssh "${SSH_OPTS[@]}" "$HOST" "bash -s -- --check '$STAMP'" \
  < "$ROOT/scripts/deploy-existing-containers-remote.sh"

echo "===== 传输已验收产物与发布辅助脚本 ====="
ssh "${SSH_OPTS[@]}" "$HOST" "umask 077 && mkdir -p '$REMOTE_STAGE' && chmod 700 '$REMOTE_STAGE'"
scp "${SSH_OPTS[@]}" \
  "$LOCAL_STAGE/backend.jar" \
  "$LOCAL_STAGE/frontend.tar.gz" \
  "$LOCAL_STAGE/SHA256SUMS" \
  "$ROOT/scripts/deploy-existing-containers-remote.sh" \
  "$HOST:$REMOTE_STAGE/"
ssh "${SSH_OPTS[@]}" "$HOST" "chmod 700 '$REMOTE_STAGE/deploy-existing-containers-remote.sh' && bash '$REMOTE_STAGE/deploy-existing-containers-remote.sh' --apply '$STAMP'"

echo "发布通过：使用原有容器；数据库与 Redis 未重启；备份保存在服务器 /root/lingdian/backups/$STAMP。"
