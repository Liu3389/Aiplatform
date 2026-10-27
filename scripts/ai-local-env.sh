#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME="$ROOT/.runtime"
ENV_FILE="$RUNTIME/local.env"
COMPOSE_FILE="$ROOT/docker-compose.local.yml"
PROJECT="aiplatform-local"
MYSQL_CONTAINER="aiplatform-local-mysql"
REDIS_CONTAINER="aiplatform-local-redis"
BACKEND_JAR="$ROOT/Aiplatform-demo/target/ai-gateway-platform.jar"
WEB_DIR="$ROOT/Aiplatform-web"
JAVA_BIN="$(command -v java || true)"
NODE_BIN="$(command -v node || true)"
LOCAL_TARGET="http://127.0.0.1:18080"
ONLINE_TARGET="https://lingdian-ai.top"
WEB_PORT="13000"
DEEPSEEK_FALLBACK_TEST_ACTIVE=false
DEEPSEEK_FALLBACK_ORIGINAL_API_KEY_SET=false
DEEPSEEK_FALLBACK_ORIGINAL_API_KEY=""
DEEPSEEK_FALLBACK_ORIGINAL_MODEL_STATUSES=""

mkdir -p "$RUNTIME/logs"

usage() {
  cat <<'EOF'
AI 平台本地环境

用法:
  ./scripts/ai-local-env.sh up                 启动隔离的 MySQL、Redis、后端和前端
  ./scripts/ai-local-env.sh switch local       前端代理切到本地后端，并启动本地栈
  ./scripts/ai-local-env.sh switch online      前端代理切到线上 API（本命令不会部署）
  ./scripts/ai-local-env.sh status              查看本地容器、进程和代理模式
  ./scripts/ai-local-env.sh verify              验证数据库、Redis、后端 API、认证边界和前端代理
  ./scripts/ai-local-env.sh verify-chat         通过本地模拟模型验证注册、登录、聊天、SSE 和结算
  ./scripts/ai-local-env.sh stop-backend        仅停止本脚本管理的后端进程，保留其他本地服务
  ./scripts/ai-local-env.sh start-backend       仅启动/恢复本地后端，不切换前端代理
  AI_VERIFY_DEEPSEEK_FALLBACK=true ./scripts/ai-local-env.sh verify-chat
                                               额外以合成 Key 验收 DeepSeek 环境回退和本地 Responses mock
  ./scripts/ai-local-env.sh logs [backend|web|mock]
  ./scripts/ai-local-env.sh down                停止本脚本管理的服务，保留本地数据卷

首次启动会在 .runtime/local.env 写入仅供本地测试的配置。此文件由仓库 .gitignore 忽略。
本地端口：前端 13000，后端 18080，MySQL 13306，Redis 16380，模拟模型 19090。
EOF
}

ensure_local_env() {
  if [[ -f "$ENV_FILE" ]]; then
    return
  fi
  umask 077
  cat > "$ENV_FILE" <<EOF
# 本地隔离栈配置。此文件应保持在 Git 忽略目录 .runtime/ 中。
MYSQL_HOST=127.0.0.1
MYSQL_PORT=13306
MYSQL_DATABASE=ai_gateway_local
MYSQL_USERNAME=root
MYSQL_PASSWORD=local-only-aiplatform-db-2026
REDIS_HOST=127.0.0.1
REDIS_PORT=16380
REDIS_PASSWORD=
API_ENCRYPTION_KEY=local-only-aiplatform-encryption-key-2026
OPENAI_API_KEY=sk-local-placeholder
RABBITMQ_ENABLED=false
SEARXNG_ENABLED=false
JINA_ENABLED=false
TAVILY_ENABLED=false
APP_STORAGE_PREFER_LOCAL=true
APP_STORAGE_LOCAL_PATH='$RUNTIME/uploads'
APP_POINTS_DAILY_GRANT_CRON=-
ALLOWED_ORIGINS=http://127.0.0.1:13000,http://localhost:13000
VERIFICATION_DELIVERY_MODE=local
VERIFICATION_ALLOW_LOCAL_CAPTURE=true
EOF
  chmod 600 "$ENV_FILE"
  echo "已生成仅本地使用的配置：$ENV_FILE"
}

load_local_env() {
  ensure_local_env
  # local.env 由本脚本生成，且只存放于被忽略的 .runtime/ 目录。
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  : "${VERIFICATION_DELIVERY_MODE:=local}"
  : "${VERIFICATION_ALLOW_LOCAL_CAPTURE:=true}"
  : "${REGISTRATION_EMAIL_VERIFICATION_REQUIRED:=true}"
  # Daily grants run at midnight in normal environments; disabling them here keeps
  # long-running local acceptance checks deterministic. Set a cron in local.env to test it.
  : "${APP_POINTS_DAILY_GRANT_CRON:=-}"
  export VERIFICATION_DELIVERY_MODE VERIFICATION_ALLOW_LOCAL_CAPTURE REGISTRATION_EMAIL_VERIFICATION_REQUIRED APP_POINTS_DAILY_GRANT_CRON
}

compose() {
  docker compose --project-name "$PROJECT" --file "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

process_matches() {
  local pid="$1" marker="$2" command_line
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  command_line="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  [[ "$command_line" == *"$marker"* ]]
}

stop_managed_process() {
  local name="$1" marker="$2" pid_file="$RUNTIME/$1.pid" pid
  [[ -f "$pid_file" ]] || return 0
  pid="$(cat "$pid_file")"
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$pid_file"
    return 0
  fi
  if ! process_matches "$pid" "$marker"; then
    echo "拒绝停止 $name：PID $pid 正在运行，但命令行不匹配；PID 文件保留在 $pid_file" >&2
    return 1
  fi
  kill -TERM "$pid"
  for _ in {1..20}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$pid_file"
      return 0
    fi
    sleep 1
  done
  if process_matches "$pid" "$marker"; then
    echo "$name (PID $pid) 未响应终止信号，正在停止已核验的本地进程。" >&2
    kill -KILL "$pid"
    for _ in {1..5}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$pid_file"
        return 0
      fi
      sleep 1
    done
  fi
  echo "$name (PID $pid) 未能退出；PID 文件保留在 $pid_file" >&2
  return 1
}

check_docker() {
  command -v docker >/dev/null 2>&1 || { echo "未找到 Docker CLI。" >&2; return 1; }
  docker info >/dev/null 2>&1 || { echo "Docker 未运行，请先启动 Docker Desktop。" >&2; return 1; }
}

seed_user_rate_window() {
  local user_id="$1" count="${2:-60}" key score i
  local members=()
  key="rate_limit:user:$user_id"
  score="$(python3 -c 'import time; print(int(time.time() * 1000))')"
  docker exec "$REDIS_CONTAINER" redis-cli DEL "$key" >/dev/null
  for ((i = 1; i <= count; i++)); do
    members+=("$score" "local-seed-$user_id-$i-$$-$RANDOM")
  done
  if (( count > 0 )); then
    docker exec "$REDIS_CONTAINER" redis-cli ZADD "$key" "${members[@]}" >/dev/null
    docker exec "$REDIS_CONTAINER" redis-cli EXPIRE "$key" 65 >/dev/null
  fi
}

backend_build_needed() {
  local source_file
  if [[ ! -f "$BACKEND_JAR" || "$ROOT/Aiplatform-demo/pom.xml" -nt "$BACKEND_JAR" ]]; then
    return 0
  fi
  while IFS= read -r source_file; do
    [[ "$source_file" -nt "$BACKEND_JAR" ]] && return 0
  done < <(find "$ROOT/Aiplatform-demo/src" -type f -print)
  return 1
}

build_backend_if_stale() {
  if backend_build_needed; then
    echo "后端源码比本地 JAR 新，正在构建（不执行测试）……"
    (cd "$ROOT/Aiplatform-demo" && mvn -q -DskipTests package)
  fi
  [[ -f "$BACKEND_JAR" ]] || { echo "后端 JAR 未生成：$BACKEND_JAR" >&2; return 1; }
}

wait_http() {
  local url="$1" label="$2" attempts="${3:-90}"
  for _ in $(seq 1 "$attempts"); do
    if curl --silent --show-error --fail --max-time 2 "$url" -o /dev/null 2>/dev/null; then
      echo "$label 就绪：$url"
      return 0
    fi
    sleep 1
  done
  echo "$label 未就绪：$url" >&2
  return 1
}

wait_container_healthy() {
  local container="$1" attempts="${2:-90}" state
  for _ in $(seq 1 "$attempts"); do
    state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$state" == healthy ]]; then
      echo "$container 健康检查通过。"
      return 0
    fi
    if [[ "$state" == unhealthy || "$state" == exited || "$state" == dead ]]; then
      docker logs --tail 80 "$container" >&2 || true
      echo "$container 状态异常：$state" >&2
      return 1
    fi
    sleep 1
  done
  docker logs --tail 80 "$container" >&2 || true
  echo "$container 等待健康检查超时，当前状态：${state:-unknown}" >&2
  return 1
}

start_backend() {
  local marker="$BACKEND_JAR" pid=""
  if [[ -f "$RUNTIME/backend.pid" ]]; then
    pid="$(cat "$RUNTIME/backend.pid")"
    if process_matches "$pid" "$marker"; then
      if ! backend_build_needed && [[ ! "$BACKEND_JAR" -nt "$RUNTIME/backend.pid" ]]; then
        echo "本地后端已运行（PID ${pid}）。"
        return 0
      fi
      echo "后端源码或 JAR 已更新，先停止旧进程再构建/重启……"
    fi
    stop_managed_process backend "$marker"
  fi
  build_backend_if_stale
  mkdir -p "$RUNTIME/uploads"
  load_local_env
  if [[ "${AI_VERIFY_DEEPSEEK_FALLBACK:-false}" == "true" ]]; then
    # 仅测试开关启用时使用一次性合成值，禁止误用真实 DeepSeek Key 访问供应商。
    DEEPSEEK_API_KEY="local-only-fallback-test-key"
    export DEEPSEEK_API_KEY
  fi
  python3 "$ROOT/scripts/launch-local-process.py" \
    "$RUNTIME/backend.pid" "$RUNTIME/logs/backend.log" "$ROOT" -- \
    "$JAVA_BIN" -jar "$BACKEND_JAR" \
    --server.port=18080 \
    --logging.file.name="$RUNTIME/logs/backend-app.log"
  wait_http "$LOCAL_TARGET/api/platform-model/list" "本地后端" 120 || {
    tail -n 100 "$RUNTIME/logs/backend.log" >&2 || true
    return 1
  }
}

start_web() {
  local target="$1" marker="$WEB_DIR/node_modules/vite/bin/vite.js" pid=""
  stop_managed_process web "$marker"
  if [[ ! -x "$WEB_DIR/node_modules/.bin/vite" && ! -f "$marker" ]]; then
    echo "未找到 Vite 依赖，请在 Aiplatform-web 执行 npm install。" >&2
    return 1
  fi
  AI_API_TARGET="$target" AI_WEB_PORT="$WEB_PORT" \
    python3 "$ROOT/scripts/launch-local-process.py" \
      "$RUNTIME/web.pid" "$RUNTIME/logs/web.log" "$WEB_DIR" -- \
      "$NODE_BIN" "$marker" --host 127.0.0.1 --port "$WEB_PORT" --strictPort
  printf '%s\n' "$target" > "$RUNTIME/api-target"
  wait_http "http://127.0.0.1:$WEB_PORT/" "本地前端" 45 || {
    tail -n 80 "$RUNTIME/logs/web.log" >&2 || true
    return 1
  }
}

start_mock() {
  local marker="$ROOT/scripts/local-openai-mock.mjs" pid=""
  if [[ -f "$RUNTIME/mock.pid" ]]; then
    pid="$(cat "$RUNTIME/mock.pid")"
    if process_matches "$pid" "$marker"; then
      echo "本地模拟模型已运行（PID ${pid}）。"
      return 0
    fi
    stop_managed_process mock "$marker"
  fi
  python3 "$ROOT/scripts/launch-local-process.py" \
    "$RUNTIME/mock.pid" "$RUNTIME/logs/mock.log" "$ROOT" -- \
    "$NODE_BIN" "$marker"
  wait_http "http://127.0.0.1:19090/health" "本地模拟模型" 20 || {
    tail -n 80 "$RUNTIME/logs/mock.log" >&2 || true
    return 1
  }
}

up() {
  check_docker
  [[ -n "$JAVA_BIN" ]] || { echo "未找到 Java。" >&2; return 1; }
  [[ -n "$NODE_BIN" ]] || { echo "未找到 Node.js。" >&2; return 1; }
  ensure_local_env
  load_local_env
  mkdir -p "$RUNTIME/uploads" "$RUNTIME/logs"
  compose up -d mysql redis
  wait_container_healthy "$MYSQL_CONTAINER"
  wait_container_healthy "$REDIS_CONTAINER"
  apply_local_schema_migrations
  start_backend
  if [[ "$(mysql_query "SELECT COUNT(*) FROM platform_model_config WHERE display_name='Local Mock (Codex)'")" -gt 0 ]]; then
    start_mock
  fi
  start_web "$LOCAL_TARGET"
  printf 'local\n' > "$RUNTIME/mode"
  echo "本地环境已启动：前端 http://127.0.0.1:$WEB_PORT ，后端 $LOCAL_TARGET/api"
}

mysql_query() {
  local query="$1"
  docker exec "$MYSQL_CONTAINER" sh -c \
    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names "$1" -e "$2"' \
    sh "$MYSQL_DATABASE" "$query"
}

cleanup_local_deepseek_fallback_test() {
  [[ "$DEEPSEEK_FALLBACK_TEST_ACTIVE" == "true" ]] || return 0
  local cleanup_failed=0 backend_stopped=false model_id model_status
  set +e
  if stop_managed_process backend "$BACKEND_JAR"; then
    backend_stopped=true
  else
    cleanup_failed=1
  fi
  mysql_query "DELETE FROM platform_model_config WHERE display_name='Local DeepSeek Fallback (Codex)';" || cleanup_failed=1
  while IFS=$'\t' read -r model_id model_status; do
    [[ "$model_id" =~ ^[0-9]+$ && "$model_status" =~ ^[0-9]+$ ]] || continue
    mysql_query "UPDATE platform_model_config SET status=$model_status WHERE id=$model_id;" || cleanup_failed=1
  done <<< "$DEEPSEEK_FALLBACK_ORIGINAL_MODEL_STATUSES"

  unset AI_VERIFY_DEEPSEEK_FALLBACK
  if [[ "$DEEPSEEK_FALLBACK_ORIGINAL_API_KEY_SET" == "true" ]]; then
    DEEPSEEK_API_KEY="$DEEPSEEK_FALLBACK_ORIGINAL_API_KEY"
    export DEEPSEEK_API_KEY
  else
    unset DEEPSEEK_API_KEY
  fi
  if [[ "$backend_stopped" == "true" && "$cleanup_failed" == "0" ]]; then
    start_backend || cleanup_failed=1
  fi
  set -e
  if [[ "$cleanup_failed" == "0" ]]; then
    DEEPSEEK_FALLBACK_TEST_ACTIVE=false
    DEEPSEEK_FALLBACK_ORIGINAL_API_KEY=""
    DEEPSEEK_FALLBACK_ORIGINAL_MODEL_STATUSES=""
    trap - EXIT
    echo "DeepSeek 本地回退测试数据已清理，后端已按原环境恢复。"
    return 0
  fi
  echo "DeepSeek 本地回退测试清理未完成；请检查本地平台模型状态和后端进程。" >&2
  return 1
}

apply_local_schema_migrations() {
  local migration
  for migration in \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v8-points-settlement-version.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v9-ai-charge-intent.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v10-points-temporary-lots.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v11-user-two-factor.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v12-oauth-identities.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v13-user-verified-contacts.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v14-file-parse-recovery.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v15-chat-message-idempotency.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v16-model-output-token-cap.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v17-ai-charge-review-audit.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v18-oauth-avatar.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v19-user-avatar.sql" \
    "$ROOT/Aiplatform-demo/src/main/resources/sql/migration-v20-user-nickname.sql"; do
  docker exec -i "$MYSQL_CONTAINER" sh -c \
    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$1"' \
    sh "$MYSQL_DATABASE" < "$migration" >/dev/null
  done
}

verify_json_models() {
  local body="$1" label="$2"
  MODEL_BODY="$body" python3 - "$label" <<'PY'
import json, os, sys
label = sys.argv[1]
payload = json.loads(os.environ["MODEL_BODY"])
assert payload.get("code") == 200, f"{label}: API code={payload.get('code')}"
assert isinstance(payload.get("data"), list), f"{label}: data 不是列表"
assert payload["data"], f"{label}: 没有启用模型"
assert all("apiKey" not in model and "api_key" not in model for model in payload["data"]), f"{label}: 响应泄露 API Key 字段"
assert all(type(model.get("chatEnabled")) is bool for model in payload["data"]), f"{label}: 缺少布尔型 chatEnabled 状态"
connected = sum(model["chatEnabled"] for model in payload["data"])
print(f"{label} 通过，启用模型：{len(payload['data'])}，可对话：{connected}，未接入：{len(payload['data']) - connected}")
PY
}

verify_mock_output_token_case() {
  local token="$1" test_case="$2" expected="$3" request_payload response stats
  request_payload="$(TOKEN_CASE="$test_case" python3 - <<'PY'
import json, os
payload = {
    "model": "Local Mock (Codex)",
    "messages": [{"role": "user", "content": "D3 输出上限本地接口验收"}],
    "stream": False,
    "disableSearch": True,
}
case = os.environ["TOKEN_CASE"]
if case != "omitted":
    payload["maxTokens"] = {"negative": -9, "over": 50000, "legal": 768}[case]
print(json.dumps(payload))
PY
  )"
  response="$(curl --silent --show-error --fail --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" \
    -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$request_payload")"
  RESPONSE_BODY="$response" python3 - <<'PY'
import json, os
payload = json.loads(os.environ["RESPONSE_BODY"])
assert payload.get("code") == 200, payload
assert "LOCAL_CHAT_OK" in payload.get("data", ""), payload
PY
  stats="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  EXPECTED_LIMIT="$expected" TEST_CASE="$test_case" MOCK_STATS="$stats" python3 - <<'PY'
import json, os
stats = json.loads(os.environ["MOCK_STATS"])
expected = int(os.environ["EXPECTED_LIMIT"])
assert stats.get("lastTokenLimitKey") == "max_tokens", (os.environ["TEST_CASE"], stats)
assert stats.get("lastTokenLimit") == expected, (os.environ["TEST_CASE"], stats, expected)
print(f"D3 {os.environ['TEST_CASE']} 请求通过：上游收到 max_tokens={expected}。")
PY
}

