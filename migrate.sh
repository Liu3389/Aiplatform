#!/bin/bash
set -e

# ============================================
# 跨设备数据迁移脚本
# 
# 用法:
#   源设备: ./migrate.sh export    → 生成迁移包
#   新设备: ./migrate.sh import    → 导入迁移包
# 
# 最快方式（直接 scp）:
#   ./migrate.sh push user@10.211.55.10:/path/to/Aiplatform
# ============================================

cd "$(dirname "$0")"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

ACTION="${1:-help}"

case "$ACTION" in
  export)
    echo -e "${CYAN}============================================${NC}"
    echo -e "${CYAN}   导出迁移数据包${NC}"
    echo -e "${CYAN}============================================${NC}"
    
    if [ -f "./backup.sh" ]; then
      ./backup.sh
    else
      echo -e "${RED}未找到 backup.sh 脚本${NC}"
      exit 1
    fi
    
    echo -e "${GREEN}迁移数据包已生成在 backups/ 目录${NC}"
    echo -e "将该文件拷贝到新设备的项目 backups/ 目录下"
    echo -e "然后在新设备执行: ${CYAN}./migrate.sh import${NC}"
    ;;

  import)
    echo -e "${CYAN}============================================${NC}"
    echo -e "${CYAN}   导入迁移数据包${NC}"
    echo -e "${CYAN}============================================${NC}"
    
    LATEST=$(ls -1t backups/lingdian_backup_*.tar.gz 2>/dev/null | head -1)
    
    if [ -z "$LATEST" ]; then
      echo -e "${RED}[错误] backups/ 目录下未找到备份文件${NC}"
      echo -e "${YELLOW}请先将迁移数据包 .tar.gz 放入 backups/ 目录${NC}"
      exit 1
    fi
    
    echo -e "  找到备份: ${CYAN}${LATEST}${NC}"
    
    if [ -f "./restore.sh" ]; then
      ./restore.sh "$LATEST"
    else
      echo -e "${RED}未找到 restore.sh 脚本${NC}"
      exit 1
    fi
    ;;

  push)
    if [ -z "$2" ]; then
      echo -e "${YELLOW}用法: ./migrate.sh push <目标路径>${NC}"
      echo -e "示例: ${CYAN}./migrate.sh push user@10.211.55.10:~/Aiplatform${NC}"
      exit 1
    fi
    
    DEST="$2"
    
    echo -e "${CYAN}正在打包源数据...${NC}"
    ./migrate.sh export
    
    LATEST=$(ls -1t backups/lingdian_backup_*.tar.gz 2>/dev/null | head -1)
    if [ -z "$LATEST" ]; then
      echo -e "${RED}打包失败${NC}"
      exit 1
    fi
    
    echo -e "${CYAN}正在传输到目标设备 ($DEST)...${NC}"
    scp "$LATEST" "${DEST}/backups/"
    
    echo -e "${GREEN}迁移数据已发送到目标设备${NC}"
    echo -e "${YELLOW}在目标设备上执行:${NC}"
    echo -e "  cd ~/Aiplatform && docker compose up -d  # 先启动服务"
    echo -e "  cd ~/Aiplatform && ./migrate.sh import    # 再导入数据"
    ;;

  help|*)
    echo -e "${CYAN}凌点 AI 网关 - 数据迁移工具${NC}"
    echo ""
    echo "用法: ./migrate.sh <操作>"
    echo ""
    echo "操作:"
    echo "  export                 导出当前数据为迁移包"
    echo "  import                 从 backups/ 目录导入最新迁移包"
    echo "  push <目标路径>         导出并 scp 到目标设备"
    echo ""
    echo "示例 - 同设备备份恢复（三步）:"
    echo "  ./backup.sh             # 备份当前数据"
    echo "  ./restore.sh backups/xxx.tar.gz  # 恢复指定备份"
    echo ""
    echo "示例 - 跨设备迁移（两步）:"
    echo "  源设备: ./migrate.sh push user@10.211.55.10:~/Aiplatform"
    echo "  目标设备: cd ~/Aiplatform && ./start.sh && ./migrate.sh import"
    echo ""
    echo "示例 - 手动迁移："
    echo "  源设备: ./migrate.sh export"
    echo "  拷贝: scp backups/xxx.tar.gz 新设备"
    echo "  新设备: ./migrate.sh import"
    ;;
esac
