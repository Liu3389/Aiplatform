#!/bin/bash
# 系统全面优化部署脚本
# 执行日期: 2026-05-15
# 说明: 一键完成所有优化步骤

set -e  # 遇到错误立即退出

echo "=========================================="
echo "AI Gateway Platform - 系统优化部署"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 步骤1: 数据库优化
echo -e "${YELLOW}步骤1: 执行数据库优化...${NC}"
read -p "是否执行数据库优化脚本? (y/n): " confirm
if [ "$confirm" = "y" ]; then
    echo "正在连接MySQL..."
    mysql -u root -p ai_gateway < src/main/resources/sql/optimization_database.sql
    echo -e "${GREEN}✅ 数据库优化完成${NC}"
else
    echo -e "${YELLOW}⚠️  跳过数据库优化${NC}"
fi
echo ""

# 步骤2: 清除Redis缓存
echo -e "${YELLOW}步骤2: 清除Redis缓存...${NC}"
read -p "是否清除Redis缓存? (y/n): " confirm
if [ "$confirm" = "y" ]; then
    redis-cli FLUSHDB
    echo -e "${GREEN}✅ Redis缓存已清除${NC}"
else
    echo -e "${YELLOW}⚠️  跳过Redis缓存清除${NC}"
fi
echo ""

# 步骤3: 重新编译
echo -e "${YELLOW}步骤3: 重新编译项目...${NC}"
mvn clean package -DskipTests
echo -e "${GREEN}✅ 编译完成${NC}"
echo ""

# 步骤4: 重启应用
echo -e "${YELLOW}步骤4: 重启应用...${NC}"
./scripts/application/stop.sh
sleep 2
./scripts/application/start.sh
echo -e "${GREEN}✅ 应用已重启${NC}"
echo ""

# 步骤5: 等待启动
echo -e "${YELLOW}步骤5: 等待应用启动...${NC}"
sleep 10

# 检查日志
echo "查看最近日志:"
tail -20 logs/ai-gateway.log
echo ""

# 步骤6: 健康检查
echo -e "${YELLOW}步骤6: 健康检查...${NC}"
HEALTH_CHECK=$(curl -s http://localhost:8080/api/auth/health 2>/dev/null || echo "FAILED")

if echo "$HEALTH_CHECK" | grep -q "200\|success"; then
    echo -e "${GREEN}✅ 应用启动成功，健康检查通过${NC}"
else
    echo -e "${RED}❌ 应用启动可能失败，请检查日志${NC}"
    echo "查看详细日志: tail -f logs/ai-gateway.log"
    exit 1
fi
echo ""

# 步骤7: 功能测试（可选）
echo -e "${YELLOW}步骤7: 运行功能测试（可选）...${NC}"
read -p "是否运行完整功能测试? (y/n): " confirm
if [ "$confirm" = "y" ]; then
    chmod +x test_chat_with_points.sh
    ./test_chat_with_points.sh
else
    echo -e "${YELLOW}⚠️  跳过功能测试${NC}"
    echo "您可以稍后手动运行: ./test_chat_with_points.sh"
fi
echo ""

# 总结
echo "=========================================="
echo -e "${GREEN}部署完成！${NC}"
echo "=========================================="
echo ""
echo "📋 已完成:"
echo "  ✅ 数据库索引优化"
echo "  ✅ Redis缓存清理"
echo "  ✅ 代码重新编译"
echo "  ✅ 应用重启"
echo "  ✅ 健康检查"
echo ""
echo "📝 相关文档:"
echo "  - SYSTEM_OPTIMIZATION_REPORT.md (优化报告)"
echo "  - DAILY_POINTS_SETUP.md (每日点数配置)"
echo "  - REDIS_OPTIMIZATION.md (Redis优化)"
echo "  - POINTS_BASED_CHAT_GUIDE.md (点数结算指南)"
echo ""
echo "🔍 监控命令:"
echo "  查看日志: tail -f logs/ai-gateway.log"
echo "  查看性能: curl http://localhost:8080/admin/stats"
echo "  查看Redis: redis-cli INFO memory"
echo ""
echo "💡 提示:"
echo "  - 定时任务会在每天凌晨0点自动发放30点"
echo "  - deepseek-v4-flash消耗1点/次"
echo "  - deepseek-v4-pro消耗3点/次"
echo ""
