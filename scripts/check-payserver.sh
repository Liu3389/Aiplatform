#!/bin/bash
KEY="${LINGDIAN_SSH_KEY:?Set LINGDIAN_SSH_KEY to the SSH private-key file path}"
HOST="${LINGDIAN_SSH_TARGET:?Set LINGDIAN_SSH_TARGET to user@host}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=20"

echo "===== 1. PayServer 进程详情 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ps -ef | grep -i '[P]ayServer'; echo '---启动方式---'; cat /proc/6676/cmdline 2>/dev/null | tr '\\0' ' '; echo; echo '---父进程---'; ps -o pid,ppid,cmd -p \$(awk '{print \$4}' /proc/6676/stat 2>/dev/null) 2>/dev/null"

echo ""
echo "===== 2. 查 systemd 服务 (pay/wechat) ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "systemctl list-unit-files 2>/dev/null | grep -iE 'pay|wechat'; echo '---running---'; systemctl list-units --type=service --state=running 2>/dev/null | grep -iE 'pay|wechat' || echo '无 pay/wechat systemd 服务'"

echo ""
echo "===== 3. 查 rc.local / crontab 自启 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "cat /etc/rc.local 2>/dev/null | grep -iv '^#' | grep -v '^\$'; echo '---root crontab---'; crontab -l 2>/dev/null | grep -iv '^#' | grep -v '^\$' || echo '无 crontab'"

echo ""
echo "===== 4. PayServer 工作目录/文件 ====="
ssh $SSH_OPTS -i "$KEY" "$HOST" "ls -la /opt/wechat-pay/ 2>/dev/null | head -15; echo '---jar位置---'; ls -la /root/*.jar /opt/*/*.jar 2>/dev/null | head"