verify() {
  load_local_env
  check_docker
  wait_container_healthy "$MYSQL_CONTAINER" 2
  wait_container_healthy "$REDIS_CONTAINER" 2
  local backend_body proxy_body auth_code web_code temp_file health_file health_http proxy_health_file proxy_health_http
  health_file="$RUNTIME/logs/verify-actuator-health.json"
  health_http="$(curl --silent --show-error --max-time 10 -o "$health_file" -w '%{http_code}' "$LOCAL_TARGET/api/actuator/health" || true)"
  [[ "$health_http" == 200 ]] || { echo "后端 Actuator 健康接口预期 HTTP 200，实际：${health_http:-无响应}" >&2; return 1; }
  python3 - "$health_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)
assert payload.get("status") == "UP", payload.get("status")
print("后端 Actuator 健康检查通过：status=UP。")
PY
  backend_body="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/platform-model/list")"
  verify_json_models "$backend_body" "后端模型列表"
  temp_file="$RUNTIME/logs/verify-auth.json"
  auth_code="$(curl --silent --show-error --max-time 10 -o "$temp_file" -w '%{http_code}' \
    -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' \
    --data '{"model":"Local Mock (Codex)","messages":[{"role":"user","content":"auth smoke"}]}' || true)"
  [[ "$auth_code" == 200 ]] || { echo "未认证聊天请求按当前 API 协议预期 HTTP 200，实际：$auth_code" >&2; return 1; }
  python3 - "$temp_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)
assert payload.get("code") == 401, payload
print("聊天认证边界通过：缺少 Token/API Key 时返回业务码 401。")
PY
  web_code="$(curl --silent --show-error --max-time 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$WEB_PORT/" || true)"
  [[ "$web_code" == 200 ]] || { echo "前端首页预期 HTTP 200，实际：$web_code" >&2; return 1; }
  proxy_health_file="$RUNTIME/logs/verify-proxy-actuator-health.json"
  proxy_health_http="$(curl --silent --show-error --max-time 10 -o "$proxy_health_file" -w '%{http_code}' "http://127.0.0.1:$WEB_PORT/api/actuator/health" || true)"
  [[ "$proxy_health_http" == 200 ]] || { echo "前端代理 Actuator 健康接口预期 HTTP 200，实际：${proxy_health_http:-无响应}" >&2; return 1; }
  python3 - "$proxy_health_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)
assert payload.get("status") == "UP", payload.get("status")
print("前端代理 Actuator 健康检查通过：status=UP。")
PY
  proxy_body="$(curl --silent --show-error --fail --max-time 10 "http://127.0.0.1:$WEB_PORT/api/platform-model/list")"
  verify_json_models "$proxy_body" "前端代理模型列表"
  echo "本地基础验证全部通过。"
}

