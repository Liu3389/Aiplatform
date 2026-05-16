# AI Group B 修复任务：Service层安全、性能、代码质量

项目路径: $HOME/Documents/Aiplatform/Aiplatform-demo

## 任务 1: SQL注入修复 (P0 安全)

以下文件中使用字符串拼接构造SQL，需改为 MyBatis-Plus 参数化占位符 `{0}`:

1. **UserSubscriptionService.java:106** — `.setSql("balance = balance - " + actualPrice.toPlainString())` 
   改为 `.setSql("balance = balance - {0}", actualPrice.toPlainString())`

2. **UserService.java:561** — `.last("LIMIT " + size + " OFFSET " + offset)` 
   改为 `.last("LIMIT {0} OFFSET {1}", size, offset)`

3. **ChatMessageService.java:147, 178** — `.last("LIMIT " + limit)` 
   改为 `.last("LIMIT {0}", limit)`

4. **NotificationService.java:203** — `.last("LIMIT " + limit)` 
   改为 `.last("LIMIT {0}", limit)`

5. **OpenAiChatService.java:147** — `.last("LIMIT " + limit)` 
   改为 `.last("LIMIT {0}", limit)`

## 任务 2: 修复 PointsService 竞态条件 (P0 安全)

文件: src/main/java/com/ai/gateway/service/PointsService.java 第314-328行

addPoints 方法存在 TOCTOU 竞态：先读 Redis → Java 计算 → 写回，非原子操作。preDeduct 方法使用 Lua 脚本是正确的，addPoints 应同样改为 Lua 脚本或使用 Redis INCRBY 原子操作。

## 任务 3: 非原子批量操作修复 (P0 数据一致性)

文件: src/main/java/com/ai/gateway/service/AdminService.java 第464-474行

batchGrantPoints() 循环调用 pointsService.addPoints()，每个调用有独立的 @Transactional。如果中途失败，前面的操作不会回滚。需要改为：整个方法加 @Transactional，批量操作失败整体回滚。

## 任务 4: ChatMessageHistory 丢失 fileUrls 字段 (P0 数据丢失)

1. **ChatMessageHistory.java** — 添加 `private String fileUrls;` 字段（参考 ChatMessage.java 第57-58行）
2. **DataArchiveService.java 第64-75行** — 归档复制时传递 fileUrls 字段

## 任务 5: 硬编码 AES 密钥 (P1 安全)

文件: src/main/java/com/ai/gateway/service/TokenService.java 第30行

`@Value("${security.aes-key:AiGateway2026Key}")` — 默认密钥写在代码中。改为不提供默认值，启动时如果缺失则抛出明确异常。

## 任务 6: Rate Limit 拦截器用户ID可伪造 (P1 安全)

文件: src/main/java/com/ai/gateway/interceptor/UserRateLimitInterceptor.java 第33行

从 `X-User-Id` Header 读取 userId 没有任何验证。应从 `request.getAttribute("userId")` 获取（由前置的 ApiKeyAuthInterceptor 设置）。

## 任务 7: 全局异常处理器补充 (P1 稳定性)

文件: src/main/java/com/ai/gateway/exception/GlobalExceptionHandler.java

添加以下异常处理器（返回400 + 友好错误信息）：
- HttpMessageNotReadableException (请求体JSON格式错误)
- MissingServletRequestParameterException (缺少必填参数)
- TypeMismatchException (参数类型错误)
- DataIntegrityViolationException (数据库约束冲突)
- HttpRequestMethodNotSupportedException (HTTP方法错误)

## 任务 8: 补全定时任务 (P2 功能缺口)

文件: src/main/java/com/ai/gateway/config/StatisticsSchedulerConfig.java

第42-111行有4个定时任务方法只有 TODO 注释和空的 try-catch。参考已有的 DailyPointsScheduler 实现模式，补全以下定时任务逻辑：
- syncDailyStatistics: 同步每日统计数据到 daily_statistics 表
- syncUserRetention: 计算用户留存率
- cleanExpiredData: 清理过期临时数据
- syncHotQuestions: 统计热门问题

## 任务 9: DataArchiveService N+1 问题 (P2 性能)

文件: src/main/java/com/ai/gateway/service/DataArchiveService.java 第82-84行

改为批量插入：收集所有待归档消息到一个 List，使用 saveBatch 一次性插入。

## 任务 10: 清理死代码

1. **BillingService.java** — 所有方法标记 @Deprecated 且为空实现，确认无调用后删除
2. **CacheService.java** — getModelConfig 返回 null，getAllEnabledModelConfigs 返回空列表，确认无调用后删除
3. **删除重复的 @EnableScheduling** — AsyncConfig.java 和 StatisticsSchedulerConfig.java 各有一个，只保留 StatisticsSchedulerConfig 中的
4. **未使用的 Mapper** — HotQuestionsStatisticsMapper, ContentSafetyStatisticsMapper, UserRetentionStatisticsMapper 如无调用则删除

## 重要
- 不要修改 application.yml 或 pom.xml
- 不要创建新文件，只修改现有文件
- 修复前先仔细阅读相关文件理解上下文
- 每个任务独立验证，修改编译无误后才算完成
