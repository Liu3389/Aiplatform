# 文档目录说明

本目录包含项目的所有文档文件，按类型分类管理。

## 📚 快速导航

### 🔰 新手入门
- [快速开始](guides/QUICK_START.md) - 5分钟上手
- [Mac快速开始](guides/MAC_QUICK_START.md) - Mac用户专用
- [快速参考](guides/QUICK_REFERENCE.md) - 常用命令速查

### 🧪 测试指南
- **[完整测试指南](guides/COMPLETE_TESTING_GUIDE.md)** ⭐NEW - **所有接口测试教程**
- [API全量测试指南](guides/API_TESTING_COMPLETE_GUIDE.md) - 详细测试文档
- [权限测试快速开始](guides/ADMIN_PERMISSION_TEST_QUICKSTART.md) - 5分钟验证权限
- [权限问题诊断](guides/ADMIN_PERMISSION_TROUBLESHOOTING.md) - 问题排查

### 🗄️ 数据库
- [数据库脚本说明](database/DATABASE_SCRIPTS.md) - 初始化和管理
- [统计系统指南](database/STATISTICS_SYSTEM_GUIDE.md) - 管理员后台统计

### 📖 API文档
- [管理员API文档](api/ADMIN_API_DOCUMENTATION.md) - 管理员接口详细说明

### 🚀 部署
- [部署指南](guides/DEPLOYMENT.md) - 生产环境部署

---

## 目录结构

```
docs/
├── README.md                    # 导航中心（本文档）
├── api/                         # API相关文档
│   ├── ADMIN_API_DOCUMENTATION.md    # 管理员后台API文档
│   └── README.md
├── guides/                      # 使用指南
│   ├── QUICK_START.md              # 快速开始
│   ├── MAC_QUICK_START.md          # Mac快速开始
│   ├── QUICK_REFERENCE.md          # 快速参考
│   ├── DEPLOYMENT.md               # 部署指南
│   ├── API_TESTING_COMPLETE_GUIDE.md ⭐NEW # API全量测试指南
│   ├── ADMIN_PERMISSION_TEST_QUICKSTART.md # 权限测试快速开始
│   ├── ADMIN_PERMISSION_TROUBLESHOOTING.md # 权限问题诊断
│   └── PERMISSION_FIX_RECORD.md      # 权限修复记录
├── database/                    # 数据库相关文档
│   ├── README.md
│   ├── DATABASE_SCRIPTS.md         # 数据库脚本说明
│   ├── STATISTICS_SYSTEM_GUIDE.md  # 统计系统指南
│   ├── STATISTICS_IMPLEMENTATION_REPORT.md # 统计系统实施报告
│   └── STATISTICS_API_UPDATE.md    # 统计API更新记录
└── reports/                     # 报告和优化记录
    ├── PROJECT_SUMMARY.md          # 项目总结
    └── CHAT_OPTIMIZATION_REPORT.md # 聊天优化报告
```

## 文档分类说明

### 🔰 新手入门 (guides/)
- **快速开始**: 5分钟上手项目
- **Mac快速开始**: Mac用户专用指南
- **快速参考**: 常用命令和配置速查

### 🧪 测试指南 (guides/)
- **API全量测试**: 80个接口的完整测试教程
- **权限测试**: 验证@RequireAdmin权限控制
- **问题诊断**: 常见问题排查指南

### 🗄️ 数据库 (database/)
- **数据库脚本**: 初始化、测试数据管理
- **统计系统**: 管理员后台统计功能说明
- **迁移记录**: 数据库变更历史

### 📖 API文档 (api/)
- **管理员API**: 38个管理员接口详细说明
- **接口规范**: 请求/响应格式说明

### 🚀 部署 (guides/)
- **部署指南**: 生产环境部署步骤
- **环境配置**: MySQL、Redis配置说明

### 📊 报告 (reports/)
- **项目总结**: 项目整体概况
- **优化报告**: 性能优化记录

---

## 主目录保留的文件

以下文件保留在项目根目录，因为它们是常用的入口文件：
- `README.md` - 项目主 readme
- `QUICK_START.md` - 快速开始（也复制到 docs/guides/）
- 其他 .sh 脚本文件
- 配置文件（pom.xml, application.yml 等）
