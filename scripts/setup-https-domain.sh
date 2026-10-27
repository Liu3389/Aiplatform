#!/bin/bash
# =============================================================================
# 配置 lingdian-ai.top HTTPS -> 凌点AI平台
# 1. 停微信支付 PayServer  2. 前端容器让出80改绑127.0.0.1:8088
# 3. 备份原nginx配置+换新  4. 启用nginx(80跳转+443SSL)  5. 测试
# =============================================================================
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"
REMOTE_DIR=/root/lingdian

echo "===== 1. 停止微信支付 PayServer (保留文件) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "if pgrep -f 'java.*PayServer' >/dev/null; then pkill -f 'java.*PayServer' && echo '  PayServer 已停止'; sleep 1; pgrep -f 'java.*PayServer' && echo '  警告:仍存在' || echo '  确认已停止'; else echo '  PayServer 未运行'; fi"

echo ""
echo "===== 2. 传输新 compose + nginx 配置到服务器 ====="
scp $SSH_OPTS -i "$KEY" \
  docker-compose.server.yml \
  "$HOST:$REMOTE_DIR/docker-compose.server.yml"
scp $SSH_OPTS -i "$KEY" \
  scripts/nginx-lingdian-ai.conf \
  "$HOST:/tmp/nginx-lingdian-ai.conf"
echo "  -> 传输完成"

echo ""
echo "===== 3. 备份原 nginx 配置并替换 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cp /etc/nginx/conf.d/lingdian-ai.conf /etc/nginx/conf.d/lingdian-ai.conf.wechatpay.bak && echo '  原配置已备份为 .wechatpay.bak'; cp /tmp/nginx-lingdian-ai.conf /etc/nginx/conf.d/lingdian-ai.conf && echo '  新配置已写入'"

echo ""
echo "===== 4. Recreate 前端容器 (改绑 127.0.0.1:8088, 释放公网80) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cd $REMOTE_DIR && docker compose -f docker-compose.server.yml --env-file .env.prod up -d frontend 2>&1 | tail -8"

echo ""
echo "===== 5. nginx -t 测试配置 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "nginx -t 2>&1"

echo ""
echo "===== 6. 启用并启动 nginx ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "systemctl enable nginx >/dev/null 2>&1 && echo '  nginx 已设置开机自启'; systemctl restart nginx && echo '  nginx 已启动'; systemctl is-active nginx"

echo ""
echo "===== 7. 端口监听确认 (80/443) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ss -tlnp 2>/dev/null | grep -E ':80 |:443 |:8088 |:8080 '"

echo ""
echo "===== 8. 测试 https://lingdian-ai.top ====="
curl -s -m 15 -o /dev/null -w 'HTTPS 首页 -> %{http_code} (%{time_total}s)\n' https://lingdian-ai.top/ 2>&1
curl -s -m 15 -o /dev/null -w 'HTTPS /api/actuator/health -> %{http_code}\n' https://lingdian-ai.top/api/actuator/health 2>&1
echo "--- HTTP 跳转 ---"
curl -s -m 10 -o /dev/null -w 'HTTP 首页 -> %{http_code} (期望301)\n' http://lingdian-ai.top/ 2>&1
