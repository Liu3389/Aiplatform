# 数据库管理脚本说明

## 概述

本项目采用标准化的数据库管理流程，包含以下核心脚本：

1. `init_db.sh` - 远程数据库初始化脚本（创建表结构）
2. `init_local_db.sh` - 本地数据库初始化脚本（创建表结构）  
3. `init_data.sql` - 统一数据初始化脚本（配置与测试数据）

### 📂 SQL 文件说明

所有 SQL 脚本已整合并归档在 `src/main/resources/sql/` 目录：

| 文件 | 用途 | 适用场景 |
| :--- | :--- | :--- |
| **[schema.sql](../../src/main/resources/sql/schema.sql)** | 核心建表脚本（22 张表） | 全新部署 / 生产环境初始化 |
| **[rebuild.sql](../../src/main/resources/sql/rebuild.sql)** ⭐ | **一键重建脚本**（建表 + 测试数据） | 开发环境快速搭建 |
| **[init_data.sql](../../src/main/resources/sql/init_data.sql)** | 数据初始化脚本（含测试用户） | 注入完整测试数据 |
| **[init_test_data.sql](../../src/main/resources/sql/init_test_data.sql)** | 轻量测试数据脚本 | 最小化快速重置 |
| **[migration_v3.1.sql](../../src/main/resources/sql/migration_v3.1.sql)** | v3.0 → v3.1 增量迁移 | 版本升级 |
| **[testdata_large.sql](../../src/main/resources/sql/testdata_large.sql)** | 海量测试数据（50 用户） | 性能测试 / 压测 |

> **注意**: 所有脚本均使用 `SET FOREIGN_KEY_CHECKS = 0` 确保幂等执行，可随时安全重置。API Key 使用 `${DEEPSEEK_API_KEY}` 占位符，部署时替换为实际值。

## 🛠️ 动态测试数据管理

为了方便开发调试，我们提供了一个专用的管理接口来动态重置测试数据：

*   **重置基础数据**: `POST /api/admin/test-data/reset`
    *   该接口会读取 `init_test_data.sql` 并重新执行，清空旧数据并插入最新配置。
*   **修复点数配置**: `POST /api/admin/test-data/fix-points`
    *   用于批量修复数据库中缺失或错误的模型固定点数消耗值。

**注意**: 请勿直接运行旧的 `testdata_large.sql`，它包含大量已过时的表结构引用。

## 核心表结构说明

### 1. platform_model_config (平台对话模型配置)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | bigint | 主键 ID |
| `display_name` | varchar(64) | 前端展示的模型名称（如 GPT-4o） |
| `actual_model` | varchar(64) | 实际调用的底层模型名（如 deepseek-v4-pro） |
| `fixed_points` | decimal(10,3) | 每次对话消耗的固定点数 |
| `provider` | varchar(32) | 关联的 AI 厂商标识 |
| `base_url` | varchar(256) | 厂商 API 的基础地址 |
| `api_key` | varchar(128) | 平台持有的该厂商 API Key |
| `sort_order` | int | 前端展示排序权重 |
| `status` | tinyint | 状态：1-启用，0-禁用 |

### 2. marketplace_provider (模型商店厂商)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | bigint | 主键 ID |
| `provider_name` | varchar(32) | 厂商名称（如 OpenAI, Anthropic） |
| `base_url` | varchar(256) | API基础地址 |
| `platform_api_key` | varchar(128) | 平台持有的厂商 Key |
| `input_price` | decimal(10,6) | 每千输入Token价格（元） |
| `output_price` | decimal(10,6) | 每千输出Token价格（元） |
| `status` | tinyint | 状态：1-上架，0-下架 |

### 3. package_template (套餐模板)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | bigint | 主键 ID |
| `package_code` | varchar(32) | 套餐代码（唯一标识） |
| `package_name` | varchar(64) | 套餐名称 |
| `description` | varchar(500) | 套餐描述 |
| `identity_label` | varchar(32) | 身份标识（如：至尊、尊享） |
| `points` | decimal(10,3) | 包含点数 |
| `price` | decimal(10,2) | 价格（元） |
| `duration_days` | int | 有效期天数（NULL表示永久） |
| `daily_call_limit` | int | 每日调用限制 |
| `monthly_call_limit` | int | 每月调用限制 |
| `max_tokens_per_call` | int | 单次最大Token数 |
| `priority_level` | int | 优先级等级 |
| `features` | text | 特性说明（JSON格式） |
| `status` | tinyint | 状态：1-上架，0-下架 |
| `sort_order` | int | 排序顺序 |

### 4. admin_operation_log (管理员操作日志)
| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | bigint | 主键 ID |
| `admin_id` | bigint | 管理员ID |
| `admin_username` | varchar(64) | 管理员用户名 |
| `operation_type` | varchar(32) | 操作类型（USER_MANAGE/POINTS_MANAGE等） |
| `operation_action` | varchar(64) | 操作动作（RECHARGE/BAN等） |
| `target_user_ids` | text | 目标用户ID列表（JSON数组） |
| `operation_detail` | text | 操作详情（JSON格式） |
| `ip_address` | varchar(64) | 操作IP地址 |
| `user_agent` | varchar(512) | 浏览器标识 |
| `result` | varchar(16) | 操作结果（SUCCESS/FAILED） |
| `error_message` | text | 错误信息 |
| `create_time` | datetime | 操作时间 |

## 脚本功能详解

### 1. init_db.sh / init_local_db.sh (环境初始化)

**用途**: 根据当前环境（远程 VM 或本地 Mac）执行 `schema.sql` 以创建数据库和表结构。

**执行步骤**:
1. 检查 MySQL 连接状态
2. 创建 `ai_gateway` 数据库
3. 导入 `schema.sql` 定义的所有表结构和索引
4. 验证表数量是否正确（预期 22 张表）

### 2. init_data.sql (数据初始化)

**用途**: 为系统注入基础运行数据和测试账户。

**包含内容**:
- **模型配置**: 预置 DeepSeek v4-flash (FREE) 和 v4-pro (PREMIUM) 模型。
- **套餐模板**: 免费版、基础版、专业版、旗舰版四种默认套餐。
- **管理员账户**: 
  - `superadmin` / `Test@123456`
  - `admin` / `Test@123456`
- **测试用户**: `testuser01`, `testuser02` 等不同点数额度的账户。
- **计数器初始化**: 设置平台统计数据初始值。

**使用方法**:
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/init_data.sql
```


## 使用场景建议

### 场景1: 一键重建（推荐开发环境）⭐
```bash
# 一键完成建表 + 测试数据注入
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/rebuild.sql
```

### 场景2: 全新部署
```bash
# 1. 初始化数据库结构
mysql -h localhost -P 3307 -u root -p123456 < src/main/resources/sql/schema.sql

# 2. 注入基础配置和测试数据
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/init_data.sql
```

### 场景3: 生产环境初始化
```bash
# 仅执行 schema.sql 创建表结构，不注入测试数据
mysql -h YOUR_HOST -u YOUR_USER -p ai_gateway < src/main/resources/sql/schema.sql
```




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

1. **开发环境**: 使用 `rebuild.sql` 一键重建数据库环境
2. **测试环境**: 使用 `rebuild.sql` 或 `init_test_data.sql` 定期重置数据
3. **生产环境**: 仅使用 `init_db.sh` 或 `init_local_db.sh` 初始化表结构
4. **版本升级**: 按顺序执行新的migration脚本
5. **数据备份**: 定期备份数据库，特别是在执行 destructive 操作前