#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE_ROOT="/root/lingdian"
PUBLIC_BASE="https://lingdian-ai.top"
BACKEND="lingdian-backend"
FRONTEND="lingdian-frontend"
MODE="$1"
RELEASE_STAMP="$2"
if (( $# != 2 )) || [[ "$MODE" != "--check" && "$MODE" != "--apply" ]] || [[ ! "$RELEASE_STAMP" =~ ^[0-9]{8}T[0-9]{6}Z-[0-9]+$ ]]; then
  echo "用法: $0 --check|--apply YYYYMMDDTHHMMSSZ-pid" >&2
  exit 2
fi
RELEASE_DIR="$REMOTE_ROOT/releases/$RELEASE_STAMP"
BACKUP_DIR="$REMOTE_ROOT/backups/$RELEASE_STAMP"
HEALTH_URL="$PUBLIC_BASE/api/actuator/health"
FRONTEND_NEXT="/usr/share/nginx/html.next-$RELEASE_STAMP"
FRONTEND_PREVIOUS="/usr/share/nginx/html.previous-$RELEASE_STAMP"
BACKEND_TOUCHED=0
FRONTEND_TOUCHED=0
ENV_TOUCHED=0

fail() { echo "认证版本发布检查失败：$1" >&2; return 1; }
container_id() { docker inspect --format '{{.Id}}' "$1"; }
check_existing_stack() {
  for name in lingdian-backend lingdian-frontend lingdian-nginx lingdian-mysql lingdian-redis lingdian-mir-stream lingdian-hj666; do
    [[ "$(docker inspect --format '{{.State.Running}}' "$name")" == true ]] || fail "$name 未运行"
  done
  [[ "$(docker inspect --format '{{.Config.Image}}' "$BACKEND")" == "aiplatform-backend:latest" ]] || fail "backend 镜像与既有 aiplatform-backend:latest 不一致"
  docker image inspect aiplatform-backend:latest >/dev/null
  docker exec "$BACKEND" test -s /app/app.jar
  docker exec "$FRONTEND" test -s /usr/share/nginx/html/index.html
  [[ "$(curl --silent --show-error --fail --max-time 20 "$HEALTH_URL" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status", ""))')" == UP ]] || fail "公网 backend health 未返回 UP"
  db_tables="$(docker exec lingdian-mysql sh -lc 'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -e "SELECT COUNT(DISTINCT TABLE_NAME) FROM information_schema.tables WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (\"user_oauth_identity\",\"user_contact_verification\");" 2>/dev/null')"
  [[ "$db_tables" == 2 ]] || fail "OAuth / 邮箱验证数据库表缺失，未执行任何迁移"
}
verify_other_containers_unchanged() {
  python3 - "$BACKUP_DIR/container-state.tsv" <<'PY'
import subprocess,sys
for line in open(sys.argv[1], encoding="utf-8"):
    name,old_id,old_started=line.rstrip("\n").split("\t")
    if name=="lingdian-backend": continue
    now_id=subprocess.check_output(["docker","inspect","--format","{{.Id}}",name],text=True).strip()
    now_started=subprocess.check_output(["docker","inspect","--format","{{.State.StartedAt}}",name],text=True).strip()
    assert now_id==old_id, f"{name} container id changed"
    assert now_started==old_started, f"{name} restarted during release"
PY
}
verify_routes() {
  curl --silent --show-error --fail --max-time 20 "$PUBLIC_BASE/api/auth/login/captcha" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; assert d.get("captchaId") and d.get("image")'
  curl --silent --show-error --fail --max-time 20 "$PUBLIC_BASE/api/auth/register/captcha" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; assert d.get("captchaId") and d.get("image")'
  curl --silent --show-error --fail --max-time 20 "$PUBLIC_BASE/api/auth/register/config" \
    | python3 -c 'import json,sys; p=json.load(sys.stdin); assert str(p.get("code")) in {"0","200"} and (p.get("data") or {}).get("emailVerificationRequired") is True'
  curl --silent --show-error --fail --max-time 20 "$PUBLIC_BASE/api/auth/oauth/providers" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; assert d.get("qq") is True and d.get("qqReviewStatus")=="pending"'
  curl --silent --show-error --fail --max-time 20 \
    "$PUBLIC_BASE/api/auth/oauth/url?provider=qq&purpose=login" \
    | python3 -c 'import json,sys; from urllib.parse import urlparse,parse_qs; p=json.load(sys.stdin); u=urlparse((p.get("data") or {}).get("url", "")); q=parse_qs(u.query); assert str(p.get("code")) in {"0","200"} and u.netloc=="graph.qq.com" and u.path=="/oauth2.0/authorize" and q.get("client_id")==["1905883463"] and q.get("redirect_uri")==["https://lingdian-ai.top/oauth/callback"] and q.get("state")'
  body="$(mktemp)"
  status="$(curl --silent --show-error --max-time 20 -o "$body" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -d '{}' "$PUBLIC_BASE/api/auth/register/email-code")"
  [[ "$status" == 200 ]] || { rm -f "$body"; fail "邮箱验证码接口 HTTP $status（空请求应由业务校验拒绝）"; }
  python3 - "$body" <<'PY'
import json,sys
p=json.load(open(sys.argv[1], encoding="utf-8"))
assert str(p.get("code")) not in {"404","500"}, "email-code route missing or backend error"
assert "验证码" in p.get("message", ""), "email-code route did not reach request validation"
PY
  rm -f "$body"
}
verify_web_assets() {
  body="$(mktemp)"
  code="$(curl --silent --show-error --max-time 20 -o "$body" -w '%{http_code}' "$PUBLIC_BASE/login")"
  [[ "$code" == 200 ]] || { rm -f "$body"; fail "登录页返回 HTTP $code"; }
  assets="$(python3 - "$body" <<'PY'
import html.parser,sys
class Assets(html.parser.HTMLParser):
    def __init__(self): super().__init__(); self.paths=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs); value=a.get("src") if tag=="script" else a.get("href") if tag=="link" else None
        if value and value.startswith("/"): self.paths.append(value)
p=Assets(); p.feed(open(sys.argv[1],encoding="utf-8").read()); print("\n".join(p.paths))
PY
  )"
  rm -f "$body"
  [[ -n "$assets" ]] || fail "登录页未引用静态资源"
  while IFS= read -r asset; do
    [[ -n "$asset" ]] || continue
    code="$(curl --silent --show-error --max-time 20 -o /dev/null -w '%{http_code}' "$PUBLIC_BASE$asset")"
    [[ "$code" == 200 ]] || fail "静态资源返回 HTTP $code"
  done <<< "$assets"
  curl --silent --show-error --fail --max-time 20 -o /dev/null "$PUBLIC_BASE/privacy"
  curl --silent --show-error --fail --max-time 20 -o /dev/null "$PUBLIC_BASE/terms"
}
rollback_on_error() {
  status="$1"
  trap - ERR
  if (( status == 0 )); then return; fi
  if [[ "$MODE" == "--check" ]]; then
    echo "只读检查未通过；未修改线上服务。" >&2
    exit "$status"
  fi
  echo "发布未通过，正在从 $BACKUP_DIR 回滚。" >&2
  set +e
  if (( FRONTEND_TOUCHED == 1 )) && [[ -s "$BACKUP_DIR/frontend/index.html" ]]; then
    docker exec "$FRONTEND" sh -eu -c "rm -rf /usr/share/nginx/html '$FRONTEND_NEXT' '$FRONTEND_PREVIOUS'; mkdir -p /usr/share/nginx/html"
    docker cp "$BACKUP_DIR/frontend/." "$FRONTEND:/usr/share/nginx/html/"
  fi
  if (( ENV_TOUCHED == 1 )) && [[ -s "$BACKUP_DIR/.env.prod" ]]; then
    cp -p "$BACKUP_DIR/.env.prod" "$REMOTE_ROOT/.env.prod"
    chmod 600 "$REMOTE_ROOT/.env.prod"
  fi
  if (( BACKEND_TOUCHED == 1 )) && [[ -s "$BACKUP_DIR/backend.jar" ]]; then
    (cd "$REMOTE_ROOT" && docker compose --env-file .env.prod -f docker-compose.server.yml \
      up -d --no-deps --no-build --pull never --force-recreate backend)
    for attempt in {1..30}; do
      [[ "$(docker inspect --format '{{.State.Running}}' "$BACKEND" 2>/dev/null || true)" == true ]] && break
      sleep 2
    done
    docker cp "$BACKUP_DIR/backend.jar" "$BACKEND:/app/app.jar.restore"
    docker exec "$BACKEND" sh -eu -c 'mv -f /app/app.jar.restore /app/app.jar'
    docker restart -t 30 "$BACKEND" >/dev/null
  fi
  echo "若自动回滚未通过，备份仍保留在 $BACKUP_DIR。" >&2
  exit "$status"
}
trap 'rollback_on_error $?' ERR

if [[ "$MODE" == "--check" ]]; then
  cd "$REMOTE_ROOT"
  check_existing_stack
  for key in SPRING_MAIL_PASSWORD VERIFICATION_DELIVERY_MODE REGISTRATION_EMAIL_VERIFICATION_REQUIRED QQ_OAUTH_APP_KEY QQ_OAUTH_REVIEW_STATUS; do
    grep -q "^[[:space:]]*$key:" docker-compose.server.yml || fail "Compose 缺少 $key 映射"
  done
  echo "只读检查通过：现有服务健康、目标镜像已存在、认证配置映射齐全。"
  exit 0
fi

[[ -s "$RELEASE_DIR/backend.jar" && -s "$RELEASE_DIR/frontend.tar.gz" && -s "$RELEASE_DIR/SHA256SUMS" ]] || fail "发布产物不完整"
(cd "$RELEASE_DIR" && sha256sum --check --status SHA256SUMS) || fail "发布产物校验失败"
cd "$REMOTE_ROOT"
check_existing_stack

mkdir -m 700 -p "$BACKUP_DIR" "$BACKUP_DIR/frontend"
chmod 700 "$BACKUP_DIR"
cp -p "$REMOTE_ROOT/.env.prod" "$BACKUP_DIR/.env.prod"
chmod 600 "$BACKUP_DIR/.env.prod"
docker cp "$BACKEND:/app/app.jar" "$BACKUP_DIR/backend.jar"
chmod 600 "$BACKUP_DIR/backend.jar"
docker cp "$FRONTEND:/usr/share/nginx/html/." "$BACKUP_DIR/frontend/"
[[ -s "$BACKUP_DIR/backend.jar" && -s "$BACKUP_DIR/frontend/index.html" ]] || fail "当前版本备份不完整"
: > "$BACKUP_DIR/container-state.tsv"
for name in lingdian-backend lingdian-frontend lingdian-nginx lingdian-mysql lingdian-redis lingdian-mir-stream lingdian-hj666; do
  printf '%s\t%s\t%s\n' "$name" "$(container_id "$name")" "$(docker inspect --format '{{.State.StartedAt}}' "$name")" >> "$BACKUP_DIR/container-state.tsv"
done

IFS= read -r -d '' QQ_APP_ID
IFS= read -r -d '' QQ_APP_KEY
IFS= read -r -d '' SMTP_PASSWORD
[[ "$QQ_APP_ID" == "1905883463" && -n "$QQ_APP_KEY" && -n "$SMTP_PASSWORD" ]] || fail "认证配置缺少必需值"

ENV_TOUCHED=1
AUTH_RELEASE_PAYLOAD="$(QQ_APP_ID="$QQ_APP_ID" QQ_APP_KEY="$QQ_APP_KEY" SMTP_PASSWORD="$SMTP_PASSWORD" python3 - <<'PY'
import json,os
print(json.dumps({
  "QQ_OAUTH_APP_ID": os.environ["QQ_APP_ID"],
  "QQ_OAUTH_APP_KEY": os.environ["QQ_APP_KEY"],
  "QQ_OAUTH_REDIRECT_URI": "https://lingdian-ai.top/oauth/callback",
  "QQ_OAUTH_REVIEW_STATUS": "pending",
  "SPRING_MAIL_HOST": "smtpdm.aliyun.com",
  "SPRING_MAIL_PORT": "465",
  "SPRING_MAIL_USERNAME": "noreply@notify.lingdian-ai.top",
  "SPRING_MAIL_PASSWORD": os.environ["SMTP_PASSWORD"],
  "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH": "true",
  "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE": "false",
  "SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE": "true",
  "VERIFICATION_DELIVERY_MODE": "external",
  "VERIFICATION_ALLOW_LOCAL_CAPTURE": "false",
  "REGISTRATION_EMAIL_VERIFICATION_REQUIRED": "true",
  "VERIFICATION_EMAIL_FROM": "noreply@notify.lingdian-ai.top"
}))
PY
)" python3 - "$REMOTE_ROOT/.env.prod" <<'PY'
import json,os,re,sys,tempfile
from pathlib import Path
path=Path(sys.argv[1]); values=json.loads(os.environ["AUTH_RELEASE_PAYLOAD"])
text=path.read_text(encoding="utf-8")
for key,value in values.items():
    line=f"{key}={value}"
    pattern=re.compile(rf"(?m)^{re.escape(key)}=.*$")
    text,n=pattern.subn(lambda _: line,text)
    if not n: text=text.rstrip()+"\n"+line+"\n"
