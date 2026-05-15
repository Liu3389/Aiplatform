# API 接口与文档一致性检查报告 (2026-05-15)

## 1. 检查背景
为了确保项目重构后的“双业务线”架构（AI 对话平台 + 模型商店）在代码、接口文档和说明文档之间保持高度一致，进行了全面的审查与更新。

## 2. 主要变更清单

### 2.1 移除的旧功能接口
*   **用户自定义 API Key**: 删除了 `/api/api-key/*` 相关的所有 Postman 请求项及文档描述。
*   **旧版模型配置**: 删除了 `/api/model-config/list` 接口，替换为新的平台模型接口。

### 2.2 新增/更新的接口
| 模块 | 接口路径 | 说明 | 状态 |
| :--- | :--- | :--- | :--- |
| **平台对话** | `GET /api/platform-model/list` | 获取支持虚拟映射的对话模型列表 | ✅ 已同步 |
| **模型商店** | `GET /api/marketplace/keys` | 查询用户已购买的 API Key | ✅ 已同步 |
| **模型商店** | `POST /api/marketplace/buy-key` | 购买厂商 API Key | ✅ 已同步 |
| **数据管理** | `POST /api/admin/test-data/reset` | 一键重置平台基础测试数据 | ✅ 已同步 |

### 2.3 数据库脚本同步
*   **schema.sql**: 增加了 `platform_model_config` 的 `provider`, `base_url`, `api_key` 字段定义。
*   **init_test_data.sql**: 更新了插入语句以适配新字段，并移除了对已废弃表的引用。

## 3. 文档修复记录
*   **[API_TEST.postman_collection.json](../API_TEST.postman_collection.json)**: 清理了失效的 API Key 管理分组，新增了测试数据管理分组。
*   **[README.md](../README.md)**: 更新了核心特性描述，增加了模型商店和数据管理的调用示例。
*   **[REFACTORING_SUMMARY.md](./REFACTORING_SUMMARY.md)**: 标记了 P0/P1 级问题的修复进度。
*   **[DATABASE_SCRIPTS.md](./DATABASE_SCRIPTS.md)**: 增加了对旧版 `testdata_large.sql` 的弃用警告。

## 4. 结论
目前所有对外提供的 `.json` 接口集合与 `.md` 说明文档均与后端 Controller 实现保持严格一致。开发者可以直接使用 Postman 集合进行全量功能测试。