verify_chat() {
  load_local_env
  check_docker
  compose up -d mysql redis
  wait_container_healthy "$MYSQL_CONTAINER"
  wait_container_healthy "$REDIS_CONTAINER"
  apply_local_schema_migrations
  echo "正在准备本地模拟模型配置并刷新服务缓存……"
  mysql_query "DELETE FROM platform_model_config WHERE display_name='Local Mock (Codex)'; INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, description, base_url, api_key, supports_vision, max_output_tokens, status, sort_order, access_level) VALUES ('Local Mock (Codex)', 'local-chat-smoke', 1.000, 'openai', '仅用于本地接口验收', 'http://127.0.0.1:19090', 'sk-local-test', 1, 1536, 1, 0, 0);"
  local deepseek_fallback_test_model="Local DeepSeek Fallback (Codex)" expected_deepseek_fallback=""
  if [[ "${AI_VERIFY_DEEPSEEK_FALLBACK:-false}" == "true" ]]; then
    if [[ -n "${DEEPSEEK_API_KEY:-}" ]]; then
      DEEPSEEK_FALLBACK_ORIGINAL_API_KEY_SET=true
      DEEPSEEK_FALLBACK_ORIGINAL_API_KEY="$DEEPSEEK_API_KEY"
    fi
    DEEPSEEK_FALLBACK_ORIGINAL_MODEL_STATUSES="$(mysql_query "SELECT id,status FROM platform_model_config WHERE LOWER(provider)='deepseek' ORDER BY id;")"
    DEEPSEEK_FALLBACK_TEST_ACTIVE=true
    trap cleanup_local_deepseek_fallback_test EXIT
    mysql_query "UPDATE platform_model_config SET status=0 WHERE LOWER(provider)='deepseek'; DELETE FROM platform_model_config WHERE display_name='$deepseek_fallback_test_model'; INSERT INTO platform_model_config (display_name, actual_model, fixed_points, provider, description, base_url, api_key, supports_vision, max_output_tokens, status, sort_order, access_level) VALUES ('$deepseek_fallback_test_model', 'deepseek-chat', 1.000, 'deepseek', '仅用于本地环境凭据回退验收', 'http://127.0.0.1:19090/v1', '', 0, 1536, 1, 999, 0);"
  fi
  stop_managed_process backend "$BACKEND_JAR"
  stop_managed_process mock "$ROOT/scripts/local-openai-mock.mjs"
  up
  start_mock

  echo "本地模拟模型已在后端缓存预热前配置，Token 上限接口测试开始。"

  local captcha_response captcha_id captcha_code username register_payload register_response login_captcha_response login_captcha_id login_captcha_code bad_login_code bad_login_payload bad_login_response replay_login_payload replay_login_response login_payload login_response token user_id bind_email bind_phone bind_email_hash bind_phone_hash bind_email_code bind_phone_code bind_send_payload bind_send_response bind_response bind_state recharge_state_before recharge_state_after recharge_cache_before recharge_cache_after recharge_response recharge_coupon_response coupon_list_response coupon_use_response coupon_redeem_response path_sentinel path_payload path_response mock_stats_before mock_stats_after state_before state_after points_before points_after chat_payload chat_response conversation_id owned_chat_payload owned_chat_response other_username other_user_id other_register_captcha_response other_register_captcha_id other_register_captcha_code other_register_payload other_register_response other_login_captcha_response other_login_captcha_id other_login_captcha_code other_login_payload other_login_response other_token idor_chat_payload idor_chat_response idor_stream_response idor_state_before idor_state_after other_points_before other_points_after rate_limited_payload rate_http_code rate_limited_response rate_state_before rate_state_after rate_points_before rate_points_after other_chat_payload other_chat_response admin_cache_url admin_unauth_read admin_unauth_init admin_user_read admin_user_init admin_user_clear admin_init_response admin_check_response admin_init_all_response admin_clear_response admin_audit_count user_sse_rate_count api_key_payload api_key_response api_key api_key_id stream_payload stream_response api_stream_response rate_count mock_stats counts revoke_response revoked_info_response revoked_chat_response revoked_stream_response
  local register_email register_email_hash register_email_code email_code_payload email_code_response other_register_email other_register_email_hash other_register_email_code other_email_code_payload other_email_code_response
  local d2_key d2_request_id d2_payload d2_before_state d2_before_points d2_mock_before d2_first_response d2_first_state d2_first_points d2_second_response d2_second_state d2_second_points d2_request_rows d2_mock_after d2_stream_key d2_stream_request_id d2_stream_payload d2_stream_before_state d2_stream_before_points d2_stream_mock_before d2_stream_first d2_stream_first_state d2_stream_first_points d2_stream_second d2_stream_second_state d2_stream_second_points d2_stream_rows d2_stream_mock_after d2_failure_key d2_failure_request_id d2_failure_payload d2_failure_before_state d2_failure_before_points d2_failure_response d2_failure_curl_status d2_failure_after_state d2_failure_after_points d2_failure_rows
  local available_models_response unconfigured_model unconfigured_payload unconfigured_response unconfigured_state_before unconfigured_state_after unconfigured_points_before unconfigured_points_after unconfigured_mock_before unconfigured_mock_after
  local deepseek_fallback_payload deepseek_fallback_response deepseek_fallback_stream_payload deepseek_fallback_stream_response deepseek_fallback_state_before deepseek_fallback_state_after deepseek_fallback_points_before deepseek_fallback_points_after deepseek_fallback_mock_before deepseek_fallback_mock_after
  captcha_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/auth/register/captcha")"
  captcha_id="$(printf '%s' "$captcha_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["captchaId"])')"
  captcha_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:captcha:register:$captcha_id")"
  [[ -n "$captcha_code" && "$captcha_code" != nil ]] || { echo "无法从专用本地 Redis 取回测试验证码。" >&2; return 1; }
  username="local_smoke_$(date +%s)_${RANDOM}"
  register_email="${username}@example.test"
  email_code_payload="$(EMAIL="$register_email" CAPTCHA_ID="$captcha_id" CAPTCHA_CODE="$captcha_code" python3 -c 'import json,os; print(json.dumps({"email":os.environ["EMAIL"],"captchaId":os.environ["CAPTCHA_ID"],"captchaCode":os.environ["CAPTCHA_CODE"]}))')"
  email_code_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$LOCAL_TARGET/api/auth/register/email-code" -H 'Content-Type: application/json' --data-binary "$email_code_payload")"
  printf '%s' "$email_code_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message")'
  register_email_hash="$(EMAIL="$register_email" python3 -c 'import hashlib,os; print(hashlib.sha256(os.environ["EMAIL"].lower().encode()).hexdigest())')"
  register_email_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "verification:local:registration:email:$register_email_hash")"
  [[ -n "$register_email_code" && "$register_email_code" != nil ]] || { echo "本地注册邮箱验证码投递失败。" >&2; return 1; }
  register_payload="$(EMAIL="$register_email" EMAIL_CODE="$register_email_code" USERNAME="$username" python3 -c 'import json,os; print(json.dumps({"username":os.environ["USERNAME"],"password":"LocalSmoke123","email":os.environ["EMAIL"],"emailCode":os.environ["EMAIL_CODE"]}))')"
  register_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$LOCAL_TARGET/api/auth/register" -H 'Content-Type: application/json' --data-binary "$register_payload")"
  user_id="$(printf '%s' "$register_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message"); print(p["data"]["id"])')"
  [[ "$user_id" =~ ^[0-9]+$ ]] || { echo "本地测试注册未返回有效用户 ID。" >&2; return 1; }

  # 只对本地新注册用户补充测试点数，API 不提供充值或跳过计费的通道。
  mysql_query "UPDATE user SET points=100, points_version=points_version+1 WHERE id=$user_id;"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "100.000" >/dev/null
  login_captcha_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/auth/login/captcha")"
  login_captcha_id="$(printf '%s' "$login_captcha_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["captchaId"])')"
  login_captcha_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:captcha:login:$login_captcha_id")"
  [[ -n "$login_captcha_code" && "$login_captcha_code" != nil ]] || { echo "无法从专用本地 Redis 取回登录测试验证码。" >&2; return 1; }
  bad_login_code="00000"
  [[ "$bad_login_code" != "$login_captcha_code" ]] || bad_login_code="11111"
  bad_login_payload="$(LOGIN_CAPTCHA_ID="$login_captcha_id" LOGIN_CAPTCHA_CODE="$bad_login_code" USERNAME="$username" python3 -c 'import json,os; print(json.dumps({"username":os.environ["USERNAME"],"password":"LocalSmoke123","captchaId":os.environ["LOGIN_CAPTCHA_ID"],"captchaCode":os.environ["LOGIN_CAPTCHA_CODE"]}))')"
  bad_login_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/auth/login" -H 'Content-Type: application/json' --data-binary "$bad_login_payload")"
  printf '%s' "$bad_login_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==400,p; print("错误登录验证码按预期拒绝，未进入账号密码校验。")'
  replay_login_payload="$(LOGIN_CAPTCHA_ID="$login_captcha_id" LOGIN_CAPTCHA_CODE="$login_captcha_code" USERNAME="$username" python3 -c 'import json,os; print(json.dumps({"username":os.environ["USERNAME"],"password":"LocalSmoke123","captchaId":os.environ["LOGIN_CAPTCHA_ID"],"captchaCode":os.environ["LOGIN_CAPTCHA_CODE"]}))')"
  replay_login_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/auth/login" -H 'Content-Type: application/json' --data-binary "$replay_login_payload")"
  printf '%s' "$replay_login_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==400,p; print("错误尝试后验证码已一次性消费，重放被拒绝。")'
  login_captcha_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/auth/login/captcha")"
  login_captcha_id="$(printf '%s' "$login_captcha_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["captchaId"])')"
  login_captcha_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:captcha:login:$login_captcha_id")"
  [[ -n "$login_captcha_code" && "$login_captcha_code" != nil ]] || { echo "无法从专用本地 Redis 取回登录测试验证码。" >&2; return 1; }
  login_payload="$(LOGIN_CAPTCHA_ID="$login_captcha_id" LOGIN_CAPTCHA_CODE="$login_captcha_code" USERNAME="$username" python3 -c 'import json,os; print(json.dumps({"username":os.environ["USERNAME"],"password":"LocalSmoke123","captchaId":os.environ["LOGIN_CAPTCHA_ID"],"captchaCode":os.environ["LOGIN_CAPTCHA_CODE"]}))')"
  login_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$LOCAL_TARGET/api/auth/login" -H 'Content-Type: application/json' --data-binary "$login_payload")"
  token="$(printf '%s' "$login_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message"); print(p["data"]["token"])')"

  available_models_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/platform-model/available" -H "X-User-Token: $token")"
  if [[ "${AI_VERIFY_DEEPSEEK_FALLBACK:-false}" == "true" || -n "${DEEPSEEK_API_KEY:-}" ]]; then
    unconfigured_model="$(mysql_query "SELECT display_name FROM platform_model_config WHERE status=1 AND (provider IS NULL OR LOWER(provider)<>'deepseek') AND (provider IS NULL OR TRIM(provider)='' OR actual_model IS NULL OR TRIM(actual_model)='' OR base_url IS NULL OR TRIM(base_url)='' OR api_key IS NULL OR TRIM(api_key)='') ORDER BY sort_order,id LIMIT 1;")"
  else
    unconfigured_model="$(mysql_query "SELECT display_name FROM platform_model_config WHERE status=1 AND (provider IS NULL OR TRIM(provider)='' OR actual_model IS NULL OR TRIM(actual_model)='' OR base_url IS NULL OR TRIM(base_url)='' OR api_key IS NULL OR TRIM(api_key)='') ORDER BY sort_order,id LIMIT 1;")"
  fi
  [[ "${AI_VERIFY_DEEPSEEK_FALLBACK:-false}" == "true" ]] && expected_deepseek_fallback="$deepseek_fallback_test_model"
  AVAILABLE_MODELS_RESPONSE="$available_models_response" UNCONFIGURED_MODEL="$unconfigured_model" EXPECTED_DEEPSEEK_FALLBACK="$expected_deepseek_fallback" python3 - <<'PY'
import json, os
payload = json.loads(os.environ['AVAILABLE_MODELS_RESPONSE'])
models = payload.get('data') if payload.get('code') == 200 else None
if not isinstance(models, list):
    raise SystemExit('认证模型列表接口未返回成功数据。')
if any(type(model.get('chatEnabled')) is not bool for model in models):
    raise SystemExit('模型可用接口缺少布尔型 chatEnabled 状态。')
if any('apiKey' in model or 'api_key' in model for model in models):
    raise SystemExit('模型可用接口意外暴露了凭据字段。')
mock = next((model for model in models if model.get('displayName') == 'Local Mock (Codex)'), None)
if mock is None or mock['chatEnabled'] is not True:
    raise SystemExit('已配置的本地模拟模型没有正确标记为可对话。')
expected = os.environ.get('UNCONFIGURED_MODEL', '')
if expected:
    unavailable = next((model for model in models if model.get('displayName') == expected), None)
    if unavailable is None or unavailable['chatEnabled'] is not False:
        raise SystemExit('缺少上游配置的模型没有正确标记为不可对话。')
expected_fallback = os.environ.get('EXPECTED_DEEPSEEK_FALLBACK', '')
if expected_fallback:
    fallback = next((model for model in models if model.get('displayName') == expected_fallback), None)
    if fallback is None or fallback['chatEnabled'] is not True:
        raise SystemExit('DeepSeek 环境回退测试模型没有标记为可对话。')
print('认证模型列表状态与凭据脱敏通过；已接入本地 mock 可对话，未配置模型明确不可用。')
PY

  if [[ -n "$unconfigured_model" ]]; then
    unconfigured_payload="$(MODEL_NAME="$unconfigured_model" python3 -c 'import json,os; print(json.dumps({"model":os.environ["MODEL_NAME"],"messages":[{"role":"user","content":"此请求应在预扣前被拒绝"}],"stream":False,"disableSearch":True}))')"
    unconfigured_state_before="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id),(SELECT COUNT(*) FROM ai_charge_intent WHERE user_id=$user_id),(SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id);")"
    unconfigured_points_before="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
    unconfigured_mock_before="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
    unconfigured_response="$(curl --silent --show-error --max-time 15 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$unconfigured_payload")"
    printf '%s' "$unconfigured_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code") != 200; print("未配置上游的模型请求在聊天接口被明确拒绝。")'
    unconfigured_state_after="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id),(SELECT COUNT(*) FROM ai_charge_intent WHERE user_id=$user_id),(SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id);")"
    unconfigured_points_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
    unconfigured_mock_after="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
    [[ "$unconfigured_state_before" == "$unconfigured_state_after" && "$unconfigured_points_before" == "$unconfigured_points_after" ]] || { echo "未配置上游的模型请求改变了消息、计费意图或用户点数。" >&2; return 1; }
    UNCONFIGURED_MOCK_BEFORE="$unconfigured_mock_before" UNCONFIGURED_MOCK_AFTER="$unconfigured_mock_after" python3 - <<'PY'
import json, os
before = json.loads(os.environ['UNCONFIGURED_MOCK_BEFORE'])
after = json.loads(os.environ['UNCONFIGURED_MOCK_AFTER'])
assert before.get('requests') == after.get('requests'), '未配置上游的请求意外到达本地模型 mock。'
print('拒绝未配置模型时没有预扣点数、写入消息/计费意图或调用上游。')
PY
  fi

  if [[ "${AI_VERIFY_DEEPSEEK_FALLBACK:-false}" == "true" ]]; then
    deepseek_fallback_state_before="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
    deepseek_fallback_points_before="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
    deepseek_fallback_mock_before="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
    deepseek_fallback_payload="$(MODEL_NAME="$deepseek_fallback_test_model" python3 -c 'import json,os; print(json.dumps({"model":os.environ["MODEL_NAME"],"messages":[{"role":"user","content":"请只回复 LOCAL_DEEPSEEK_OK"}],"maxTokens":256,"stream":False,"disableSearch":True}))')"
    deepseek_fallback_response="$(curl --silent --show-error --fail --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$deepseek_fallback_payload")"
    deepseek_fallback_stream_payload="$(MODEL_NAME="$deepseek_fallback_test_model" python3 -c 'import json,os; print(json.dumps({"model":os.environ["MODEL_NAME"],"messages":[{"role":"user","content":"请只回复 LOCAL_DEEPSEEK_STREAM_OK"}],"maxTokens":256,"stream":True,"disableSearch":True}))')"
    deepseek_fallback_stream_response="$(curl --silent --show-error --fail --max-time 60 -N -X POST "$LOCAL_TARGET/api/chat/stream" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$deepseek_fallback_stream_payload")"
    deepseek_fallback_state_after="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
    deepseek_fallback_points_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
    deepseek_fallback_mock_after="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
    DEEPSEEK_FALLBACK_RESPONSE="$deepseek_fallback_response" DEEPSEEK_FALLBACK_STREAM_RESPONSE="$deepseek_fallback_stream_response" DEEPSEEK_FALLBACK_STATE_BEFORE="$deepseek_fallback_state_before" DEEPSEEK_FALLBACK_STATE_AFTER="$deepseek_fallback_state_after" DEEPSEEK_FALLBACK_POINTS_BEFORE="$deepseek_fallback_points_before" DEEPSEEK_FALLBACK_POINTS_AFTER="$deepseek_fallback_points_after" DEEPSEEK_FALLBACK_MOCK_BEFORE="$deepseek_fallback_mock_before" DEEPSEEK_FALLBACK_MOCK_AFTER="$deepseek_fallback_mock_after" python3 - <<'PY'
