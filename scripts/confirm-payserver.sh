#!/bin/bash
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 精确查 PayServer (排除 grep 自身) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ps -ef | grep '[j]ava -Xmx128m' | grep PayServer && echo '⚠️ PayServer 仍在运行' || echo '✅ PayServer 已停止'"

echo ""
echo "===== 8081 端口 (PayServer 原端口) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ss -tlnp 2>/dev/null | grep ':8081' && echo '⚠️ 8081 仍监听' || echo '✅ 8081 无监听 (PayServer 已停)'"

echo ""
echo "===== 保留的微信支付文件确认 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ls /opt/wechat-pay/ >/dev/null 2>&1 && echo '✅ /opt/wechat-pay/ 文件保留' ; ls /etc/nginx/conf.d/lingdian-ai.conf.wechatpay.bak >/dev/null 2>&1 && echo '✅ 原nginx配置备份保留(.wechatpay.bak)'"
