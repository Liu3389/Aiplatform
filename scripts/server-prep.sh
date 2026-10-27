#!/bin/bash
# 服务器准备: 停止 nginx 释放80端口 + 排查8081端口java进程
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 1. 排查 8081 端口 java 进程 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ps -p 6676 -o pid,ppid,cmd --no-headers 2>/dev/null; echo '---所有java进程---'; pgrep -af java 2>/dev/null || echo '无java进程'"

echo ""
echo "===== 2. 停止并禁用 nginx ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "systemctl stop nginx && systemctl disable nginx 2>&1; echo '-> nginx 已停止'; systemctl is-active nginx 2>/dev/null || echo '-> 确认 nginx 未运行'"

echo ""
echo "===== 3. 杀掉占用8081的旧java进程(若是残留后端) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "if [ -f /proc/6676/cmdline ]; then cmd=\$(tr '\\0' ' ' < /proc/6676/cmdline 2>/dev/null); if echo \"\$cmd\" | grep -q 'ai_gateway\|app.jar\|aiplatform'; then kill 6676 2>/dev/null && echo '已杀掉残留后端进程 6676'; else echo '8081进程非lingdian后端，保留: '\$cmd; fi; else echo '进程6676已不存在'; fi"

echo ""
echo "===== 4. 确认80端口已释放 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ss -tlnp 2>/dev/null | grep ':80 ' || echo '-> 80端口已空闲'"
