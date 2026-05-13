#!/bin/bash
set -e

cd "$(dirname "$0")"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

source .env 2>/dev/null || true
MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123}"
REDIS_PASSWORD="${REDIS_PASSWORD:-redis123}"

RESTORE_FILE="$1"

if [ -z "$RESTORE_FILE" ]; then
  echo -e "${YELLOW}用法: ./restore.sh <备份文件.tar.gz>${NC}"
  echo ""
  echo "可用的备份文件:"
  ls -1 backups/*.tar.gz 2>/dev/null | while read f; do
    echo "  $f ($(du -h "$f" | cut -f1))"
  done
  exit 1
fi

if [ ! -f "$RESTORE_FILE" ]; then
  echo -e "${RED}[错误] 文件不存在: $RESTORE_FILE${NC}"
  exit 1
fi

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   数据恢复${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

WORK_DIR="${RESTORE_FILE%.tar.gz}_restore"
rm -rf "$WORK_DIR" 2>/dev/null || true
mkdir -p "$WORK_DIR"

echo -e "${CYAN}[1/4] 解压备份文件...${NC}"
tar -xzf "$RESTORE_FILE" -C "$WORK_DIR"

# 显示备份信息
if [ -f "$WORK_DIR/backup_info.txt" ]; then
  echo -e "  ${YELLOW}备份信息:${NC}"
  cat "$WORK_DIR/backup_info.txt" | while read line; do echo -e "  ${YELLOW}$line${NC}"; done
  echo ""
fi

echo ""
echo -n -e "${YELLOW}⚠ 此操作将覆盖当前所有数据，确认继续? [y/N] ${NC}"
read -r CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
  echo "已取消"
  rm -rf "$WORK_DIR"
  exit 0
fi

# --- 恢复 MySQL ---
echo -e "${CYAN}[2/4] 恢复 MySQL...${NC}"
if [ -f "$WORK_DIR/mysql_backup.sql" ]; then
  docker exec -i lingdian-mysql mysql -uroot -p"${MYSQL_PASSWORD}" < "$WORK_DIR/mysql_backup.sql" 2>/dev/null
  echo -e "  ${GREEN}MySQL 已恢复${NC}"
else
  echo -e "  ${YELLOW}备份中没有 MySQL 数据${NC}"
fi

# --- 恢复 Redis ---
echo -e "${CYAN}[3/4] 恢复 Redis...${NC}"
if [ -f "$WORK_DIR/redis_dump.rdb" ] || [ -f "$WORK_DIR/redis_appendonly.aof" ]; then
  docker exec lingdian-redis redis-cli -a "${REDIS_PASSWORD}" FLUSHALL 2>/dev/null || true
  if [ -f "$WORK_DIR/redis_dump.rdb" ]; then
    docker cp "$WORK_DIR/redis_dump.rdb" lingdian-redis:/data/dump.rdb 2>/dev/null
    docker restart lingdian-redis 2>/dev/null
    echo -e "  ${GREEN}Redis 已恢复（RDB）${NC}"
  fi
else
  echo -e "  ${YELLOW}备份中没有 Redis 数据${NC}"
fi

# --- 重启后端加载新数据 ---
echo -e "${CYAN}[4/4] 重启后端服务...${NC}"
docker compose restart ai-gateway-app 2>/dev/null

rm -rf "$WORK_DIR"

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}   恢复完成！${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "  前端页面:  ${CYAN}http://localhost${NC}"
echo ""