fd,tmp=tempfile.mkstemp(prefix=".env.prod.auth.",dir=path.parent)
try:
    with os.fdopen(fd,"w",encoding="utf-8") as f: f.write(text); f.flush(); os.fsync(f.fileno())
    os.chmod(tmp,0o600); os.replace(tmp,path)
finally:
    if os.path.exists(tmp): os.unlink(tmp)
PY
unset AUTH_RELEASE_PAYLOAD QQ_APP_KEY SMTP_PASSWORD
for key in QQ_OAUTH_APP_ID QQ_OAUTH_APP_KEY QQ_OAUTH_REDIRECT_URI QQ_OAUTH_REVIEW_STATUS SPRING_MAIL_HOST SPRING_MAIL_PORT SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD VERIFICATION_DELIVERY_MODE REGISTRATION_EMAIL_VERIFICATION_REQUIRED VERIFICATION_EMAIL_FROM; do
  grep -Eq "^$key=.+$" "$REMOTE_ROOT/.env.prod" || fail "受控环境配置缺少 $key"
done
[[ "$(stat -c '%a' "$REMOTE_ROOT/.env.prod")" == 600 ]] || chmod 600 "$REMOTE_ROOT/.env.prod"

BACKEND_TOUCHED=1
docker compose --env-file .env.prod -f docker-compose.server.yml \
  up -d --no-deps --no-build --pull never --force-recreate backend
