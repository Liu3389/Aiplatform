# 脚本目录说明

本目录包含项目的所有脚本文件，按功能分类管理。

## 目录结构

```
scripts/
├── database/              # 数据库相关脚本
│   ├── init_db.sh                    # 远程数据库初始化
│   ├── init_local_db.sh              # 本地数据库初始化
│   ├── init_test_data.sh             # 测试数据初始化
│   └── clear_test_data.sh            # 清除测试数据
├── application/           # 应用管理脚本
│   ├── start.sh                      # 启动应用
│   └── stop.sh                       # 停止应用
├── testing/               # 测试相关脚本
│   ├── test_api.sh                   # API接口测试
│   └── check_env.sh                  # 环境检查
└── README.md              # 本文档
```

## 脚本使用说明

### 数据库脚本 (database/)

#### init_db.sh
- **用途**: 初始化远程VM数据库环境
- **目标**: VM IP 10.211.55.10
- **功能**: 
  - 创建数据库
  - 导入表结构
  - 执行所有迁移脚本
  - 验证初始化结果

#### init_local_db.sh
- **用途**: 初始化本地开发环境数据库
- **目标**: localhost:3307
- **功能**: 同init_db.sh，但针对本地环境

#### init_test_data.sh
- **用途**: 重建数据库并插入完整测试数据
- **警告**: ⚠️ 会删除现有数据库并重新创建
- **包含数据**:
  - 9个模型配置
  - 35个用户账户（5个超级管理员 + 10个管理员 + 20个普通用户）
  - 70个API Keys
  - 15条调用日志
  - 10条计费记录
  - 10条点数账单

#### clear_test_data.sh
- **用途**: 一键清除所有测试数据
- **安全特性**: 需要用户确认才能执行
- **清除内容**: 用户数据、API Keys、日志等
- **保留内容**: 表结构和模型配置

### 应用管理脚本 (application/)

#### start.sh
- **用途**: 启动Spring Boot应用
- **功能**: 
  - 检查环境
  - 编译项目
  - 启动应用

#### stop.sh
- **用途**: 停止正在运行的应用
- **功能**: 优雅关闭应用进程

### 测试脚本 (testing/)

#### test_api.sh
- **用途**: 测试API接口
- **功能**: 自动化API测试用例

#### check_env.sh
- **用途**: 检查开发环境配置
- **功能**: 
  - 检查Java版本
  - 检查Maven配置
  - 检查MySQL连接
  - 检查Redis连接

## 使用建议

### 首次设置开发环境
```bash
# 1. 检查环境
./scripts/testing/check_env.sh

# 2. 初始化本地数据库
./scripts/database/init_local_db.sh

# 3. 插入测试数据
./scripts/database/init_test_data.sh

# 4. 启动应用
./scripts/application/start.sh

# 5. 测试API
./scripts/testing/test_api.sh
```

### 重置测试环境
```bash
# 清除测试数据
./scripts/database/clear_test_data.sh

# 重新生成测试数据
./scripts/database/init_test_data.sh
```

### 生产环境部署
```bash
# 仅初始化数据库结构（不插入测试数据）
./scripts/database/init_db.sh

# 启动应用
./scripts/application/start.sh
```

## 注意事项

1. **执行权限**: 确保脚本有执行权限 `chmod +x scripts/**/*.sh`
2. **数据库密码**: 生产环境请修改脚本中的默认密码
3. **备份**: 执行destructive操作前先备份数据
4. **环境变量**: 可以根据需要配置环境变量覆盖默认值

## 维护指南

- 新增脚本时，将其放入对应的子目录
- 保持脚本的可执行权限
- 更新本文档以反映脚本变更
- 为复杂脚本添加详细的注释
