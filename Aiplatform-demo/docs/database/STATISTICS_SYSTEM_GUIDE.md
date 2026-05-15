# 管理员后台统计系统 - 使用说明

## 📊 系统概述

本统计系统采用**定时统计 + Redis缓存**的架构，避免实时全表扫描导致数据库CPU过载。

### 核心设计理念

1. **计数器表**: 新增用户、对话时自动+1，统计时直接查计数器
2. **每日统计表**: 每天凌晨2点统计前一天数据，存入daily_statistics表
3. **Redis缓存**: 统计结果缓存1-24小时，管理员直接从Redis获取
4. **冷热分离**: 优先查询近30天数据，历史数据单独统计

## 🗄️ 数据库表结构

### 1. daily_statistics（每日统计表）
存储每天的完整统计数据，包括：
- 用户统计：新增用户、总用户、活跃用户
- 对话统计：新增会话、总会话、消息数
- Token统计：输入/输出Token总数
- API调用统计：成功/失败次数
- 模型使用分布（JSON）
- 时段分布（JSON）

### 2. statistics_counter（实时计数器表）
用于快速累加计数：
- USER_TOTAL: 用户总数
- CONVERSATION_TOTAL: 会话总数
- MESSAGE_TOTAL: 消息总数
- API_CALL_TOTAL: API调用总数
- API_CALL_SUCCESS: API成功调用数
- API_CALL_FAILED: API失败调用数

### 3. user_retention_statistics（用户留存统计表）
存储用户留存数据：
- 次日留存率
- 7日留存率
- 30日留存率

### 4. hot_questions_statistics（热门问题统计表）
存储用户提问TOP10

### 5. content_safety_statistics（内容安全统计表）
存储敏感词触发、违规举报等数据

## 🔧 核心组件

### 1. StatisticsCounterService（计数器服务）
```java
// 用户注册时调用
statisticsCounterService.incrementUserTotal();

// 创建会话时调用
statisticsCounterService.incrementConversationTotal();

// 发送消息时调用
statisticsCounterService.incrementMessageTotal(1L);

// API调用时调用
statisticsCounterService.incrementApiCallTotal(success);
```

### 2. StatisticsCacheService（缓存服务）
```java
// 缓存仪表盘数据（24小时TTL）
cacheService.cacheDashboardStats("7d", stats);

// 获取缓存数据
DashboardStatsVO cached = cacheService.getDashboardStats("7d", DashboardStatsVO.class);

// 清除缓存
cacheService.clearAllCache();
```

### 3. StatisticsService（统计服务）
提供核心统计功能：
- `getDashboardStats(period)`: 获取仪表盘数据
- `getUserStats()`: 获取用户统计
- `getConversationStats()`: 获取对话统计

### 4. StatisticsSchedulerConfig（定时任务）
```
每小时执行（0分钟）: 更新今日统计数据
每天凌晨2点: 统计前一天完整数据
每天凌晨3点: 清理90天前的旧数据
```

## 📡 API接口

### 1. 获取仪表盘统计数据
```
GET /admin/statistics/dashboard?period=7d
```

**参数**:
- `period`: 时间范围，可选值：`7d`（近7天）、`30d`（近30天）

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "totalUsers": 1000,
    "todayNewUsers": 50,
    "totalConversations": 5000,
    "todayConversations": 200,
    "todayActiveUsers": 150,
    "systemStatus": "normal",
    "userTrend": [
      {"date": "2026-05-08", "value": 45},
      {"date": "2026-05-09", "value": 50}
    ],
    "conversationTrend": [...],
    "activeUserTrend": [...],
    "quickStats": {
      "totalMessages": 50000,
      "totalPointsConsumed": 1000.000,
      "apiSuccessRate": 98.5,
      "avgConversationLength": 10
    }
  }
}
```

### 2. 获取用户统计数据
```
GET /admin/statistics/users
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "totalUsers": 1000,
    "todayNewUsers": 50,
    "activeUsers7d": 300,
    "inactiveUsers30d": 100,
    "statusDistribution": {
      "ACTIVE": 950,
      "DISABLED": 50
    },
    "userSegments": {
      "highActive": 50,
      "mediumActive": 150,
      "lowActive": 100,
      "inactive": 700
    }
  }
}
```

### 3. 获取对话统计数据
```
GET /admin/statistics/conversations
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "totalConversations": 5000,
    "totalMessages": 50000,
    "avgMessagesPerConv": 10.0,
    "avgDuration": 120,
    "todayConversations": 200,
    "todayMessages": 2000,
    "modelUsage": {
      "totalCalls": 5000,
      "todayCalls": 200,
      "successRate": 98.5,
      "modelDistribution": {
        "gpt-4o": 2000,
        "deepseek-v4": 3000
      },
      "topModels": [
        {"modelName": "gpt-4o", "callCount": 2000, "percentage": 40.0}
      ]
    }
  }
}
```

## 🚀 部署步骤

### 1. 执行数据库迁移脚本
```bash
mysql -h localhost -P 3306 -u root -p ai_gateway < src/main/resources/sql/migration_statistics.sql
```

### 2. 重启应用
```bash
./scripts/application/stop.sh
./scripts/application/start.sh
```

### 3. 验证定时任务
查看日志确认定时任务是否正常执行：
```bash
tail -f logs/ai-gateway.log | grep "统计任务"
```

### 4. 测试API接口
```bash
# 获取管理员token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin01","password":"Test@123456"}' | jq -r '.data.token')