[[ "$(docker inspect --format '{{.Config.Image}}' "$BACKEND")" == "aiplatform-backend:latest" ]] || fail "backend 没有复用既有镜像"
for attempt in {1..30}; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$BACKEND")" == true ]]; then break; fi
  (( attempt < 30 )) || fail "backend 重建后未运行"
  sleep 2
done

docker cp "$RELEASE_DIR/backend.jar" "$BACKEND:/app/app.jar.next"
docker exec "$FRONTEND" rm -rf "$FRONTEND_NEXT"
docker exec "$FRONTEND" mkdir -p "$FRONTEND_NEXT"
docker cp "$RELEASE_DIR/frontend.tar.gz" "$FRONTEND:/tmp/frontend-$RELEASE_STAMP.tar.gz"
docker exec "$FRONTEND" tar -xzf "/tmp/frontend-$RELEASE_STAMP.tar.gz" -C "$FRONTEND_NEXT"
docker exec "$FRONTEND" rm -f "/tmp/frontend-$RELEASE_STAMP.tar.gz"
docker exec "$FRONTEND" test -s "$FRONTEND_NEXT/index.html"

docker exec "$BACKEND" sh -eu -c 'mv -f /app/app.jar.next /app/app.jar'
docker restart -t 30 "$BACKEND" >/dev/null
for attempt in {1..45}; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$BACKEND")" == true ]] \
    && curl --silent --show-error --fail --max-time 10 "$HEALTH_URL" \
      | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status")=="UP" else 1)'; then break; fi
  (( attempt < 45 )) || fail "新版 backend health 等待超时"
  sleep 2
done

FRONTEND_TOUCHED=1
docker exec "$FRONTEND" sh -eu -c \
  "mv /usr/share/nginx/html '$FRONTEND_PREVIOUS'; mv '$FRONTEND_NEXT' /usr/share/nginx/html"
check_existing_stack
verify_routes
verify_web_assets
verify_other_containers_unchanged

docker exec "$FRONTEND" rm -rf "$FRONTEND_PREVIOUS"
FRONTEND_TOUCHED=0
BACKEND_TOUCHED=0
ENV_TOUCHED=0
trap - ERR
echo "线上认证验收通过；QQ OAuth ready（腾讯审核状态 pending）；邮箱验证码已启用。"
echo "仅重建 backend 服务以加载环境变量并复用既有镜像；其他容器未重启。回滚备份：$BACKUP_DIR。"
