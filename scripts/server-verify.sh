#!/bin/bash
# 验证后端完全启动 + 测试公网访问
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 1. 后端启动日志(查 Started / ERROR) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker logs lingdian-backend --tail 50 2>&1 | grep -E 'Started AiGateway|ERROR|Exception|Tomcat started|Application run failed|端口|8080' | tail -20"

echo ""
echo "===== 2. 后端容器状态(是否重启过) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "docker ps --filter name=lingdian-backend --format '{{.Status}} | 重启次数:' && docker inspect lingdian-backend --format '{{.RestartCount}}'"

echo ""
echo "===== 3. 服务器本机测试后端API ====="
backend_health="$(ssh $SSH_OPTS -i "$KEY" "$HOST" "curl --silent --show-error --fail --max-time 5 http://127.0.0.1:8080/api/actuator/health")"
printf '%s' "$backend_health" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("status")=="UP",p.get("status"); print("服务器后端 Actuator 健康：UP")'

echo ""
echo "===== 4. 服务器本机测试前端Nginx ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "curl -s -m 5 -o /dev/null -w '前端首页 -> HTTP %{http_code}\n' http://127.0.0.1:80/; curl -s -m 5 -o /dev/null -w '前端/api代理 -> HTTP %{http_code}\n' http://127.0.0.1:80/api/auth/userinfo"

echo ""
echo "===== 5. 公网 HTTPS 测试(lingdian-ai.top) ====="
curl --silent --show-error --location --max-time 15 --output /dev/null --write-out '公网首页 -> HTTP %{http_code}, 耗时%{time_total}s\n' https://lingdian-ai.top/
public_health="$(curl --silent --show-error --fail --max-time 10 https://lingdian-ai.top/api/actuator/health)"
printf '%s' "$public_health" | python3 -c 'import json,sys; p=json.load(sys.stdin); assert p.get("status")=="UP",p.get("status"); print("公网 HTTPS Actuator 健康：UP")'