# 测试仪表盘接口
curl -s http://localhost:8080/admin/statistics/dashboard?period=7d \
  -H "X-User-Token: $TOKEN" | jq .
```

## 📈 性能优化建议

### 1. Redis配置
确保Redis配置合理：
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
```

### 2. 数据库索引
已为以下字段添加索引：
- `user.create_time`
- `user.last_login_time`
- `conversation.create_time`
- `call_log.model`
- `call_log.status`

### 3. 缓存策略
- 仪表盘数据：缓存24小时
- 用户/对话统计：缓存1小时
- 留存统计：缓存24小时

### 4. 定时任务调优
如需调整统计频率，修改`StatisticsSchedulerConfig`中的cron表达式：
```java
// 每30分钟统计一次
@Scheduled(cron = "0 */30 * * * ?")

// 每天凌晨1点统计
@Scheduled(cron = "0 0 1 * * ?")
```

## ⚠️ 注意事项

### 1. 计数器一致性
计数器通过数据库事务保证一致性，但如果应用异常退出可能导致计数器与实际数据不一致。建议定期校验：
```sql
-- 校验用户总数
SELECT COUNT(*) FROM user;
SELECT counter_value FROM statistics_counter WHERE counter_type = 'USER_TOTAL';
```

### 2. 缓存失效
当手动修改数据后，需要清除相关缓存：
```java
cacheService.clearCache("dashboard");
```

### 3. 历史数据统计
默认只统计近30天数据，如需统计更长时间，需要：
1. 调用专门的统计接口
2. 或手动执行SQL聚合查询

### 4. 内存占用
Redis缓存会占用一定内存，监控Redis内存使用情况：
```bash
redis-cli info memory
```

## 🔍 故障排查

### 问题1: 统计数据不准确
**原因**: 计数器未正确更新  
**解决**: 
1. 检查代码中是否正确调用了`incrementXXX()`方法
2. 手动校正计数器值：
```sql
UPDATE statistics_counter SET counter_value = (SELECT COUNT(*) FROM user) 
WHERE counter_type = 'USER_TOTAL';
```

### 问题2: 缓存未生效
**原因**: Redis连接失败或序列化错误  
**解决**:
1. 检查Redis是否正常运行
2. 查看日志中的缓存错误信息
3. 测试Redis连接：`redis-cli ping`

### 问题3: 定时任务未执行
**原因**: @EnableScheduling未生效  
**解决**:
1. 确认`StatisticsSchedulerConfig`类上有`@EnableScheduling`注解
2. 检查Spring Boot启动日志是否有调度器相关错误

## 📝 扩展开发

### 添加新的统计指标

1. **在daily_statistics表中添加字段**
```sql
ALTER TABLE daily_statistics ADD COLUMN new_metric INT DEFAULT 0;
```

2. **创建对应的Entity和Mapper**

3. **在定时任务中计算并保存**
```java
@Scheduled(cron = "0 0 2 * * ?")
public void dailyStatistics() {
    // 计算新指标
    int newValue = calculateNewMetric();
    
    // 保存到daily_statistics表
    dailyStats.setNewMetric(newValue);
    dailyStatsMapper.updateById(dailyStats);
}
```

4. **在VO中添加字段并返回**

## 🎯 总结

本统计系统通过以下机制实现高性能：
- ✅ 计数器表避免COUNT(*)全表扫描
- ✅ 每日统计表汇总历史数据
- ✅ Redis缓存减少数据库查询
- ✅ 定时任务异步计算，不阻塞请求
- ✅ 冷热分离，优先查询近期数据

管理员打开后台时，所有数据都从Redis缓存获取，响应时间在毫秒级别，不会对数据库造成压力。
