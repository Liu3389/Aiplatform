# 架构重构总结报告 - 2026-05-15

## 1. 背景与目标
为了明确区分“平台内 AI 对话服务”与“模型 API Key 中转服务”，本项目进行了核心架构重构，实现了**双业务线并行**的运行模式。

## 2. 数据库结构变更
我们将原有的 `model_config` 表拆分为以下四个核心表：

| 表名 | 用途 | 关键字段 |
| :--- | :--- | :--- |
| `platform_model_config` | **AI 对话平台**使用的模型配置 | `display_name` (展示名), `actual_model` (实际模型), `fixed_points` (固定点数) |
| `marketplace_provider` | **模型商店**的厂商基础配置 | `provider_name`, `platform_api_key` (平台持有的 Key) |
| `user_marketplace_key` | 用户在商店购买的 **API Key 记录** | `api_key`, `total_requests`, `total_cost` (用量统计) |
| `marketplace_usage_log` | 模型商店的详细**调用日志** | `input_tokens`, `output_tokens`, `cost` |

## 3. 业务逻辑调整

### 3.1 AI 对话平台（点数制）
*   **计费方式**：采用**按次固定点数扣除**。无论用户在前端选择什么模型（如 GPT-4o），后端均通过虚拟映射路由至 DeepSeek，并根据配置的 `fixed_points` 扣费。
*   **资金来源**：用户充值后购买套餐/会员获得点数。
*   **接口变更**：原 `/api/model-config/list` 变更为 `/api/platform-model/list`。

### 3.2 模型商店（API Key 中转）
*   **服务模式**：作为模型提供商的中转平台，向用户分发 API Key。
*   **计费方式**：根据用户实际调用的 Token 数量（输入/输出）和厂商定价进行实时扣费。
*   **统计功能**：系统自动记录每个 Key 的请求次数、Token 消耗及金额花费。
*   **新增接口**：
    *   `GET /api/marketplace/keys`: 查询用户已购 Key 列表。
    *   `POST /api/marketplace/buy-key`: 购买新的 API Key。

## 4. 代码层重构清单
1.  **实体类 (Entity)**: 新增 `PlatformModelConfig`, `UserMarketplaceKey`；移除旧的 `UserApiKey`。
2.  **服务层 (Service)**: 
    *   新增 `PlatformModelService` 处理平台模型查询。
    *   重构 `OpenAiChatService` 实现虚拟模型映射逻辑。
3.  **控制层 (Controller)**: 
    *   重命名 `ModelConfigController` 为 `PlatformModelController`。
    *   新增 `MarketplaceController` 处理商店业务。

## 5. 后续计划
*   **完善购买流程**：在 `MarketplaceService` 中集成真实的余额扣减与 Key 生成算法。 (已完成)
*   **数据统计面板**：开发管理员后台，展示模型商店的整体营收与各厂商用量分布。
*   **安全加固**：对分发给用户的 API Key 增加 IP 白名单限制及速率限制。
*   **接口一致性检查**：已完成 Postman 集合与 README 的同步更新。