import json, os
from decimal import Decimal
response = json.loads(os.environ['DEEPSEEK_FALLBACK_RESPONSE'])
assert response.get('code') == 200 and 'LOCAL_DEEPSEEK_OK' in response.get('data', ''), response
stream_response = os.environ['DEEPSEEK_FALLBACK_STREAM_RESPONSE']
assert 'LOCAL_DEEPSEEK_STREAM_OK' in stream_response, stream_response
assert 'event:done' in stream_response or 'event: done' in stream_response, stream_response
before_state = [int(value) for value in os.environ['DEEPSEEK_FALLBACK_STATE_BEFORE'].splitlines()]
after_state = [int(value) for value in os.environ['DEEPSEEK_FALLBACK_STATE_AFTER'].splitlines()]
assert after_state == [before_state[0] + 4, before_state[1] + 2], (before_state, after_state)
assert Decimal(os.environ['DEEPSEEK_FALLBACK_POINTS_AFTER']) == Decimal(os.environ['DEEPSEEK_FALLBACK_POINTS_BEFORE']) - Decimal('2.000')
mock_before = json.loads(os.environ['DEEPSEEK_FALLBACK_MOCK_BEFORE'])
mock_after = json.loads(os.environ['DEEPSEEK_FALLBACK_MOCK_AFTER'])
assert mock_after.get('requests') == mock_before.get('requests', 0) + 2, (mock_before, mock_after)
assert mock_after.get('streamingRequests') == mock_before.get('streamingRequests', 0) + 1, (mock_before, mock_after)
assert mock_after.get('lastEndpoint') == '/responses', mock_after
assert mock_after.get('lastAuthorizationPresent') is True, mock_after
assert mock_after.get('lastTokenLimitKey') == 'max_output_tokens' and mock_after.get('lastTokenLimit') == 256, mock_after
print('DeepSeek 环境变量回退 HTTP 验收通过：非流式/SSE 均到达认证 Responses mock，消息落库并各结算一次。')
PY
  fi

  verify_mock_output_token_case "$token" omitted 1536
  verify_mock_output_token_case "$token" negative 1536
  verify_mock_output_token_case "$token" over 1536
  verify_mock_output_token_case "$token" legal 768

  bind_email="verified_${username}@example.test"
  bind_phone="139$(printf '%08d' "$((user_id % 100000000))")"
  bind_send_payload="$(BIND_TARGET="$bind_email" python3 -c 'import json,os; print(json.dumps({"type":"email","target":os.environ["BIND_TARGET"]}))')"
  bind_send_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/send-bind-code" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$bind_send_payload")"
  printf '%s' "$bind_send_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("本地邮箱验证码通道发送接口通过。")'
  bind_email_hash="$(BIND_TARGET="$bind_email" python3 -c 'import hashlib,os; print(hashlib.sha256(os.environ["BIND_TARGET"].encode()).hexdigest())')"
  bind_email_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "verification:local:contact:$user_id:email:$bind_email_hash")"
  [[ "$bind_email_code" =~ ^[0-9]{6}$ ]] || { echo "本地邮箱验证码未进入隔离测试收件箱。" >&2; return 1; }
  bind_response="$(BIND_EMAIL="$bind_email" BIND_CODE="$bind_email_code" python3 -c 'import json,os; print(json.dumps({"email":os.environ["BIND_EMAIL"],"code":os.environ["BIND_CODE"]}))')"
  bind_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/bind-email" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$bind_response")"
  printf '%s' "$bind_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("邮箱验证码核验与数据库绑定通过。")'

  bind_send_payload="$(BIND_TARGET="$bind_phone" python3 -c 'import json,os; print(json.dumps({"type":"phone","target":os.environ["BIND_TARGET"]}))')"
  bind_send_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/send-bind-code" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$bind_send_payload")"
  printf '%s' "$bind_send_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p'
  bind_phone_hash="$(BIND_TARGET="$bind_phone" python3 -c 'import hashlib,os; print(hashlib.sha256(os.environ["BIND_TARGET"].encode()).hexdigest())')"
  bind_phone_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "verification:local:contact:$user_id:phone:$bind_phone_hash")"
  [[ "$bind_phone_code" =~ ^[0-9]{6}$ ]] || { echo "本地短信验证码未进入隔离测试收件箱。" >&2; return 1; }
  bind_response="$(BIND_PHONE="$bind_phone" BIND_CODE="$bind_phone_code" python3 -c 'import json,os; print(json.dumps({"phone":os.environ["BIND_PHONE"],"code":os.environ["BIND_CODE"]}))')"
  bind_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/bind-phone" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$bind_response")"
  printf '%s' "$bind_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("短信验证码核验与手机号数据库绑定通过。")'
  bind_state="$(mysql_query "SELECT email FROM user WHERE id=$user_id; SELECT phone FROM user WHERE id=$user_id; SELECT COUNT(*) FROM user_contact_verification WHERE user_id=$user_id;")"
  EXPECTED_EMAIL="$bind_email" EXPECTED_PHONE="$bind_phone" BIND_STATE="$bind_state" python3 - <<'PY'
import os
values = os.environ["BIND_STATE"].splitlines()
assert values == [os.environ["EXPECTED_EMAIL"], os.environ["EXPECTED_PHONE"], "1"], values
print("邮箱、手机号和已验证联系方式记录均已持久化。")
PY

  # 用户端直接充值与优惠券充值均已关闭，确认伪造金额不改变数据库、账本或 Redis 余额。
  recharge_state_before="$(mysql_query "SELECT balance FROM user WHERE id=$user_id; SELECT COUNT(*) FROM billing_record WHERE user_id=$user_id AND type=2; SELECT COUNT(*) FROM coupon WHERE user_id=$user_id; SELECT COUNT(*) FROM coupon_usage_record WHERE user_id=$user_id;")"
  recharge_cache_before="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:user_balance:$user_id")"
  recharge_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/recharge?amount=1000000" -H "X-User-Token: $token")"
  printf '%s' "$recharge_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==503,p; print("普通用户直接充值已安全关闭，伪造金额请求返回 503。")'
  recharge_coupon_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/recharge-with-coupon" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary '{"amount":1000000,"couponId":1}')"
  printf '%s' "$recharge_coupon_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==503,p; print("优惠券直充已安全关闭，优惠券与余额均未变更。")'
  coupon_list_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/user/coupons" -H "X-User-Token: $token")"
  printf '%s' "$coupon_list_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200 and p.get("data")==[],p; print("优惠券列表只返回数据库中的真实记录，新用户不会看到随机生成的虚假优惠券。")'
  coupon_use_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/coupons/use" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary '{"code":"COUPON-EXAMPLE"}')"
  printf '%s' "$coupon_use_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==503,p; print("独立使用优惠券接口明确返回暂不可用，不会伪报成功。")'
  coupon_redeem_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/coupons/redeem" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary '{"redeemCode":"REDEEM-EXAMPLE"}')"
  printf '%s' "$coupon_redeem_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==503,p; print("兑换码尚未开放，接口明确拒绝且不伪报成功。")'
  recharge_state_after="$(mysql_query "SELECT balance FROM user WHERE id=$user_id; SELECT COUNT(*) FROM billing_record WHERE user_id=$user_id AND type=2; SELECT COUNT(*) FROM coupon WHERE user_id=$user_id; SELECT COUNT(*) FROM coupon_usage_record WHERE user_id=$user_id;")"
  [[ "$recharge_state_before" == "$recharge_state_after" ]] || { echo "用户自助充值/优惠券接口拒绝后余额、充值流水、优惠券或使用记录发生变化。" >&2; return 1; }
  recharge_cache_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:user_balance:$user_id")"
  [[ "$recharge_cache_before" == "$recharge_cache_after" ]] || { echo "用户自助充值拒绝后 Redis 余额发生变化。" >&2; return 1; }

  path_sentinel="$RUNTIME/path-traversal-sentinel.txt"
  printf 'LOCAL_PATH_TRAVERSAL_SENTINEL' > "$path_sentinel"
  state_before="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id), (SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED');")"
  points_before="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  mock_stats_before="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  path_payload="$(PATH_SENTINEL_NAME="$(basename "$path_sentinel")" python3 -c 'import json,os; print(json.dumps({"model":"Local Mock (Codex)","messages":[{"role":"user","content":[{"type":"text","text":"本地路径边界测试"},{"type":"image_url","image_url":{"url":"http://127.0.0.1:18080/api/uploads/%2e%2e/"+os.environ["PATH_SENTINEL_NAME"]}}]}],"stream":False,"disableSearch":True}))')"
  path_response="$(curl --silent --show-error --max-time 15 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$path_payload")"
  printf '%s' "$path_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code") != 200,p; print("聊天图片路径越界请求已拒绝。")'
  mock_stats_after="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  MOCK_STATS_BEFORE="$mock_stats_before" MOCK_STATS_AFTER="$mock_stats_after" python3 - <<'PY'
import json, os
before = json.loads(os.environ["MOCK_STATS_BEFORE"])
after = json.loads(os.environ["MOCK_STATS_AFTER"])
assert before.get("requests") == after.get("requests"), (before, after)
print("路径越界请求未到达本地模型上游。")
PY
  state_after="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id), (SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED');")"
  [[ "$state_before" == "$state_after" ]] || { echo "越界请求意外写入消息或结算记录：$state_before -> $state_after" >&2; return 1; }
  points_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  POINTS_BEFORE="$points_before" POINTS_AFTER="$points_after" python3 - <<'PY'
from decimal import Decimal
import os
before = Decimal(os.environ["POINTS_BEFORE"])
after = Decimal(os.environ["POINTS_AFTER"])
assert before == after, f"越界请求未正确回滚预扣点数：{before} -> {after}"
PY
  rm -f "$path_sentinel"

  chat_payload='{"model":"Local Mock (Codex)","messages":[{"role":"user","content":"请只回复 LOCAL_CHAT_OK"}],"stream":false,"disableSearch":true}'
  chat_response="$(curl --silent --show-error --fail --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$chat_payload")"
  printf '%s' "$chat_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message"); assert "LOCAL_CHAT_OK" in p.get("data", ""),p.get("data"); print("非流式 AI 对话通过：收到本地模拟回复。")'

  # D2: a reused client key replays the durable response without another provider call or charge.
  d2_key="local-d2-retry-$user_id-$(date +%s)-${RANDOM}"
  d2_request_id="$(D2_USER_ID="$user_id" D2_KEY="$d2_key" python3 -c 'import hashlib,os; print("req_"+hashlib.sha256((os.environ["D2_USER_ID"]+":"+os.environ["D2_KEY"]).encode()).hexdigest()[:60])')"
  d2_payload="$(D2_KEY="$d2_key" python3 -c 'import json,os; print(json.dumps({"model":"Local Mock (Codex)","messages":[{"role":"user","content":"D2 本地幂等重试"}],"stream":False,"disableSearch":True,"idempotencyKey":os.environ["D2_KEY"]}))')"
  d2_before_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_before_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  d2_mock_before="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  d2_first_response="$(curl --silent --show-error --fail --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$d2_payload")"
  d2_first_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_first_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  d2_second_response="$(curl --silent --show-error --fail --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$d2_payload")"
  d2_second_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_second_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  d2_request_rows="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE request_id='$d2_request_id'; SELECT COUNT(*) FROM billing_record WHERE request_id='$d2_request_id'; SELECT status FROM ai_charge_intent WHERE request_id='$d2_request_id';")"
  d2_mock_after="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  D2_FIRST_RESPONSE="$d2_first_response" D2_SECOND_RESPONSE="$d2_second_response" D2_BEFORE_STATE="$d2_before_state" D2_FIRST_STATE="$d2_first_state" D2_SECOND_STATE="$d2_second_state" D2_BEFORE_POINTS="$d2_before_points" D2_FIRST_POINTS="$d2_first_points" D2_SECOND_POINTS="$d2_second_points" D2_REQUEST_ROWS="$d2_request_rows" D2_MOCK_BEFORE="$d2_mock_before" D2_MOCK_AFTER="$d2_mock_after" python3 - <<'PY'
import json, os
first = json.loads(os.environ["D2_FIRST_RESPONSE"])
second = json.loads(os.environ["D2_SECOND_RESPONSE"])
assert first.get("code") == 200 and second.get("code") == 200, (first, second)
assert first.get("data") == second.get("data"), (first.get("data"), second.get("data"))
before = [int(x) for x in os.environ["D2_BEFORE_STATE"].splitlines()]
first_state = [int(x) for x in os.environ["D2_FIRST_STATE"].splitlines()]
second_state = [int(x) for x in os.environ["D2_SECOND_STATE"].splitlines()]
assert first_state == [before[0] + 2, before[1] + 1], (before, first_state)
assert second_state == first_state, (first_state, second_state)
assert float(os.environ["D2_FIRST_POINTS"]) == float(os.environ["D2_BEFORE_POINTS"]) - 1.0
assert os.environ["D2_SECOND_POINTS"] == os.environ["D2_FIRST_POINTS"]
assert os.environ["D2_REQUEST_ROWS"].splitlines() == ["2", "1", "SETTLED"]
before_calls = json.loads(os.environ["D2_MOCK_BEFORE"])["requests"]
after_calls = json.loads(os.environ["D2_MOCK_AFTER"])["requests"]
assert after_calls == before_calls + 1, (before_calls, after_calls)
print("D2 幂等重试通过：第二次请求重放已保存回复，没有重复调用模型、写消息或收费。")
PY

  d2_stream_key="local-d2-stream-$user_id-$(date +%s)-${RANDOM}"
  d2_stream_request_id="$(D2_USER_ID="$user_id" D2_KEY="$d2_stream_key" python3 -c 'import hashlib,os; print("req_"+hashlib.sha256((os.environ["D2_USER_ID"]+":"+os.environ["D2_KEY"]).encode()).hexdigest()[:60])')"
  d2_stream_payload="$(D2_KEY="$d2_stream_key" python3 -c 'import json,os; print(json.dumps({"model":"Local Mock (Codex)","messages":[{"role":"user","content":"D2 SSE 幂等重试"}],"stream":True,"disableSearch":True,"idempotencyKey":os.environ["D2_KEY"]}))')"
  d2_stream_before_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_stream_before_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  d2_stream_mock_before="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  d2_stream_first="$(curl --silent --show-error --fail --max-time 60 -N -X POST "$LOCAL_TARGET/api/chat/stream" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$d2_stream_payload")"
  d2_stream_first_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_stream_first_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  d2_stream_second="$(curl --silent --show-error --fail --max-time 60 -N -X POST "$LOCAL_TARGET/api/chat/stream" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$d2_stream_payload")"
  d2_stream_second_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_stream_second_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  d2_stream_rows="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE request_id='$d2_stream_request_id'; SELECT COUNT(*) FROM billing_record WHERE request_id='$d2_stream_request_id'; SELECT status FROM ai_charge_intent WHERE request_id='$d2_stream_request_id';")"
  d2_stream_mock_after="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  D2_STREAM_FIRST="$d2_stream_first" D2_STREAM_SECOND="$d2_stream_second" D2_STREAM_BEFORE_STATE="$d2_stream_before_state" D2_STREAM_FIRST_STATE="$d2_stream_first_state" D2_STREAM_SECOND_STATE="$d2_stream_second_state" D2_STREAM_BEFORE_POINTS="$d2_stream_before_points" D2_STREAM_FIRST_POINTS="$d2_stream_first_points" D2_STREAM_SECOND_POINTS="$d2_stream_second_points" D2_STREAM_ROWS="$d2_stream_rows" D2_STREAM_MOCK_BEFORE="$d2_stream_mock_before" D2_STREAM_MOCK_AFTER="$d2_stream_mock_after" python3 - <<'PY'
import json, os
from decimal import Decimal
first = os.environ["D2_STREAM_FIRST"]
second = os.environ["D2_STREAM_SECOND"]
for body in (first, second):
    assert "LOCAL_STREAM_OK" in body or "LOCAL_CHAT_OK" in body, body
    assert "event:done" in body or "event: done" in body, body
