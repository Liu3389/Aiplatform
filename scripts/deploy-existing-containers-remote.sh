#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE_ROOT="/root/lingdian"
RELEASE_STAMP="${2:-}"
PUBLIC_BASE="https://lingdian-ai.top"
BACKEND="lingdian-backend"
FRONTEND="lingdian-frontend"
CONTAINERS=(lingdian-backend lingdian-frontend lingdian-nginx lingdian-mysql lingdian-redis lingdian-mir-stream lingdian-hj666)

if (( $# != 2 )) || [[ ! "$RELEASE_STAMP" =~ ^[0-9]{8}T[0-9]{6}Z-[0-9]+$ ]]; then
  echo "用法: $0 --check|--apply YYYYMMDDTHHMMSSZ" >&2
  exit 2
fi
MODE="$1"
if [[ "$MODE" != "--check" && "$MODE" != "--apply" ]]; then
  echo "用法: $0 --check|--apply YYYYMMDDTHHMMSSZ" >&2
  exit 2
fi

RELEASE_DIR="$REMOTE_ROOT/releases/$RELEASE_STAMP"
BACKUP_DIR="$REMOTE_ROOT/backups/$RELEASE_STAMP"
HEALTH_URL="$PUBLIC_BASE/api/actuator/health"
CAPTCHA_URL="$PUBLIC_BASE/api/auth/login/captcha"
BACKEND_TOUCHED=0
FRONTEND_TOUCHED=0

fail() {
  echo "发布检查失败：$1" >&2
  return 1
}

container_id() {
  docker inspect --format '{{.Id}}' "$1"
}

check_running_container() {
  local name="$1"
  [[ "$(docker inspect --format '{{.State.Running}}' "$name")" == "true" ]] || fail "$name 未运行"
  container_id "$name"
}

CONTAINER_IDS=()
CONTAINER_STARTED_AT=()
check_existing_stack() {
  local name current_id index
  for index in "${!CONTAINERS[@]}"; do
    name="${CONTAINERS[$index]}"
    current_id="$(check_running_container "$name")"
    if [[ -z "${CONTAINER_IDS[$index]:-}" ]]; then
      CONTAINER_IDS[$index]="$current_id"
      CONTAINER_STARTED_AT[$index]="$(docker inspect --format '{{.State.StartedAt}}' "$name")"
    else
      [[ "$current_id" == "${CONTAINER_IDS[$index]}" ]] || fail "$name 容器 ID 发生变化"
    fi
  done
  docker exec "$BACKEND" test -s /app/app.jar
  docker exec "$FRONTEND" test -s /usr/share/nginx/html/index.html
  [[ "$(curl --silent --show-error --fail --max-time 20 "$HEALTH_URL" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status", ""))')" == "UP" ]] || fail "公网 backend health 未返回 UP"
}

verify_auth_routes() {
  curl --silent --show-error --fail --max-time 20 "$CAPTCHA_URL" \
    | python3 -c 'import json,sys; p=json.load(sys.stdin); d=p.get("data") or {}; assert str(p.get("code")) in {"0","200"} and d.get("captchaId") and d.get("image"), "login captcha response incomplete"'
  curl --silent --show-error --fail --max-time 20 "$PUBLIC_BASE/api/auth/oauth/providers" \
    | python3 -c 'import json,sys; p=json.load(sys.stdin); d=p.get("data") or {}; assert str(p.get("code")) in {"0","200"} and d.get("qq") is True, "QQ OAuth is not configured in backend"'
  curl --silent --show-error --fail --max-time 20 \
    "$PUBLIC_BASE/api/auth/oauth/url?provider=qq&purpose=login" \
    | python3 -c 'import json,sys; from urllib.parse import urlparse,parse_qs; p=json.load(sys.stdin); d=p.get("data") or {}; u=urlparse(d.get("url", "")); q=parse_qs(u.query); assert str(p.get("code")) in {"0","200"} and u.scheme=="https" and u.netloc=="graph.qq.com" and u.path=="/oauth2.0/authorize" and q.get("client_id") and q.get("redirect_uri") and q.get("state"), "QQ OAuth authorization URL is incomplete"'
}

verify_container_ids_unchanged() {
  local name after index
  for index in "${!CONTAINERS[@]}"; do
    name="${CONTAINERS[$index]}"
    after="$(container_id "$name")"
    [[ "$after" == "${CONTAINER_IDS[$index]}" ]] || fail "$name 容器 ID 发生变化"
  done
}

verify_non_backend_containers_not_restarted() {
  local name started_at index
  for index in "${!CONTAINERS[@]}"; do
    name="${CONTAINERS[$index]}"
    [[ "$name" == "$BACKEND" ]] && continue
    started_at="$(docker inspect --format '{{.State.StartedAt}}' "$name")"
    [[ "$started_at" == "${CONTAINER_STARTED_AT[$index]}" ]] || fail "$name 在发布期间发生重启"
  done
}

verify_web_assets() {
  local body assets asset code
  body="$(mktemp)"
  trap 'rm -f "$body"' RETURN
  code="$(curl --silent --show-error --max-time 20 -o "$body" -w '%{http_code}' "$PUBLIC_BASE/welcome")"
  [[ "$code" == "200" ]] || fail "欢迎页返回 HTTP $code"
  assets="$(python3 - "$body" <<'PY'
import html.parser, sys

class Assets(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        value = attrs.get("src") if tag == "script" else attrs.get("href") if tag == "link" else None
        if value and value.startswith("/"):
            self.paths.append(value)

parser = Assets()
parser.feed(open(sys.argv[1], encoding="utf-8").read())
print("\n".join(parser.paths))
PY
)"
  [[ -n "$assets" ]] || fail "欢迎页未引用静态 JS/CSS 资源"
  while IFS= read -r asset; do
    [[ -n "$asset" ]] || continue
    code="$(curl --silent --show-error --max-time 20 -o /dev/null -w '%{http_code}' "$PUBLIC_BASE$asset")"
    [[ "$code" == "200" ]] || fail "静态资源返回 HTTP $code"
  done <<< "$assets"
  curl --silent --show-error --fail --max-time 20 -o /dev/null "$PUBLIC_BASE/privacy"
  curl --silent --show-error --fail --max-time 20 -o /dev/null "$PUBLIC_BASE/terms"
  curl --silent --show-error --fail --max-time 20 -o /dev/null "$PUBLIC_BASE/user?tab=security"
  curl --silent --show-error --fail --max-time 20 -o /dev/null "$PUBLIC_BASE/brand/icon-macos-default.png"
}

rollback_on_error() {
  local status="$1"
  trap - ERR
  if (( status == 0 )); then
    return
  fi
  if [[ "$MODE" == "--check" ]]; then
    echo "只读检查未通过；未修改容器。" >&2
    exit "$status"
  fi
  echo "发布未通过；正在使用备份恢复已触及的原容器。" >&2
  if (( BACKEND_TOUCHED == 1 )) && [[ -s "$BACKUP_DIR/backend.jar" ]]; then
    docker cp "$BACKUP_DIR/backend.jar" "$BACKEND:/app/app.jar.restore" \
      && docker exec "$BACKEND" sh -eu -c 'mv -f /app/app.jar.restore /app/app.jar' \
      && docker restart -t 30 "$BACKEND" >/dev/null \
      || echo "backend 自动恢复失败；原 JAR 备份保留在 $BACKUP_DIR/backend.jar" >&2
  fi
  if (( FRONTEND_TOUCHED == 1 )) && [[ -s "$BACKUP_DIR/frontend/index.html" ]]; then
    docker exec "$FRONTEND" sh -eu -c 'rm -rf /usr/share/nginx/html.restore /usr/share/nginx/html.failed; mkdir -p /usr/share/nginx/html.restore' \
      && docker cp "$BACKUP_DIR/frontend/." "$FRONTEND:/usr/share/nginx/html.restore/" \
      && docker exec "$FRONTEND" sh -eu -c 'if [ -e /usr/share/nginx/html ]; then mv /usr/share/nginx/html /usr/share/nginx/html.failed; fi; mv /usr/share/nginx/html.restore /usr/share/nginx/html; rm -rf /usr/share/nginx/html.failed /usr/share/nginx/html.next /usr/share/nginx/html.previous' \
      || echo "frontend 自动恢复失败；静态文件备份保留在 $BACKUP_DIR/frontend" >&2
  fi
  exit "$status"
}
trap 'rollback_on_error $?' ERR

if [[ "$MODE" == "--check" ]]; then
  check_existing_stack
  echo "只读检查通过：现有容器、后端、前端入口、health 与登录验证码均正常。"
  exit 0
fi

[[ -s "$RELEASE_DIR/backend.jar" && -s "$RELEASE_DIR/frontend.tar.gz" \
  && -s "$RELEASE_DIR/migration-v19-user-avatar.sql" \
  && -s "$RELEASE_DIR/migration-v20-user-nickname.sql" \
  && -s "$RELEASE_DIR/SHA256SUMS" ]] || fail "发布产物不完整"
(cd "$RELEASE_DIR" && sha256sum --check --status SHA256SUMS) || fail "发布产物校验失败"
check_existing_stack

mkdir -m 700 -p "$BACKUP_DIR" "$BACKUP_DIR/frontend"
chmod 700 "$BACKUP_DIR"
docker cp "$BACKEND:/app/app.jar" "$BACKUP_DIR/backend.jar"
chmod 600 "$BACKUP_DIR/backend.jar"
docker cp "$FRONTEND:/usr/share/nginx/html/." "$BACKUP_DIR/frontend/"
[[ -s "$BACKUP_DIR/backend.jar" && -s "$BACKUP_DIR/frontend/index.html" ]] || fail "原版本备份不完整"

# Additive, idempotent schema update: old backend code remains compatible while this column is added.
docker exec -i lingdian-mysql sh -lc 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$MYSQL_DATABASE"' \
  < "$RELEASE_DIR/migration-v19-user-avatar.sql"
avatar_column_count="$(docker exec lingdian-mysql sh -lc 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -N -uroot "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=0x75736572 AND COLUMN_NAME=0x6176617461725f75726c;"')"
[[ "$avatar_column_count" == "1" ]] || fail "用户头像数据库迁移未生效"

# Add the user-facing nickname independently; this keeps the stable login username unchanged.
docker exec -i lingdian-mysql sh -lc 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$MYSQL_DATABASE"' \
  < "$RELEASE_DIR/migration-v20-user-nickname.sql"
nickname_column_count="$(docker exec lingdian-mysql sh -lc 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -N -uroot "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=0x75736572 AND COLUMN_NAME=0x6e69636b6e616d65;"')"
[[ "$nickname_column_count" == "1" ]] || fail "用户昵称数据库迁移未生效"

mkdir -m 700 -p "$RELEASE_DIR/frontend"
tar -xzf "$RELEASE_DIR/frontend.tar.gz" -C "$RELEASE_DIR/frontend"
[[ -s "$RELEASE_DIR/frontend/index.html" ]] || fail "前端发布包缺少 index.html"
docker cp "$RELEASE_DIR/backend.jar" "$BACKEND:/app/app.jar.next"
docker exec "$FRONTEND" sh -eu -c 'rm -rf /usr/share/nginx/html.next; mkdir -p /usr/share/nginx/html.next'
docker cp "$RELEASE_DIR/frontend/." "$FRONTEND:/usr/share/nginx/html.next/"
docker exec "$FRONTEND" chmod -R a+rX /usr/share/nginx/html.next

BACKEND_TOUCHED=1
docker exec "$BACKEND" sh -eu -c 'mv -f /app/app.jar.next /app/app.jar'
docker restart -t 30 "$BACKEND" >/dev/null
for attempt in {1..30}; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$BACKEND")" == "true" ]] \
    && curl --silent --show-error --fail --max-time 10 "$HEALTH_URL" \
      | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status")=="UP" else 1)'; then
    break
  fi
  (( attempt < 30 )) || fail "backend health 等待超时"
  sleep 2
done

FRONTEND_TOUCHED=1
docker exec "$FRONTEND" sh -eu -c 'rm -rf /usr/share/nginx/html.previous; mv /usr/share/nginx/html /usr/share/nginx/html.previous; mv /usr/share/nginx/html.next /usr/share/nginx/html'
verify_container_ids_unchanged
verify_non_backend_containers_not_restarted
check_existing_stack
verify_auth_routes
verify_web_assets
docker exec "$FRONTEND" rm -rf /usr/share/nginx/html.previous
FRONTEND_TOUCHED=0
BACKEND_TOUCHED=0
trap - ERR
echo "应用验收通过：容器 ID 未变；只重启 backend；MySQL、Redis 和其他容器未重启。"
echo "回滚备份保留在 ${BACKUP_DIR}（backend.jar 与 frontend/）。"
