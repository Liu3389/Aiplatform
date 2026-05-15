#!/bin/bash

# 项目文件整理脚本
# 将主目录的文件移动到相应的子目录中

set -e  # 遇到错误立即退出

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "开始整理项目文件..."
echo "========================================="

# 创建目录结构
echo ""
echo "1. 创建目录结构..."
mkdir -p scripts/database
mkdir -p scripts/application
mkdir -p scripts/testing
mkdir -p docs/api
mkdir -p docs/guides
mkdir -p docs/database
mkdir -p docs/reports

echo "✅ 目录结构创建完成"

# 移动数据库脚本
echo ""
echo "2. 移动数据库脚本..."
if [ -f "init_db.sh" ]; then
    mv init_db.sh scripts/database/
    echo "   ✅ init_db.sh -> scripts/database/"
fi

if [ -f "init_local_db.sh" ]; then
    mv init_local_db.sh scripts/database/
    echo "   ✅ init_local_db.sh -> scripts/database/"
fi

if [ -f "init_test_data.sh" ]; then
    mv init_test_data.sh scripts/database/
    echo "   ✅ init_test_data.sh -> scripts/database/"
fi

if [ -f "clear_test_data.sh" ]; then
    mv clear_test_data.sh scripts/database/
    echo "   ✅ clear_test_data.sh -> scripts/database/"
fi

# 移动应用管理脚本
echo ""
echo "3. 移动应用管理脚本..."
if [ -f "start.sh" ]; then
    mv start.sh scripts/application/
    echo "   ✅ start.sh -> scripts/application/"
fi

if [ -f "stop.sh" ]; then
    mv stop.sh scripts/application/
    echo "   ✅ stop.sh -> scripts/application/"
fi

# 移动测试脚本
echo ""
echo "4. 移动测试脚本..."
if [ -f "test_api.sh" ]; then
    mv test_api.sh scripts/testing/
    echo "   ✅ test_api.sh -> scripts/testing/"
fi

if [ -f "check_env.sh" ]; then
    mv check_env.sh scripts/testing/
    echo "   ✅ check_env.sh -> scripts/testing/"
fi

# 移动API文档
echo ""
echo "5. 移动API文档..."
if [ -f "ADMIN_API_DOCUMENTATION.md" ]; then
    mv ADMIN_API_DOCUMENTATION.md docs/api/
    echo "   ✅ ADMIN_API_DOCUMENTATION.md -> docs/api/"
fi

# 移动使用指南
echo ""
echo "6. 移动使用指南..."
for file in QUICK_START.md QUICK_REFERENCE.md MAC_QUICK_START.md DEPLOYMENT.md; do
    if [ -f "$file" ]; then
        mv "$file" docs/guides/
        echo "   ✅ $file -> docs/guides/"
    fi
done

# 移动数据库文档
echo ""
echo "7. 移动数据库文档..."
if [ -f "DATABASE_SCRIPTS.md" ]; then
    mv DATABASE_SCRIPTS.md docs/database/
    echo "   ✅ DATABASE_SCRIPTS.md -> docs/database/"
fi

if [ -f "DATABASE_SCRIPTS_SUMMARY.md" ]; then
    mv DATABASE_SCRIPTS_SUMMARY.md docs/database/
    echo "   ✅ DATABASE_SCRIPTS_SUMMARY.md -> docs/database/"
fi

# 移动报告文档
echo ""
echo "8. 移动报告文档..."
for file in PROJECT_SUMMARY.md CHAT_OPTIMIZATION_REPORT.md DOCUMENTATION_CLEANUP_REPORT.md CHECKLIST.md; do
    if [ -f "$file" ]; then
        mv "$file" docs/reports/
        echo "   ✅ $file -> docs/reports/"
    fi
done

# 设置执行权限
echo ""
echo "9. 设置脚本执行权限..."
chmod +x scripts/database/*.sh 2>/dev/null || true
chmod +x scripts/application/*.sh 2>/dev/null || true
chmod +x scripts/testing/*.sh 2>/dev/null || true
echo "   ✅ 执行权限设置完成"

echo ""
echo "========================================="
echo "✅ 文件整理完成！"
echo "========================================="
echo ""
echo "整理后的目录结构："
echo ""
echo "项目根目录:"
echo "├── docs/                      # 文档目录"
echo "│   ├── api/                   # API文档"
echo "│   ├── guides/                # 使用指南"
echo "│   ├── database/              # 数据库文档"
echo "│   └── reports/               # 报告和优化记录"
echo "├── scripts/                   # 脚本目录"
echo "│   ├── database/              # 数据库脚本"
echo "│   ├── application/           # 应用管理脚本"
echo "│   └── testing/               # 测试脚本"
echo "├── src/                       # 源代码"
echo "├── docker/                    # Docker配置"
echo "├── pom.xml                    # Maven配置"
echo "├── README.md                  # 项目主文档"
echo "└── .gitignore                 # Git配置"
echo ""
echo "快速开始:"
echo "  初始化数据库: ./scripts/database/init_local_db.sh"
echo "  插入测试数据: ./scripts/database/init_test_data.sh"
echo "  启动应用:     ./scripts/application/start.sh"
echo ""
echo "详细说明请查看:"
echo "  - scripts/README.md"
echo "  - docs/README.md"
echo ""
