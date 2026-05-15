# 数据库管理脚本说明

## 概述

本项目包含以下数据库管理脚本，用于简化数据库初始化、测试数据管理和维护工作：

1. `init_db.sh` - 远程数据库初始化脚本
2. `init_local_db.sh` - 本地数据库初始化脚本  
3. `init_test_data.sh` - 测试数据初始化脚本
4. `clear_test_data.sh` - 清除测试数据脚本

## 脚本功能详解

### 1. init_db.sh (远程数据库初始化)

**用途**: 初始化远程VM上的数据库环境

**配置**:
- VM IP: 10.211.55.10
- MySQL用户: root
- MySQL密码: 123456
- 数据库名: ai_gateway

**执行步骤**:
1. 创建数据库
2. 导入基础表结构 (schema.sql)
3. 执行所有迁移脚本:
   - migration_admin_enhanced.sql (管理员增强功能)
   - migration_chat_optimization.sql (聊天优化)
   - migration_notification.sql (通知系统)
   - migration_p1_optimization.sql (P1优化)
4. 验证表结构和初始数据

**使用方法**:
```bash
./init_db.sh
```

### 2. init_local_db.sh (本地数据库初始化)

**用途**: 初始化本地开发环境的数据库

**配置**:
- 主机: localhost
- 端口: 3307
- MySQL用户: root
- MySQL密码: 123456
- 数据库名: ai_gateway

**执行步骤**:
1. 检查MySQL连接
2. 创建数据库
3. 导入基础表结构
4. 执行所有迁移脚本
5. 验证表结构和初始数据

**使用方法**:
```bash
./init_local_db.sh
```

### 3. init_test_data.sh (测试数据初始化)

**用途**: 重建数据库并插入完整的测试数据

**特点**:
- 完全重置数据库（删除后重建）
- 插入9个模型配置
- 创建35个测试账户:
  - 5个超级管理员 (superadmin01-05)
  - 10个管理员 (admin01-10) 
  - 20个普通用户 (testuser01-20)
- 为每个账户生成2个API Key
- 生成调用日志、计费记录、点数账单等测试数据

**测试账户信息**:
- 超级管理员: superadmin01~05 / Test@123456 (点数: 10000, 余额: $10000)
- 管理员: admin01~10 / Test@123456 (点数: 5000, 余额: $5000)
- 普通用户: testuser01~20 / Test@123456 (不同余额等级)

**使用方法**:
```bash
./init_test_data.sh
```

**注意**: 此脚本会删除现有数据库并重新创建，请谨慎使用！

### 4. clear_test_data.sh (清除测试数据)

**用途**: 一键清除所有测试数据，保留表结构和基础配置

**清除内容**:
- 用户数据
- API Keys
- 调用日志
- 计费记录
- 点数账单
- 会话和消息数据
- 通知数据
- 操作日志
- 统计数据
- 代金券

**保留内容**:
- 表结构
- 模型配置 (model_config)

**安全特性**:
- 需要用户确认才能执行
- 按依赖关系逆序清除数据
- 显示清除后的数据统计

**使用方法**:
```bash
./clear_test_data.sh
```

## 使用场景建议

### 场景1: 全新部署
```bash
# 1. 初始化数据库结构
./init_local_db.sh

# 2. 插入测试数据（可选）
./init_test_data.sh
```

### 场景2: 开发测试
```bash
# 快速重置测试环境
./init_test_data.sh
```

### 场景3: 清理测试数据
```bash
# 清除测试数据，保留表结构
./clear_test_data.sh

# 如需重新生成测试数据
./init_test_data.sh
```

### 场景4: 生产环境初始化
```bash
# 仅初始化表结构，不插入测试数据
./init_db.sh  # 或 ./init_local_db.sh
```

## SQL文件说明

项目中的SQL文件位于 `src/main/resources/sql/` 目录：

- `schema.sql` - 基础表结构定义
- `init_test_data.sql` - 测试数据插入脚本
- `migration_*.sql` - 各种功能迁移脚本
- `testdata.sql` - 简化的测试数据（已废弃，使用init_test_data.sql替代）

## 注意事项

1. **备份重要数据**: 在执行任何数据库操作前，请确保已备份重要数据
2. **权限要求**: 确保MySQL用户具有足够的权限执行DDL和DML操作
3. **网络连接**: 远程数据库脚本需要能够访问VM网络
4. **端口配置**: 本地数据库默认使用3307端口，根据实际情况调整
5. **密码安全**: 生产环境请修改默认密码

## 故障排查

### 问题1: 无法连接MySQL
- 检查MySQL服务是否运行
- 验证端口配置是否正确
- 确认用户名和密码

### 问题2: 脚本执行失败
- 检查脚本是否有执行权限: `chmod +x *.sh`
- 查看错误输出信息
- 确认SQL文件路径正确

### 问题3: 表数量不符
- 检查所有迁移脚本是否成功执行
- 查看MySQL错误日志
- 手动验证表是否存在

## 最佳实践

1. **开发环境**: 使用 `init_test_data.sh` 快速搭建测试环境
2. **测试环境**: 定期使用 `clear_test_data.sh` + `init_test_data.sh` 重置数据
3. **生产环境**: 仅使用 `init_db.sh` 或 `init_local_db.sh` 初始化表结构
4. **版本升级**: 按顺序执行新的migration脚本
5. **数据备份**: 定期备份数据库，特别是在执行 destructive 操作前