#!/bin/bash
# 诊断 lingdian-ai.top 域名 + HTTPS 问题
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 1. 域名 DNS 解析 (本地Mac) ====="
dig +short lingdian-ai.top 2>/dev/null || nslookup lingdian-ai.top 2>/dev/null | grep -A2 Name
if [[ -n "${LINGDIAN_EXPECTED_IPV4:-}" ]]; then echo "--- 配置的期望地址：$LINGDIAN_EXPECTED_IPV4 ---"; fi

echo ""
echo "===== 2. 服务器端口监听 (80/443) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ss -tlnp 2>/dev/null | grep -E ':80 |:443 ' || echo '  80/443 无监听'"

echo ""
echo "===== 3. Nginx 状态 + 配置文件 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "systemctl is-enabled nginx 2>/dev/null; systemctl is-active nginx 2>/dev/null; echo '--- nginx 配置文件 ---'; ls -la /etc/nginx/conf.d/ 2>/dev/null; ls -la /etc/nginx/sites-enabled/ 2>/dev/null; echo '--- 主配置 server_name ---'; grep -rn 'server_name\|listen\|ssl_certificate\|proxy_pass' /etc/nginx/ 2>/dev/null | grep -i 'lingdian\|443\|ssl_cert\|proxy_pass' | head -30"

echo ""
echo "===== 4. SSL 证书文件 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "find /etc/nginx /etc/letsencrypt /root -maxdepth 4 \\( -name '*.pem' -o -name '*.crt' -o -name '*.key' \\) 2>/dev/null | grep -iv 'fortest' | head -20; echo '--- letsencrypt 目录 ---'; ls -la /etc/letsencrypt/live/ 2>/dev/null || echo '无 letsencrypt 证书'"

echo ""
echo "===== 5. 本地直连测试 (HTTP/HTTPS) ====="
echo "--- HTTPS 443 ---"
curl -s -m 10 -o /dev/null -w 'https://lingdian-ai.top -> HTTP %{http_code}, 耗时%{time_total}s\n' https://lingdian-ai.top/ 2>&1 || echo "  HTTPS 连接失败"
echo "--- HTTP 80 ---"
curl -s -m 10 -o /dev/null -w 'http://lingdian-ai.top -> HTTP %{http_code}\n' http://lingdian-ai.top/ 2>&1
echo "--- HTTPS 详细错误 ---"
curl -sv -m 10 https://lingdian-ai.top/ 2>&1 | grep -E 'Connected|SSL|certificate|HTTP/|error|refused|timed out|Could not' | head -10
