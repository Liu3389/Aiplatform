#!/bin/bash
set -e

cd "$(dirname "$0")"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

source .env 2>/dev/null || true
BACKUP_DIR="${BACKUP_DIR:-./backups}"
MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123}"
REDIS_PASSWORD="${REDIS_PASSWORD:-redis123}"

mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/lingdian_backup_${TIMESTAMP}.tar.gz"
WORK_DIR="${BACKUP_DIR}/tmp_${TIMESTAMP}"
mkdir -p "$WORK_DIR"

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}   数据备份${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

# --- 备份 MySQL ---
echo -e "${CYAN}[1/3] 备份 MySQL...${NC}"
docker exec lingdian-mysql mysqldump \
  -uroot -p"${MYSQL_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  --databases "${MYSQL_DATABASE:-ai_gateway}" \
  > "${WORK_DIR}/mysql_backup.sql" 2>/dev/null

if [ -s "${WORK_DIR}/mysql_backup.sql" ]; then
  SQL_SIZE=$(wc -c < "${WORK_DIR}/mysql_backup.sql" | tr -d ' ')
  echo -e "  ${GREEN}MySQL 已备份 (${SQL_SIZE} bytes)${NC}"
else
  echo -e "  ${YELLOW}MySQL 备份为空，可能数据库未初始化${NC}"
fi

# --- 备份 Redis ---
echo -e "${CYAN}[2/3] 备份 Redis...${NC}"
docker exec lingdian-redis redis-cli -a "${REDIS_PASSWORD}" --rdb /data/dump.rdb 2>/dev/null
docker cp lingdian-redis:/data/dump.rdb "${WORK_DIR}/redis_dump.rdb" 2>/dev/null

if [ -f "${WORK_DIR}/redis_dump.rdb" ]; then
  RDB_SIZE=$(wc -c < "${WORK_DIR}/redis_dump.rdb" | tr -d ' ')
  echo -e "  ${GREEN}Redis 已备份 (${RDB_SIZE} bytes)${NC}"
else
  echo -e "  ${YELLOW}Redis 备份文件导出失败，尝试直接复制${NC}"
  docker cp lingdian-redis:/data/appendonly.aof "${WORK_DIR}/redis_appendonly.aof" 2>/dev/null || true
fi

# --- 打包 ---
echo -e "${CYAN}[3/3] 打包备份文件...${NC}"

# 生成备份信息
cat > "${WORK_DIR}/backup_info.txt" << INFOEOF
备份时间: $(date)
设备信息: $(hostname) / $(uname -a)
MySQL数据库: ${MYSQL_DATABASE:-ai_gateway}
INFOEOF

tar -czf "$BACKUP_FILE" -C "$WORK_DIR" .
rm -rf "$WORK_DIR"

BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}   备份完成${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "  文件: ${CYAN}${BACKUP_FILE}${NC}"
echo -e "  大小: ${CYAN}${BACKUP_SIZE}${NC}"
echo ""
echo -e "  ${YELLOW}将备份文件拷贝到新设备:${NC}"
echo -e "  ${CYAN}scp ${BACKUP_FILE} user@new-device:~/Aiplatform/backups/${NC}"
echo ""
