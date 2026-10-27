#!/bin/bash
# =============================================================================
# 恢复微信支付服务 (备用脚本)
# 用途: 当需要重新启用 lingdian-ai.top 域名上的微信支付时执行
# 注意: 执行后 lingdian-ai.top 将切回微信支付, 凌点AI平台将不再通过该域名访问
#       (平台访问地址由部署者自行配置)
# =============================================================================
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 1. 启动 PayServer (8081) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cd /opt/wechat-pay && \
  if pgrep -f '[j]ava -Xmx128m.*PayServer' >/dev/null; then \
    echo '  PayServer 已在运行'; \
  else \
    nohup java -Xmx128m -Dfile.encoding=UTF-8 PayServer > server.log 2>&1 & \
    sleep 2 && pgrep -f '[j]ava -Xmx128m.*PayServer' >/dev/null && echo '  ✅ PayServer 已启动' || echo '  ❌ 启动失败, 查看 server.log'; \
  fi"

echo ""
echo "===== 2. 恢复 nginx 微信支付配置 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "if [ -f /etc/nginx/conf.d/lingdian-ai.conf.wechatpay.bak ]; then \
  cp /etc/nginx/conf.d/lingdian-ai.conf /etc/nginx/conf.d/lingdian-ai.conf.lingdianai.bak; \
  cp /etc/nginx/conf.d/lingdian-ai.conf.wechatpay.bak /etc/nginx/conf.d/lingdian-ai.conf; \
  nginx -t && systemctl reload nginx && echo '  ✅ 已切回微信支付配置(凌点AI配置备份为 .lingdianai.bak)'; \
else echo '  ❌ 未找到 .wechatpay.bak 备份'; fi"

echo ""
echo "===== 3. 验证 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ss -tlnp 2>/dev/null | grep -E ':80 |:443 |:8081 '"
curl -s -m 10 -o /dev/null -w 'https://lingdian-ai.top -> %{http_code}\n' https://lingdian-ai.top/