before = [int(x) for x in os.environ["D2_STREAM_BEFORE_STATE"].splitlines()]
first_state = [int(x) for x in os.environ["D2_STREAM_FIRST_STATE"].splitlines()]
second_state = [int(x) for x in os.environ["D2_STREAM_SECOND_STATE"].splitlines()]
assert first_state == [before[0] + 2, before[1] + 1], (before, first_state)
assert second_state == first_state, (first_state, second_state)
assert Decimal(os.environ["D2_STREAM_FIRST_POINTS"]) == Decimal(os.environ["D2_STREAM_BEFORE_POINTS"]) - Decimal("1.000")
assert Decimal(os.environ["D2_STREAM_SECOND_POINTS"]) == Decimal(os.environ["D2_STREAM_FIRST_POINTS"])
assert os.environ["D2_STREAM_ROWS"].splitlines() == ["2", "1", "SETTLED"]
before_calls = json.loads(os.environ["D2_STREAM_MOCK_BEFORE"])["requests"]
after_calls = json.loads(os.environ["D2_STREAM_MOCK_AFTER"])["requests"]
assert after_calls == before_calls + 1, (before_calls, after_calls)
print("D2 SSE 幂等重试通过：重放仍返回 message/done，模型、消息与结算均未重复。")
PY

  # Force a local database write error after a complete mock response. The full turn must roll back and refund.
  d2_failure_key="local-d2-failure-$user_id-$(date +%s)-${RANDOM}"
  d2_failure_request_id="$(D2_USER_ID="$user_id" D2_KEY="$d2_failure_key" python3 -c 'import hashlib,os; print("req_"+hashlib.sha256((os.environ["D2_USER_ID"]+":"+os.environ["D2_KEY"]).encode()).hexdigest()[:60])')"
  d2_failure_payload="$(D2_KEY="$d2_failure_key" python3 -c 'import json,os; print(json.dumps({"model":"Local Mock (Codex)","messages":[{"role":"user","content":"D2 本地写库失败退款"}],"stream":False,"disableSearch":True,"idempotencyKey":os.environ["D2_KEY"]}))')"
  d2_failure_before_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_failure_before_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  mysql_query "DROP TRIGGER IF EXISTS trg_d2_local_fail_chat; CREATE TRIGGER trg_d2_local_fail_chat BEFORE INSERT ON chat_message FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'D2 local persistence failure';"
  set +e
  d2_failure_response="$(curl --silent --show-error --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$d2_failure_payload")"
  d2_failure_curl_status=$?
  set -e
  mysql_query "DROP TRIGGER IF EXISTS trg_d2_local_fail_chat;"
  [[ "$d2_failure_curl_status" == "0" ]] || { echo "D2 写库失败验收未收到接口响应。" >&2; return 1; }
  d2_failure_after_state="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  d2_failure_after_points="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  d2_failure_rows="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE request_id='$d2_failure_request_id'; SELECT status FROM ai_charge_intent WHERE request_id='$d2_failure_request_id'; SELECT COUNT(*) FROM billing_record WHERE request_id='$d2_failure_request_id';")"
  D2_FAILURE_RESPONSE="$d2_failure_response" D2_FAILURE_BEFORE_STATE="$d2_failure_before_state" D2_FAILURE_AFTER_STATE="$d2_failure_after_state" D2_FAILURE_BEFORE_POINTS="$d2_failure_before_points" D2_FAILURE_AFTER_POINTS="$d2_failure_after_points" D2_FAILURE_ROWS="$d2_failure_rows" python3 - <<'PY'
import json, os
from decimal import Decimal
response = json.loads(os.environ["D2_FAILURE_RESPONSE"])
assert response.get("code") != 200, response
assert os.environ["D2_FAILURE_BEFORE_STATE"] == os.environ["D2_FAILURE_AFTER_STATE"]
assert Decimal(os.environ["D2_FAILURE_BEFORE_POINTS"]) == Decimal(os.environ["D2_FAILURE_AFTER_POINTS"]), (
    os.environ["D2_FAILURE_BEFORE_POINTS"], os.environ["D2_FAILURE_AFTER_POINTS"])
assert os.environ["D2_FAILURE_ROWS"].splitlines() == ["0", "REFUNDED", "0"]
print("D2 数据库写入故障通过：接口显式失败、消息事务回滚、预扣退款且没有结算扣费。")
PY

  conversation_id="$(mysql_query "SELECT id FROM conversation WHERE user_id=$user_id ORDER BY id DESC LIMIT 1;")"
  [[ "$conversation_id" =~ ^[0-9]+$ ]] || { echo "本地聊天未创建会话。" >&2; return 1; }
  owned_chat_payload="$(CONVERSATION_ID="$conversation_id" python3 -c 'import json,os; print(json.dumps({"model":"Local Mock (Codex)","conversationId":int(os.environ["CONVERSATION_ID"]),"messages":[{"role":"user","content":"本人继续会话"}],"stream":False,"disableSearch":True}))')"
  owned_chat_response="$(curl --silent --show-error --fail --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$owned_chat_payload")"
  printf '%s' "$owned_chat_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message"); assert "LOCAL_CHAT_OK" in p.get("data", ""),p.get("data"); print("本人继续已有会话通过。")'

  other_username="local_idor_$(date +%s)_${RANDOM}"
  other_register_captcha_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/auth/register/captcha")"
  other_register_captcha_id="$(printf '%s' "$other_register_captcha_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["captchaId"])')"
  other_register_captcha_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:captcha:register:$other_register_captcha_id")"
  [[ -n "$other_register_captcha_code" && "$other_register_captcha_code" != nil ]] || { echo "无法读取本地 IDOR 测试注册验证码。" >&2; return 1; }
  other_register_email="${other_username}@example.test"
  other_email_code_payload="$(EMAIL="$other_register_email" CAPTCHA_ID="$other_register_captcha_id" CAPTCHA_CODE="$other_register_captcha_code" python3 -c 'import json,os; print(json.dumps({"email":os.environ["EMAIL"],"captchaId":os.environ["CAPTCHA_ID"],"captchaCode":os.environ["CAPTCHA_CODE"]}))')"
  other_email_code_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$LOCAL_TARGET/api/auth/register/email-code" -H 'Content-Type: application/json' --data-binary "$other_email_code_payload")"
  printf '%s' "$other_email_code_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message")'
  other_register_email_hash="$(EMAIL="$other_register_email" python3 -c 'import hashlib,os; print(hashlib.sha256(os.environ["EMAIL"].lower().encode()).hexdigest())')"
  other_register_email_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "verification:local:registration:email:$other_register_email_hash")"
  [[ -n "$other_register_email_code" && "$other_register_email_code" != nil ]] || { echo "本地 IDOR 测试注册邮箱验证码投递失败。" >&2; return 1; }
  other_register_payload="$(EMAIL="$other_register_email" EMAIL_CODE="$other_register_email_code" USERNAME="$other_username" python3 -c 'import json,os; print(json.dumps({"username":os.environ["USERNAME"],"password":"LocalSmoke123","email":os.environ["EMAIL"],"emailCode":os.environ["EMAIL_CODE"]}))')"
  other_register_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$LOCAL_TARGET/api/auth/register" -H 'Content-Type: application/json' --data-binary "$other_register_payload")"
  other_user_id="$(printf '%s' "$other_register_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message"); print(p["data"]["id"])')"
  other_login_captcha_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/auth/login/captcha")"
  other_login_captcha_id="$(printf '%s' "$other_login_captcha_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["captchaId"])')"
  other_login_captcha_code="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:captcha:login:$other_login_captcha_id")"
  [[ -n "$other_login_captcha_code" && "$other_login_captcha_code" != nil ]] || { echo "无法读取本地 IDOR 测试登录验证码。" >&2; return 1; }
  other_login_payload="$(OTHER_USERNAME="$other_username" CAPTCHA_ID="$other_login_captcha_id" CAPTCHA_CODE="$other_login_captcha_code" python3 -c 'import json,os; print(json.dumps({"username":os.environ["OTHER_USERNAME"],"password":"LocalSmoke123","captchaId":os.environ["CAPTCHA_ID"],"captchaCode":os.environ["CAPTCHA_CODE"]}))')"
  other_login_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$LOCAL_TARGET/api/auth/login" -H 'Content-Type: application/json' --data-binary "$other_login_payload")"
  other_token="$(printf '%s' "$other_login_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message"); print(p["data"]["token"])')"
  idor_chat_payload="$(CONVERSATION_ID="$conversation_id" python3 -c 'import json,os; print(json.dumps({"model":"Local Mock (Codex)","conversationId":int(os.environ["CONVERSATION_ID"]),"messages":[{"role":"user","content":"跨用户会话越权测试"}],"stream":False,"disableSearch":True}))')"
  idor_state_before="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE conversation_id=$conversation_id), (SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id IN ($user_id,$other_user_id) AND status='SETTLED');")"
  other_points_before="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$other_user_id")"
  mock_stats_before="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  idor_chat_response="$(curl --silent --show-error --max-time 15 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $other_token" --data-binary "$idor_chat_payload")"
  printf '%s' "$idor_chat_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==404 and "无权访问" in p.get("message", ""),p; print("跨用户非流式会话请求按预期拒绝。")'
  idor_stream_response="$(curl --silent --show-error --max-time 15 -N -X POST "$LOCAL_TARGET/api/chat/stream" -H 'Content-Type: application/json' -H "X-User-Token: $other_token" --data-binary "$(CONVERSATION_ID="$conversation_id" python3 -c 'import json,os; print(json.dumps({"model":"Local Mock (Codex)","conversationId":int(os.environ["CONVERSATION_ID"]),"messages":[{"role":"user","content":"跨用户流式会话越权测试"}],"stream":True,"disableSearch":True}))')")"
  IDOR_STREAM_BODY="$idor_stream_response" python3 - <<'PY'
import os
body = os.environ["IDOR_STREAM_BODY"]
assert ("event:error" in body or "event: error" in body) and "无权访问" in body, body
assert "event:done" not in body and "event: done" not in body, body
print("跨用户 SSE 会话请求返回 error 事件并终止。")
PY
  mock_stats_after="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  MOCK_STATS_BEFORE="$mock_stats_before" MOCK_STATS_AFTER="$mock_stats_after" python3 - <<'PY'
import json, os
before = json.loads(os.environ["MOCK_STATS_BEFORE"])
after = json.loads(os.environ["MOCK_STATS_AFTER"])
assert before.get("requests") == after.get("requests"), (before, after)
print("跨用户会话请求未到达本地模型上游。")
PY
  idor_state_after="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE conversation_id=$conversation_id), (SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id IN ($user_id,$other_user_id) AND status='SETTLED');")"
  [[ "$idor_state_before" == "$idor_state_after" ]] || { echo "跨用户会话请求意外写入消息或结算：$idor_state_before -> $idor_state_after" >&2; return 1; }
  other_points_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$other_user_id")"
  [[ "$other_points_before" == "$other_points_after" ]] || { echo "跨用户会话请求意外变更点数：$other_points_before -> $other_points_after" >&2; return 1; }

  # 把用户 Token 的滑动窗口填到阈值，确认真实 /api/chat 路径会触发用户限流，且不产生业务副作用。
  seed_user_rate_window "$user_id" 60
  rate_limited_payload='{"model":"Local Mock (Codex)","messages":[{"role":"user","content":"用户限流边界测试"}],"stream":false,"disableSearch":true}'
  rate_state_before="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id), (SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED');")"
  rate_points_before="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  mock_stats_before="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  rate_http_code="$(curl --silent --show-error --max-time 10 -o "$RUNTIME/rate-limit-response.json" -w '%{http_code}' -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$rate_limited_payload")"
  rate_limited_response="$(cat "$RUNTIME/rate-limit-response.json")"
  [[ "$rate_http_code" == 429 ]] || { echo "用户限流预期 HTTP 429，实际：$rate_http_code $rate_limited_response" >&2; return 1; }
  printf '%s' "$rate_limited_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==429,p; print("用户 Token 超出 60 次/分钟后按约定返回 HTTP/业务码 429。")'
  mock_stats_after="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  MOCK_STATS_BEFORE="$mock_stats_before" MOCK_STATS_AFTER="$mock_stats_after" python3 - <<'PY'
