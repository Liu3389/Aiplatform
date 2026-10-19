#!/usr/bin/env bash
set -euo pipefail

if (( $# > 1 )); then
  echo "用法: $0 [--local-only]" >&2
  exit 2
fi
PREFLIGHT_MODE="${1:-full}"
if [[ "$PREFLIGHT_MODE" != "full" && "$PREFLIGHT_MODE" != "--local-only" ]]; then
  echo "用法: $0 [--local-only]" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RESTART_BACKEND_ON_EXIT=0

restore_backend_on_exit() {
  local exit_code=$?
  trap - EXIT
  if [[ "$RESTART_BACKEND_ON_EXIT" == "1" ]]; then
    echo "恢复本地 backend……"
    if ! "$ROOT/scripts/ai-local-env.sh" start-backend; then
      echo "本地 backend 未能自动恢复，请运行 ./scripts/ai-local-env.sh start-backend。" >&2
      exit_code=1
    fi
  fi
  exit "$exit_code"
}
trap restore_backend_on_exit EXIT

stop_managed_backend_for_tests() {
  RESTART_BACKEND_ON_EXIT=1
  "$ROOT/scripts/ai-local-env.sh" stop-backend
}

restore_backend_after_tests() {
  if [[ "$RESTART_BACKEND_ON_EXIT" == "1" ]]; then
    echo "Maven 测试结束，恢复本地服务……"
    "$ROOT/scripts/ai-local-env.sh" start-backend
    RESTART_BACKEND_ON_EXIT=0
  fi
}

echo "验证隔离本地服务、认证边界和 API 代理……"
"$ROOT/scripts/ai-local-env.sh" verify

echo "运行后端全量测试……"
stop_managed_backend_for_tests
(
  cd "$ROOT/Aiplatform-demo"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.runtime/local.env"
  set +a
  mvn -q -Dapp.messaging.rabbit-enabled=false test
)
restore_backend_after_tests

echo "构建前端并验证生产包……"
(
  cd "$ROOT/Aiplatform-web"
  npm run build
)

echo "运行验证码、模拟模型、SSE 与结算端到端回归……"
"$ROOT/scripts/ai-local-env.sh" verify-chat

if [[ "$PREFLIGHT_MODE" == "--local-only" ]]; then
  echo "本地功能验收全部通过；目标服务器生产配置须由部署脚本在传输前只读校验。"
else
  echo "最后检查本机受控生产密钥配置……"
  python3 "$ROOT/scripts/check-production-env.py"
  echo "本机发布前置验收全部通过；此脚本没有执行远程传输或部署。"
fi
