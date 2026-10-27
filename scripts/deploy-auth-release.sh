#!/usr/bin/env bash
set -eo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REMOTE_ROOT="/root/lingdian"
MODE="--plan"
if (( $# > 0 )); then MODE="$1"; fi
if (( $# > 1 )) || [[ "$MODE" != "--plan" && "$MODE" != "--apply" ]]; then
  echo "用法: $0 [--plan|--apply]" >&2
  exit 2
fi
if [[ "$MODE" == "--plan" ]]; then
  echo "计划模式：本地全量验收后部署 QQ OAuth 与邮箱验证码注册/登录。"
  echo "只复用现有后端镜像；仅重建 backend 服务以加载环境变量，不动数据库、Redis、Nginx 与游戏容器。"
  echo "新凭据仅通过隐藏输入或进程环境，经 SSH 标准输入写入服务器 /root/lingdian/.env.prod。"
  exit 0
fi
if [[ "$AI_PLATFORM_LEGAL_PAGES_APPROVED" != "true" ]]; then
  echo "发布已停止：法律页面终稿需先明确批准。" >&2
  exit 2
fi
if rg -q '本草案|待运营方提供|尚待确认|仍待确认|正式发布前请补充|本地审阅草案' "$ROOT/Aiplatform-web/src/pages/LegalDocumentPage.tsx"; then
  echo "发布已停止：法律页面仍含草案或待补充标记。" >&2
  exit 2
fi

HOST="$AI_PLATFORM_DEPLOY_HOST"
KEY="$AI_PLATFORM_DEPLOY_SSH_KEY"
if [[ -z "$HOST" || -z "$KEY" || ! -f "$KEY" ]]; then
  echo "缺少 AI_PLATFORM_DEPLOY_HOST 或不可读的 SSH 私钥；未连接服务器。" >&2
  exit 2
fi
QQ_APP_ID="1905883463"
QQ_APP_KEY="$QQ_OAUTH_APP_KEY"
SMTP_PASSWORD="$SPRING_MAIL_PASSWORD"
if [[ -z "$QQ_APP_KEY" ]]; then
  read -r -s -p '请输入 QQ 互联 AppKey（输入不回显）：' QQ_APP_KEY
  echo
fi
if [[ -z "$SMTP_PASSWORD" ]]; then
  read -r -s -p '请输入当前阿里云 DirectMail SMTP 密码（输入不回显）：' SMTP_PASSWORD
  echo
fi
if [[ -z "$QQ_APP_KEY" || -z "$SMTP_PASSWORD" ]]; then
  echo "缺少认证凭据；线上服务未修改。" >&2
  exit 2
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
LOCAL_STAGE="$(mktemp -d /tmp/lingdian-auth-release.XXXXXX)"
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

echo "===== 传输验收产物到隔离发布目录 ====="
ssh -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=yes "$HOST" \
  "umask 077 && mkdir -p '$REMOTE_STAGE' && chmod 700 '$REMOTE_STAGE'"
scp -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=yes \
  "$LOCAL_STAGE/backend.jar" "$LOCAL_STAGE/frontend.tar.gz" "$LOCAL_STAGE/SHA256SUMS" \
  "$ROOT/scripts/deploy-auth-release-remote.sh" "$HOST:$REMOTE_STAGE/"
ssh -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=yes "$HOST" \
  "chmod 700 '$REMOTE_STAGE/deploy-auth-release-remote.sh' && bash '$REMOTE_STAGE/deploy-auth-release-remote.sh' --check '$STAMP'"

echo "===== 应用线上配置并发布 ====="
printf '%s\0' "$QQ_APP_ID" "$QQ_APP_KEY" "$SMTP_PASSWORD" \
  | ssh -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=yes "$HOST" \
      "bash '$REMOTE_STAGE/deploy-auth-release-remote.sh' --apply '$STAMP'"

echo "发布通过：QQ OAuth 已就绪（审核状态保持 pending）；邮箱注册验证码及已验证邮箱登录已启用。"
echo "未新增数据库迁移；未构建或拉取镜像；其他现有服务未重启。回滚备份位于 $REMOTE_ROOT/backups/$STAMP."