import json, os
before = json.loads(os.environ["MOCK_STATS_BEFORE"])
after = json.loads(os.environ["MOCK_STATS_AFTER"])
assert before.get("requests") == after.get("requests"), (before, after)
print("被用户限流的请求未到达模型上游。")
PY
  rate_state_after="$(mysql_query "SELECT (SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id), (SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED');")"
  [[ "$rate_state_before" == "$rate_state_after" ]] || { echo "被用户限流的请求意外写入消息/结算：$rate_state_before -> $rate_state_after" >&2; return 1; }
  rate_points_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  [[ "$rate_points_before" == "$rate_points_after" ]] || { echo "被用户限流的请求意外改变点数：$rate_points_before -> $rate_points_after" >&2; return 1; }

  # 另一个用户拥有独立窗口，不能被第一个用户的 60 条记录连带限流。
  mysql_query "UPDATE user SET points=100, points_version=points_version+1 WHERE id=$other_user_id;"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$other_user_id" "100.000" >/dev/null
  other_chat_payload='{"model":"Local Mock (Codex)","messages":[{"role":"user","content":"不同用户限流隔离测试"}],"stream":false,"disableSearch":true}'
  other_chat_response="$(curl --silent --show-error --fail --max-time 60 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $other_token" --data-binary "$other_chat_payload")"
  printf '%s' "$other_chat_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; assert "LOCAL_CHAT_OK" in p.get("data", ""),p; print("另一用户的独立限流窗口仍允许正常聊天。")'

  # 测试数据运维接口必须通过管理员切面；验证拒绝请求不读取或改写缓存。
  admin_cache_url="$LOCAL_TARGET/api/admin/test-data"
  docker exec "$REDIS_CONTAINER" redis-cli MSET "conversations:$user_id" "A5_SENTINEL" "conversations:version:$user_id" "A5_SENTINEL_VERSION" >/dev/null
  admin_unauth_read="$(curl --silent --show-error --fail --max-time 10 "$admin_cache_url/check-conversation-cache?userId=$user_id")"
  printf '%s' "$admin_unauth_read" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==401,p; print("未登录测试数据读取请求返回 401。")'
  admin_unauth_init="$(curl --silent --show-error --fail --max-time 10 -X POST "$admin_cache_url/init-conversation-cache?userId=$user_id")"
  printf '%s' "$admin_unauth_init" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==401,p; print("未登录测试数据写入请求返回 401。")'
  admin_user_read="$(curl --silent --show-error --fail --max-time 10 "$admin_cache_url/check-conversation-cache?userId=$user_id" -H "X-User-Token: $token")"
  printf '%s' "$admin_user_read" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==403,p; print("普通用户测试数据读取请求返回 403。")'
  admin_user_init="$(curl --silent --show-error --fail --max-time 10 -X POST "$admin_cache_url/init-conversation-cache?userId=$user_id" -H "X-User-Token: $token")"
  printf '%s' "$admin_user_init" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==403,p; print("普通用户测试数据写入请求返回 403。")'
  admin_user_clear="$(curl --silent --show-error --fail --max-time 10 -X DELETE "$admin_cache_url/clear-conversation-cache?userId=$user_id" -H "X-User-Token: $token")"
  printf '%s' "$admin_user_clear" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==403,p; print("普通用户清理测试缓存请求返回 403。")'
  [[ "$(docker exec "$REDIS_CONTAINER" redis-cli GET "conversations:$user_id")" == "A5_SENTINEL" ]] || { echo "未授权测试数据请求改写或删除了缓存哨兵。" >&2; return 1; }
  [[ "$(docker exec "$REDIS_CONTAINER" redis-cli GET "conversations:version:$user_id")" == "A5_SENTINEL_VERSION" ]] || { echo "未授权测试数据请求改写或删除了缓存版本。" >&2; return 1; }

  # 只提升本地临时测试用户角色，覆盖管理员路径和数据库审计记录。
  mysql_query "UPDATE user SET role='ADMIN' WHERE id=$other_user_id;"
  admin_init_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$admin_cache_url/init-conversation-cache?userId=$user_id" -H "X-User-Token: $other_token")"
  printf '%s' "$admin_init_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("管理员初始化指定用户缓存成功。")'
  admin_check_response="$(curl --silent --show-error --fail --max-time 15 "$admin_cache_url/check-conversation-cache?userId=$user_id" -H "X-User-Token: $other_token")"
  printf '%s' "$admin_check_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200 and p["data"].get("hasCache"),p; print("管理员查看缓存状态成功。")'
  admin_init_all_response="$(curl --silent --show-error --fail --max-time 30 -X POST "$admin_cache_url/init-all-conversation-cache" -H "X-User-Token: $other_token")"
  printf '%s' "$admin_init_all_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("管理员批量初始化缓存成功。")'
  admin_clear_response="$(curl --silent --show-error --fail --max-time 15 -X DELETE "$admin_cache_url/clear-conversation-cache?userId=$user_id" -H "X-User-Token: $other_token")"
  printf '%s' "$admin_clear_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("管理员清理指定用户缓存成功。")'
  [[ "$(docker exec "$REDIS_CONTAINER" redis-cli EXISTS "conversations:$user_id")" == "0" ]] || { echo "管理员清理后对话缓存仍存在。" >&2; return 1; }
  [[ "$(docker exec "$REDIS_CONTAINER" redis-cli EXISTS "conversations:version:$user_id")" == "0" ]] || { echo "管理员清理后缓存版本仍存在。" >&2; return 1; }
  admin_audit_count=0
  for _ in {1..15}; do
    admin_audit_count="$(mysql_query "SELECT COUNT(*) FROM admin_operation_log WHERE admin_id=$other_user_id AND admin_username='$other_username' AND operation_type='TEST_DATA_MANAGE' AND operation_action IN ('INIT_CONVERSATION_CACHE','CHECK_CONVERSATION_CACHE','INIT_ALL_CONVERSATION_CACHE','CLEAR_CONVERSATION_CACHE') AND result='SUCCESS';")"
    [[ "$admin_audit_count" =~ ^[0-9]+$ && "$admin_audit_count" -ge 4 ]] && break
    sleep 1
  done
  [[ "$admin_audit_count" =~ ^[0-9]+$ && "$admin_audit_count" -ge 4 ]] || { echo "管理员测试数据操作审计记录不足，实际：$admin_audit_count" >&2; return 1; }
  echo "管理员初始化、查询、批量初始化和清理操作均记录了操作者与结果。"

  # B2：在本地模拟一个尚未结算的 Redis 预扣，再通过管理员真实接口充值，验证充值保留预扣余额。
  b2_db_before="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b2_redis_before="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  B2_DB_BEFORE="$b2_db_before" B2_REDIS_BEFORE="$b2_redis_before" python3 - <<'PY'
from decimal import Decimal
import os
assert Decimal(os.environ["B2_DB_BEFORE"]) == Decimal(os.environ["B2_REDIS_BEFORE"]), (os.environ["B2_DB_BEFORE"], os.environ["B2_REDIS_BEFORE"])
PY
  b2_pending_key="points:pre_deduct:local-b2-pending-$user_id"
  b2_pending_balance="$(B2_DB_BEFORE="$b2_db_before" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B2_DB_BEFORE"]) - Decimal("5.000"))')"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b2_pending_balance" >/dev/null
  docker exec "$REDIS_CONTAINER" redis-cli SET "$b2_pending_key" "5.000" EX 86400 >/dev/null
  b2_pending_payload="$(B2_USER_ID="$user_id" python3 -c 'import json,os; print(json.dumps({"userId":int(os.environ["B2_USER_ID"]),"points":20,"reason":"B2_LOCAL_PENDING_TOPUP"}))')"
  b2_pending_response="$(curl --silent --show-error --fail --max-time 20 -X POST "$LOCAL_TARGET/api/admin/points/adjust" -H 'Content-Type: application/json' -H "X-User-Token: $other_token" --data-binary "$b2_pending_payload")"
  printf '%s' "$b2_pending_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("有未结算预扣时管理员充值仍保留预扣，接口操作成功。")'
  b2_after_pending="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b2_redis_after_pending="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  b2_pending_bill="$(mysql_query "SELECT balance_before, balance_after FROM points_bill WHERE user_id=$user_id AND business_id LIKE 'admin_adjust_%' ORDER BY id DESC LIMIT 1;")"
  B2_DB_BEFORE="$b2_db_before" B2_DB_AFTER="$b2_after_pending" B2_REDIS_AFTER="$b2_redis_after_pending" B2_BILL="$b2_pending_bill" python3 - <<'PY'
from decimal import Decimal
import os
before = Decimal(os.environ["B2_DB_BEFORE"])
expected = before - Decimal("5.000") + Decimal("20.000")
after_db = Decimal(os.environ["B2_DB_AFTER"])
after_redis = Decimal(os.environ["B2_REDIS_AFTER"])
bill = [Decimal(value) for value in os.environ["B2_BILL"].split()]
assert after_db == expected, (expected, after_db)
assert after_redis == expected, (expected, after_redis)
assert bill == [before - Decimal("5.000"), expected], bill
print("未结算预扣余额、充值后 DB/Redis 和点数流水一致。")
PY
  b2_final_balance="$(B2_DB_BEFORE="$b2_db_before" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B2_DB_BEFORE"]) + Decimal("20.000"))')"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b2_final_balance" >/dev/null
  mysql_query "UPDATE user SET points=$b2_final_balance, points_version=points_version+1 WHERE id=$user_id;"
  docker exec "$REDIS_CONTAINER" redis-cli DEL "$b2_pending_key" >/dev/null

  # 并发执行 10 个独立增点请求，验证每次增量都进入 DB、Redis 和账单。
  b2_concurrent_before="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b2_bills_before="$(mysql_query "SELECT COUNT(*) FROM points_bill WHERE user_id=$user_id;")"
  b2_tmpdir="$(mktemp -d "$RUNTIME/b2-points.XXXXXX")"
  b2_adjust_pids=()
  for i in {1..10}; do
    b2_concurrent_payload="$(B2_USER_ID="$user_id" B2_INDEX="$i" python3 -c 'import json,os; print(json.dumps({"userId":int(os.environ["B2_USER_ID"]),"points":1.125,"reason":"B2_CONCURRENT_"+os.environ["B2_INDEX"]}))')"
    curl --silent --show-error --fail --max-time 30 -o "$b2_tmpdir/response-$i.json" -X POST "$LOCAL_TARGET/api/admin/points/adjust" -H 'Content-Type: application/json' -H "X-User-Token: $other_token" --data-binary "$b2_concurrent_payload" &
    b2_adjust_pids+=("$!")
  done
  for b2_pid in "${b2_adjust_pids[@]}"; do wait "$b2_pid"; done
  B2_TMPDIR="$b2_tmpdir" python3 - <<'PY'
import json, os
from pathlib import Path
responses = [json.loads(path.read_text()) for path in sorted(Path(os.environ["B2_TMPDIR"]).glob("response-*.json"))]
assert len(responses) == 10, len(responses)
assert all(response.get("code") == 200 for response in responses), responses
print("10 个并发管理员增点请求均成功。")
PY
  b2_concurrent_after="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b2_redis_concurrent_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  b2_bills_after="$(mysql_query "SELECT COUNT(*) FROM points_bill WHERE user_id=$user_id;")"
  B2_BEFORE="$b2_concurrent_before" B2_DB_AFTER="$b2_concurrent_after" B2_REDIS_AFTER="$b2_redis_concurrent_after" B2_BILLS_BEFORE="$b2_bills_before" B2_BILLS_AFTER="$b2_bills_after" python3 - <<'PY'
from decimal import Decimal
import os
before = Decimal(os.environ["B2_BEFORE"])
expected = before + Decimal("1.125") * 10
after_db = Decimal(os.environ["B2_DB_AFTER"])
after_redis = Decimal(os.environ["B2_REDIS_AFTER"])
bills_added = int(os.environ["B2_BILLS_AFTER"]) - int(os.environ["B2_BILLS_BEFORE"])
assert after_db == expected, (expected, after_db)
assert after_redis == expected, (expected, after_redis)
assert bills_added == 10, bills_added
print("并发后 Redis/DB 余额一致，10 条点数流水无丢失。")
PY
  rm -rf "$b2_tmpdir"

  # B3：模拟 Redis 已结算但 outbox 尚未确认、随后另一笔余额变更已提交，再重放旧结算快照。
  local b3_request_id b3_balance_before b3_version_before b3_snapshot_balance b3_latest_balance b3_settled_at b3_status b3_event_id b3_db_after b3_redis_after b3_version_after b3_billing_count b3_points_bill_count b3_billing_row b3_points_bill_row b3_points_bill_time
  b3_request_id="local-b3-replay-${user_id}-$(date +%s)-${RANDOM}"
  b3_balance_before="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b3_version_before="$(mysql_query "SELECT points_version FROM user WHERE id=$user_id;")"
  b3_snapshot_balance="$(B3_BEFORE="$b3_balance_before" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B3_BEFORE"]) - Decimal("3.000"))')"
  b3_latest_balance="$(B3_SNAPSHOT="$b3_snapshot_balance" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B3_SNAPSHOT"]) + Decimal("10.000"))')"
  b3_settled_at="$(date '+%Y-%m-%dT%H:%M:%S')"
  mysql_query "UPDATE user SET points=$b3_latest_balance, points_version=points_version+1 WHERE id=$user_id;"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b3_latest_balance" >/dev/null
  docker exec "$REDIS_CONTAINER" redis-cli HSET "points:settlement:$b3_request_id" requestId "$b3_request_id" actual "3.000" preDeduct "4.000" balanceAfter "$b3_snapshot_balance" databasePointsVersion "$b3_version_before" settledAt "$b3_settled_at" >/dev/null
  b3_event_id="$(mysql_query "INSERT INTO billing_settlement_outbox (request_id,user_id,actual_points,model_name,status,dispatch_attempts,process_attempts) VALUES ('$b3_request_id',$user_id,3.000,'B3_LOCAL_REPLAY','PENDING',0,0); SELECT LAST_INSERT_ID();")"
  [[ "$b3_event_id" =~ ^[0-9]+$ ]] || { echo "B3 本地重放事件创建失败：$b3_event_id" >&2; return 1; }
  b3_status="PENDING"
  for _ in {1..35}; do
    b3_status="$(mysql_query "SELECT status FROM billing_settlement_outbox WHERE id=$b3_event_id;")"
    [[ "$b3_status" == "SETTLED" ]] && break
    sleep 1
  done
  [[ "$b3_status" == "SETTLED" ]] || { echo "B3 旧快照重放未完成：outbox=$b3_status" >&2; return 1; }
  b3_db_after="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b3_redis_after="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  b3_version_after="$(mysql_query "SELECT points_version FROM user WHERE id=$user_id;")"
  b3_billing_count="$(mysql_query "SELECT COUNT(*) FROM billing_record WHERE request_id='$b3_request_id';")"
  b3_points_bill_count="$(mysql_query "SELECT COUNT(*) FROM points_bill WHERE business_id='$b3_request_id';")"
  b3_billing_row="$(mysql_query "SELECT amount,balance_before,balance_after FROM billing_record WHERE request_id='$b3_request_id';")"
  b3_points_bill_row="$(mysql_query "SELECT points_change,balance_before,balance_after FROM points_bill WHERE business_id='$b3_request_id';")"
  b3_points_bill_time="$(mysql_query "SELECT DATE_FORMAT(create_time,'%Y-%m-%dT%H:%i:%s') FROM points_bill WHERE business_id='$b3_request_id';")"
  B3_EXPECTED="$b3_latest_balance" B3_DB="$b3_db_after" B3_REDIS="$b3_redis_after" B3_BEFORE="$b3_balance_before" B3_VERSION_BEFORE="$b3_version_before" B3_VERSION_AFTER="$b3_version_after" B3_BILLING_COUNT="$b3_billing_count" B3_POINTS_BILL_COUNT="$b3_points_bill_count" B3_BILLING_ROW="$b3_billing_row" B3_POINTS_BILL_ROW="$b3_points_bill_row" B3_POINTS_BILL_TIME="$b3_points_bill_time" B3_SETTLED_AT="$b3_settled_at" python3 - <<'PY'
