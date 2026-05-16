# AI Group A 修复任务：API层、文档、Postman集合

项目路径: $HOME/Documents/Aiplatform/Aiplatform-demo

## 任务 1: MarketplaceController 假接口改成真实实现

文件: src/main/java/com/ai/gateway/controller/MarketplaceController.java

两个接口目前直接 return Result.error("模型商店功能正在开发中，敬请期待！")，需改为真实实现：

- listKeys (GET /marketplace/keys): 通过 token 获取 userId，查询 userMarketplaceKeyMapper.selectList 返回用户的 API Key 列表
- buyKey (POST /marketplace/buy-key): 
  1. 从 token 获取 userId
  2. 从 body 获取 providerId
  3. 查询 MarketplaceProviderMapper 获取 provider 信息
  4. 从 UserService 扣减用户余额
  5. 生成 UUID 作为 apiKey
  6. 插入 user_marketplace_key 记录
  7. 返回创建的 key 信息

## 任务 2: API_TEST Postman 补齐缺失端点

文件: API_TEST.postman_collection.json

在"聊天对话"分组中添加以下端点：
- POST /chat/with-file (上传文件对话)
- POST /chat/generate-file (AI生成文件)  
- POST /chat/sync (同步对话到OSS)
- POST /chat/export-content (导出对话内容)
- GET /chat/load?conversationId=1 (加载对话文件)

添加新分组"AI文档助手":
- POST /v1/ai-doc/chat (上传文件与AI交互)
- POST /v1/ai-doc/export (导出AI结果)

添加新分组"用户订阅与点数":
- GET /user/subscriptions/packages (可用套餐列表)
- GET /user/subscriptions/current (当前订阅状态)
- GET /user/subscriptions/history (订阅历史)
- POST /user/subscriptions/purchase (购买套餐, body: {packageCode, couponId?})
- POST /user/subscriptions/cancel/{subscriptionId} (取消订阅)
- GET /user/points-bills?page=1&size=20&changeType= (点数账单)

## 任务 3: 清理 API_TEST Postman 中的 X-API-Key Header

聊天接口的"免费模式-非流式对话"和"免费模式-流式对话"中移除了 X-API-Key header，平台模式已不需要。但保留"自定义模型"的接口不变。

## 任务 4: 统一通知未读数 key 名

文件: src/main/java/com/ai/gateway/controller/NotificationController.java 第34行

将 unreadCount() 方法返回的 Map key 从 "count" 改为 "unreadCount"，与 AdminController 保持一致。

## 任务 5: 清理两套订阅系统

UserController 中有旧版订阅接口 (/user/subscription, /user/subscription/subscribe, /user/subscription/cancel)，UserSubscriptionController 有新版 (/user/subscriptions/*)。旧版 UserController 中的订阅方法对应的 UserService 方法如果只是简单包装，将其标记为 @Deprecated 并在返回值中提示前端迁移到新接口。

注意：不要删除旧接口，只标记废弃 + 提示迁移。

## 重要
- 所有 URL 前缀包含 /api（因为 server.servlet.context-path=/api）
- Header 认证统一使用 X-User-Token
- 不要创建新文件，只修改现有文件
- 不要修改 application.yml 配置