from decimal import Decimal
import os
expected = Decimal(os.environ["B3_EXPECTED"])
assert Decimal(os.environ["B3_DB"]) == expected, (os.environ["B3_DB"], expected)
assert Decimal(os.environ["B3_REDIS"]) == expected, (os.environ["B3_REDIS"], expected)
assert int(os.environ["B3_VERSION_AFTER"]) == int(os.environ["B3_VERSION_BEFORE"]) + 1, os.environ["B3_VERSION_AFTER"]
assert int(os.environ["B3_BILLING_COUNT"]) == 1
assert int(os.environ["B3_POINTS_BILL_COUNT"]) == 1
assert [Decimal(value) for value in os.environ["B3_BILLING_ROW"].split()] == [Decimal("3.000"), expected - Decimal("7.000"), expected - Decimal("10.000")]
assert [Decimal(value) for value in os.environ["B3_POINTS_BILL_ROW"].split()] == [Decimal("-3.000"), expected - Decimal("7.000"), expected - Decimal("10.000")]
assert os.environ["B3_POINTS_BILL_TIME"] == os.environ["B3_SETTLED_AT"], (os.environ["B3_POINTS_BILL_TIME"], os.environ["B3_SETTLED_AT"])
print("结算重放补齐账本后保留更新后的 DB/Redis 余额，流水只写入一次且保留原结算快照。")
PY
  mysql_query "UPDATE billing_settlement_outbox SET status='PENDING',settled_at=NULL,last_attempt_at=NULL WHERE id=$b3_event_id;"
  for _ in {1..20}; do
    b3_status="$(mysql_query "SELECT status FROM billing_settlement_outbox WHERE id=$b3_event_id;")"
    [[ "$b3_status" == "SETTLED" ]] && break
    sleep 1
  done
  [[ "$b3_status" == "SETTLED" ]] || { echo "B3 重复投递未能幂等完成：outbox=$b3_status" >&2; return 1; }
  b3_db_after="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b3_billing_count="$(mysql_query "SELECT COUNT(*) FROM billing_record WHERE request_id='$b3_request_id';")"
  b3_points_bill_count="$(mysql_query "SELECT COUNT(*) FROM points_bill WHERE business_id='$b3_request_id';")"
  B3_EXPECTED="$b3_latest_balance" B3_DB="$b3_db_after" B3_BILLING_COUNT="$b3_billing_count" B3_POINTS_BILL_COUNT="$b3_points_bill_count" python3 - <<'PY'
from decimal import Decimal
import os
assert Decimal(os.environ["B3_DB"]) == Decimal(os.environ["B3_EXPECTED"]), (os.environ["B3_DB"], os.environ["B3_EXPECTED"])
assert int(os.environ["B3_BILLING_COUNT"]) == 1
assert int(os.environ["B3_POINTS_BILL_COUNT"]) == 1
print("结算事件重复投递后未重复扣点、写流水或回滚余额。")
PY

  # B4: recover a reservation after its old Redis key expired, settle a received response,
  # and retain an ambiguous upstream call for manual reconciliation.
  local b4_original_balance b4_hold_balance b4_refund_id b4_refund_status b4_refund_db b4_refund_redis b4_refund_bill_count b4_refund_version_before_replay b4_refund_db_after_replay b4_refund_bill_count_after_replay b4_unpersisted_id b4_unpersisted_status b4_unpersisted_db b4_unpersisted_redis b4_unpersisted_counts b4_settle_id b4_settle_conversation_id b4_settle_status b4_settle_db b4_settle_redis b4_settle_outbox b4_settle_billing_count b4_settle_bill_count b4_manual_id b4_manual_status b4_manual_db b4_manual_redis b4_manual_error
  b4_original_balance="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b4_hold_balance="$(B4_BALANCE="$b4_original_balance" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B4_BALANCE"]) - Decimal("5.000"))')"

  b4_refund_id="local-b4-refund-${user_id}-$(date +%s)-${RANDOM}"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b4_hold_balance" >/dev/null
  docker exec "$REDIS_CONTAINER" redis-cli DEL "points:pre_deduct:$b4_refund_id" >/dev/null
  mysql_query "INSERT INTO ai_charge_intent (request_id,user_id,reserved_points,actual_points,model_name,status,reservation_confirmed,create_time,update_time) VALUES ('$b4_refund_id',$user_id,5.000,5.000,'B4_LOCAL_REFUND','RESERVED',1,DATE_SUB(NOW(), INTERVAL 10 MINUTE),DATE_SUB(NOW(), INTERVAL 10 MINUTE));"
  b4_refund_status="RESERVED"
  for _ in {1..30}; do
    b4_refund_status="$(mysql_query "SELECT status FROM ai_charge_intent WHERE request_id='$b4_refund_id';")"
    [[ "$b4_refund_status" == "REFUNDED" ]] && break
    sleep 1
  done
  [[ "$b4_refund_status" == "REFUNDED" ]] || { echo "B4 过期预扣恢复未完成退款：$b4_refund_status" >&2; return 1; }
  b4_refund_db="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b4_refund_redis="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  b4_refund_bill_count="$(mysql_query "SELECT COUNT(*) FROM points_bill WHERE business_id='$b4_refund_id' AND change_type='ROLLBACK';")"
  B4_ORIGINAL="$b4_original_balance" B4_DB="$b4_refund_db" B4_REDIS="$b4_refund_redis" B4_BILLS="$b4_refund_bill_count" python3 - <<'PY'
from decimal import Decimal
import os
expected = Decimal(os.environ["B4_ORIGINAL"])
assert Decimal(os.environ["B4_DB"]) == expected, (os.environ["B4_DB"], expected)
assert Decimal(os.environ["B4_REDIS"]) == expected, (os.environ["B4_REDIS"], expected)
assert int(os.environ["B4_BILLS"]) == 1, os.environ["B4_BILLS"]
print("B4 过期预扣恢复通过：扫描器用持久化金额退款，DB/Redis 与退款流水一致。")
PY
  b4_refund_version_before_replay="$(mysql_query "SELECT points_version FROM user WHERE id=$user_id;")"
  mysql_query "UPDATE ai_charge_intent SET status='REFUND_PENDING',update_time=DATE_SUB(NOW(), INTERVAL 10 MINUTE),recovery_claimed_at=NULL WHERE request_id='$b4_refund_id';"
  for _ in {1..20}; do
    b4_refund_status="$(mysql_query "SELECT status FROM ai_charge_intent WHERE request_id='$b4_refund_id';")"
    [[ "$b4_refund_status" == "REFUNDED" ]] && break
    sleep 1
  done
  [[ "$b4_refund_status" == "REFUNDED" ]] || { echo "B4 重复退款恢复未完成：$b4_refund_status" >&2; return 1; }
  b4_refund_db_after_replay="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b4_refund_bill_count_after_replay="$(mysql_query "SELECT COUNT(*) FROM points_bill WHERE business_id='$b4_refund_id' AND change_type='ROLLBACK';")"
  B4_DB_BEFORE="$b4_refund_db" B4_DB_AFTER="$b4_refund_db_after_replay" B4_VERSION_BEFORE="$b4_refund_version_before_replay" B4_VERSION_AFTER="$(mysql_query "SELECT points_version FROM user WHERE id=$user_id;")" B4_BILLS="$b4_refund_bill_count_after_replay" python3 - <<'PY'
from decimal import Decimal
import os
assert Decimal(os.environ["B4_DB_AFTER"]) == Decimal(os.environ["B4_DB_BEFORE"])
assert os.environ["B4_VERSION_AFTER"] == os.environ["B4_VERSION_BEFORE"]
assert int(os.environ["B4_BILLS"]) == 1, os.environ["B4_BILLS"]
print("B4 重复退款恢复通过：退款标记阻止二次加点和重复流水。")
PY

  # A complete upstream response without a durable assistant row is not chargeable.
  b4_unpersisted_id="local-b4-unpersisted-${user_id}-$(date +%s)-${RANDOM}"
  b4_hold_balance="$(B4_BALANCE="$b4_original_balance" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B4_BALANCE"]) - Decimal("5.000"))')"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b4_hold_balance" >/dev/null
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:pre_deduct:$b4_unpersisted_id" "5.000" >/dev/null
  mysql_query "INSERT INTO ai_charge_intent (request_id,user_id,reserved_points,actual_points,model_name,status,reservation_confirmed,response_received_at,create_time,update_time) VALUES ('$b4_unpersisted_id',$user_id,5.000,3.000,'B4_LOCAL_UNPERSISTED','RESPONSE_RECEIVED',1,DATE_SUB(NOW(), INTERVAL 10 MINUTE),DATE_SUB(NOW(), INTERVAL 10 MINUTE),DATE_SUB(NOW(), INTERVAL 10 MINUTE));"
  b4_unpersisted_status="RESPONSE_RECEIVED"
  for _ in {1..30}; do
    b4_unpersisted_status="$(mysql_query "SELECT status FROM ai_charge_intent WHERE request_id='$b4_unpersisted_id';")"
    [[ "$b4_unpersisted_status" == "REFUNDED" ]] && break
    sleep 1
  done
  [[ "$b4_unpersisted_status" == "REFUNDED" ]] || { echo "B4 无持久化回复的响应未退款：$b4_unpersisted_status" >&2; return 1; }
  b4_unpersisted_db="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b4_unpersisted_redis="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  b4_unpersisted_counts="$(mysql_query "SELECT COUNT(*) FROM billing_settlement_outbox WHERE request_id='$b4_unpersisted_id'; SELECT COUNT(*) FROM billing_record WHERE request_id='$b4_unpersisted_id'; SELECT COUNT(*) FROM points_bill WHERE business_id='$b4_unpersisted_id' AND change_type='ROLLBACK';")"
  B4_ORIGINAL="$b4_original_balance" B4_DB="$b4_unpersisted_db" B4_REDIS="$b4_unpersisted_redis" B4_COUNTS="$b4_unpersisted_counts" python3 - <<'PY'
from decimal import Decimal
import os
assert Decimal(os.environ["B4_DB"]) == Decimal(os.environ["B4_ORIGINAL"])
assert Decimal(os.environ["B4_REDIS"]) == Decimal(os.environ["B4_ORIGINAL"])
assert os.environ["B4_COUNTS"].splitlines() == ["0", "0", "1"]
print("B4 未持久化回复恢复通过：扫描器退款且没有生成结算事件或扣费记录。")
PY

  b4_settle_id="local-b4-settle-${user_id}-$(date +%s)-${RANDOM}"
  b4_hold_balance="$(B4_BALANCE="$b4_original_balance" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B4_BALANCE"]) - Decimal("5.000"))')"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b4_hold_balance" >/dev/null
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:pre_deduct:$b4_settle_id" "5.000" >/dev/null
  mysql_query "INSERT INTO ai_charge_intent (request_id,user_id,reserved_points,actual_points,model_name,status,reservation_confirmed,response_received_at,create_time,update_time) VALUES ('$b4_settle_id',$user_id,5.000,3.000,'B4_LOCAL_SETTLE','RESPONSE_RECEIVED',1,DATE_SUB(NOW(), INTERVAL 10 MINUTE),DATE_SUB(NOW(), INTERVAL 10 MINUTE),DATE_SUB(NOW(), INTERVAL 10 MINUTE));"
  b4_settle_conversation_id="$(mysql_query "INSERT INTO conversation (user_id,title,model,message_count,is_deleted) VALUES ($user_id,'B4 settlement recovery','B4_LOCAL_SETTLE',2,0); SELECT LAST_INSERT_ID();")"
  mysql_query "INSERT INTO chat_message (conversation_id,user_id,request_id,role,content,model,tokens) VALUES ($b4_settle_conversation_id,$user_id,'$b4_settle_id','user','B4 recovery user message','B4_LOCAL_SETTLE',0),($b4_settle_conversation_id,$user_id,'$b4_settle_id','assistant','B4 recovery assistant message','B4_LOCAL_SETTLE',8);"
  b4_settle_status="RESPONSE_RECEIVED"
  b4_settle_outbox="PENDING"
  for _ in {1..30}; do
    b4_settle_status="$(mysql_query "SELECT status FROM ai_charge_intent WHERE request_id='$b4_settle_id';")"
    b4_settle_outbox="$(mysql_query "SELECT status FROM billing_settlement_outbox WHERE request_id='$b4_settle_id';")"
    [[ "$b4_settle_status" == "SETTLED" && "$b4_settle_outbox" == "SETTLED" ]] && break
    sleep 1
  done
  [[ "$b4_settle_status" == "SETTLED" && "$b4_settle_outbox" == "SETTLED" ]] || { echo "B4 响应后结算恢复未完成：intent=$b4_settle_status outbox=$b4_settle_outbox" >&2; return 1; }
  b4_settle_db="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b4_settle_redis="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  b4_settle_billing_count="$(mysql_query "SELECT COUNT(*) FROM billing_record WHERE request_id='$b4_settle_id' AND amount=3.000;")"
  b4_settle_bill_count="$(mysql_query "SELECT COUNT(*) FROM points_bill WHERE business_id='$b4_settle_id' AND change_type='SETTLE' AND points_change=-3.000;")"
  B4_ORIGINAL="$b4_original_balance" B4_DB="$b4_settle_db" B4_REDIS="$b4_settle_redis" B4_BILLING="$b4_settle_billing_count" B4_BILLS="$b4_settle_bill_count" python3 - <<'PY'
from decimal import Decimal
import os
expected = Decimal(os.environ["B4_ORIGINAL"]) - Decimal("3.000")
assert Decimal(os.environ["B4_DB"]) == expected, (os.environ["B4_DB"], expected)
assert Decimal(os.environ["B4_REDIS"]) == expected, (os.environ["B4_REDIS"], expected)
assert int(os.environ["B4_BILLING"]) == 1
assert int(os.environ["B4_BILLS"]) == 1
print("B4 响应后结算恢复通过：outbox 幂等落账，DB/Redis 扣除实际点数且流水各一条。")
PY

  b4_manual_id="local-b4-manual-${user_id}-$(date +%s)-${RANDOM}"
  b4_hold_balance="$(B4_BALANCE="$b4_settle_db" python3 -c 'from decimal import Decimal; import os; print(Decimal(os.environ["B4_BALANCE"]) - Decimal("5.000"))')"
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b4_hold_balance" >/dev/null
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:pre_deduct:$b4_manual_id" "5.000" >/dev/null
  mysql_query "INSERT INTO ai_charge_intent (request_id,user_id,reserved_points,actual_points,model_name,status,reservation_confirmed,upstream_started_at,create_time,update_time) VALUES ('$b4_manual_id',$user_id,5.000,5.000,'B4_LOCAL_AMBIGUOUS','IN_FLIGHT',1,DATE_SUB(NOW(), INTERVAL 10 MINUTE),DATE_SUB(NOW(), INTERVAL 10 MINUTE),DATE_SUB(NOW(), INTERVAL 10 MINUTE));"
  b4_manual_status="IN_FLIGHT"
  for _ in {1..30}; do
    b4_manual_status="$(mysql_query "SELECT status FROM ai_charge_intent WHERE request_id='$b4_manual_id';")"
    [[ "$b4_manual_status" == "MANUAL_REVIEW" ]] && break
    sleep 1
  done
  [[ "$b4_manual_status" == "MANUAL_REVIEW" ]] || { echo "B4 未知上游结果未进入人工对账：$b4_manual_status" >&2; return 1; }
  b4_manual_db="$(mysql_query "SELECT points FROM user WHERE id=$user_id;")"
  b4_manual_redis="$(docker exec "$REDIS_CONTAINER" redis-cli GET "points:user:$user_id")"
  b4_manual_error="$(mysql_query "SELECT COALESCE(last_error,'') FROM ai_charge_intent WHERE request_id='$b4_manual_id';")"
  B4_DB="$b4_manual_db" B4_REDIS="$b4_manual_redis" B4_HOLD="$b4_hold_balance" B4_ERROR="$b4_manual_error" python3 - <<'PY'
from decimal import Decimal
import os
assert Decimal(os.environ["B4_DB"]) == Decimal(os.environ["B4_HOLD"]) + Decimal("5.000")
assert Decimal(os.environ["B4_REDIS"]) == Decimal(os.environ["B4_HOLD"])
assert os.environ["B4_ERROR"], "manual-review reason missing"
print("B4 未知上游结果通过：预扣保留、未退款，状态和人工对账原因已持久化。")
PY

  # Remove only the isolated B4 fixtures and restore the local smoke user's initial balance.
  mysql_query "DELETE FROM chat_message WHERE request_id IN ('$b4_unpersisted_id','$b4_settle_id'); DELETE FROM conversation WHERE id=$b4_settle_conversation_id AND user_id=$user_id; DELETE FROM points_bill WHERE business_id IN ('$b4_refund_id','$b4_unpersisted_id','$b4_settle_id','$b4_manual_id'); DELETE FROM billing_record WHERE request_id IN ('$b4_unpersisted_id','$b4_settle_id'); DELETE FROM billing_settlement_outbox WHERE request_id IN ('$b4_refund_id','$b4_unpersisted_id','$b4_settle_id','$b4_manual_id'); DELETE FROM ai_charge_intent WHERE request_id IN ('$b4_refund_id','$b4_unpersisted_id','$b4_settle_id','$b4_manual_id'); UPDATE user SET points=$b4_original_balance, points_version=points_version+1 WHERE id=$user_id;"
  docker exec "$REDIS_CONTAINER" redis-cli DEL "points:pre_deduct:$b4_refund_id" "points:pre_deduct:$b4_unpersisted_id" "points:pre_deduct:$b4_settle_id" "points:pre_deduct:$b4_manual_id" "points:refund:$b4_refund_id" "points:refund:$b4_unpersisted_id" "points:settlement:$b4_settle_id" >/dev/null
  docker exec "$REDIS_CONTAINER" redis-cli SET "points:user:$user_id" "$b4_original_balance" >/dev/null

  docker exec "$REDIS_CONTAINER" redis-cli DEL "rate_limit:user:$user_id" >/dev/null

  stream_payload='{"model":"Local Mock (Codex)","messages":[{"role":"user","content":"请只回复 LOCAL_STREAM_OK"}],"stream":true,"disableSearch":true}'
  stream_response="$(curl --silent --show-error --fail --max-time 60 -N -X POST "$LOCAL_TARGET/api/chat/stream" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$stream_payload")"
  STREAM_BODY="$stream_response" python3 - <<'PY'
import os
body = os.environ["STREAM_BODY"]
assert "LOCAL_STREAM_OK" in body, body
assert "event:done" in body or "event: done" in body, body
print("SSE AI 对话通过：收到模型内容与 done 终态事件。")
PY
  user_sse_rate_count="$(docker exec "$REDIS_CONTAINER" redis-cli ZCARD "rate_limit:user:$user_id")"
  [[ "$user_sse_rate_count" == "1" ]] || { echo "用户 Token SSE 应只消费一次用户限流，实际：$user_sse_rate_count" >&2; return 1; }

  api_key_payload='{"name":"Local SSE smoke","rateLimit":1}'
  api_key_response="$(curl --silent --show-error --fail --max-time 15 -X POST "$LOCAL_TARGET/api/api-key/generate" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$api_key_payload")"
  api_key="$(printf '%s' "$api_key_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p.get("message"); print(p["data"]["apiKey"])')"
  api_key_id="$(printf '%s' "$api_key_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])')"
  seed_user_rate_window "$user_id" 60
  api_stream_response="$(curl --silent --show-error --fail --max-time 60 -N -X POST "$LOCAL_TARGET/api/chat/stream" -H 'Content-Type: application/json' -H "X-API-Key: $api_key" --data-binary "$stream_payload")"
  API_STREAM_BODY="$api_stream_response" python3 - <<'PY'
import os
body = os.environ["API_STREAM_BODY"]
assert "LOCAL_STREAM_OK" in body, body
assert "event:done" in body or "event: done" in body, body
print("API Key SSE 通过：限流阈值为 1 时，单次流式请求正常收到 done。")
PY
  rate_count="$(docker exec "$REDIS_CONTAINER" redis-cli GET "ai_gateway:rate_limit:$api_key")"
  [[ "$rate_count" == "1" ]] || { echo "API Key SSE 预期仅计 1 次限流，实际：$rate_count" >&2; return 1; }
  docker exec "$REDIS_CONTAINER" redis-cli DEL "rate_limit:user:$user_id" >/dev/null

  mock_stats="$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:19090/stats)"
  MOCK_STATS="$mock_stats" python3 - <<'PY'
import json, os
payload = json.loads(os.environ["MOCK_STATS"])
assert payload.get("requests", 0) >= 3, payload
assert payload.get("streamingRequests", 0) >= 2, payload
print(f"模拟上游收到了 {payload['requests']} 次请求，其中 {payload['streamingRequests']} 次为流式。")
PY

  revoke_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/user/login-devices/remove-all" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary '{"exceptCurrent":"false"}')"
  printf '%s' "$revoke_response" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("code")==200,p; print("本地测试账号已通过设备管理接口退出所有设备。")'
  revoked_info_response="$(curl --silent --show-error --fail --max-time 10 "$LOCAL_TARGET/api/user/info" -H "X-User-Token: $token")"
  revoked_chat_response="$(curl --silent --show-error --fail --max-time 10 -X POST "$LOCAL_TARGET/api/chat/completions" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$chat_payload")"
  revoked_stream_response="$(curl --silent --show-error --fail --max-time 10 -N -X POST "$LOCAL_TARGET/api/chat/stream" -H 'Content-Type: application/json' -H "X-User-Token: $token" --data-binary "$stream_payload")"
  REVOKED_INFO="$revoked_info_response" REVOKED_CHAT="$revoked_chat_response" REVOKED_STREAM="$revoked_stream_response" python3 - <<'PY'
import json, os
responses = {
    "ordinary API": json.loads(os.environ["REVOKED_INFO"]),
    "OpenAI-compatible chat": json.loads(os.environ["REVOKED_CHAT"]),
    "streaming chat": json.loads(os.environ["REVOKED_STREAM"]),
}
for name, response in responses.items():
    assert response.get("code") == 401, (name, response)
print("Token 撤销回归通过：普通 API、OpenAI 兼容聊天与 SSE 均拒绝旧 Token。")
PY

  counts="$(mysql_query "SELECT COUNT(*) FROM chat_message WHERE user_id=$user_id; SELECT COUNT(*) FROM billing_settlement_outbox WHERE user_id=$user_id AND status='SETTLED';")"
  COUNTS="$counts" python3 - <<'PY'
import os
values = [int(line) for line in os.environ["COUNTS"].splitlines() if line.strip()]
assert len(values) == 2 and values[0] >= 8 and values[1] >= 4, values
print(f"本地持久化与结算通过：写入 {values[0]} 条消息，完成 {values[1]} 笔结算。")
PY
  if [[ "$DEEPSEEK_FALLBACK_TEST_ACTIVE" == "true" ]]; then
    cleanup_local_deepseek_fallback_test
  fi
  echo "本地 AI 功能冒烟验证全部通过。测试账号：${username}（仅在本地数据库）"
}

status() {
  ensure_local_env
  echo "本地容器："
  docker ps --filter "label=com.docker.compose.project=$PROJECT" --format '  {{.Names}}  {{.Status}}  {{.Ports}}' 2>/dev/null || true
  local name="" marker="" pid=""
  for name in backend web mock; do
    case "$name" in
      backend) marker="$BACKEND_JAR" ;;
      web) marker="$WEB_DIR/node_modules/vite/bin/vite.js" ;;
      mock) marker="$ROOT/scripts/local-openai-mock.mjs" ;;
    esac
    if [[ -f "$RUNTIME/$name.pid" ]]; then
      pid="$(cat "$RUNTIME/$name.pid")"
      if process_matches "$pid" "$marker"; then
        echo "进程 $name: running (PID $pid)"
      else
        echo "进程 $name: stopped/stale PID file"
      fi
    else
      echo "进程 $name: stopped"
    fi
  done
  echo "前端 API 代理模式：$(cat "$RUNTIME/mode" 2>/dev/null || echo 未设置)"
  echo "前端 API 目标：$(cat "$RUNTIME/api-target" 2>/dev/null || echo 未设置)"
  echo "运行日志目录：$RUNTIME/logs"
}

down() {
  stop_managed_process web "$WEB_DIR/node_modules/vite/bin/vite.js"
  stop_managed_process backend "$BACKEND_JAR"
  stop_managed_process mock "$ROOT/scripts/local-openai-mock.mjs"
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    ensure_local_env
    compose down
  fi
  rm -f "$RUNTIME/mode" "$RUNTIME/api-target"
  echo "本地服务已停止；专用 Docker 数据卷已保留。"
}

logs() {
  local service="${1:-backend}" file
  case "$service" in
    backend) file="$RUNTIME/logs/backend.log" ;;
    web) file="$RUNTIME/logs/web.log" ;;
    mock) file="$RUNTIME/logs/mock.log" ;;
    *) echo "未知日志类型：$service" >&2; return 2 ;;
  esac
  [[ -f "$file" ]] || { echo "日志文件不存在：$file"; return 0; }
  tail -n 100 "$file"
}

command_name="${1:-help}"
case "$command_name" in
  up)
    up
    ;;
  switch)
    case "${2:-}" in
      local)
        up
        ;;
      online)
        [[ -n "$NODE_BIN" ]] || { echo "未找到 Node.js。" >&2; exit 1; }
        ensure_local_env
        start_web "$ONLINE_TARGET"
        printf 'online\n' > "$RUNTIME/mode"
        echo "前端代理现指向线上 API：$ONLINE_TARGET。此操作只切换本地代理，没有部署或写入线上。"
        ;;
      *) echo "用法：$0 switch local|online" >&2; exit 2 ;;
    esac
    ;;
  status)
    status
    ;;
  verify)
    verify
    ;;
  verify-chat)
    verify_chat
    ;;
  stop-backend)
    stop_managed_process backend "$BACKEND_JAR"
    ;;
  start-backend)
    check_docker
    [[ -n "$JAVA_BIN" ]] || { echo "未找到 Java。" >&2; exit 1; }
    ensure_local_env
    load_local_env
    wait_container_healthy "$MYSQL_CONTAINER"
    wait_container_healthy "$REDIS_CONTAINER"
    start_backend
    ;;
  down)
    down
    ;;
  logs)
    logs "${2:-backend}"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
