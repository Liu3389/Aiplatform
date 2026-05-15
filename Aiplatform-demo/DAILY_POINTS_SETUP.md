# 点数结算系统配置指南

**更新日期**: 2026-05-15  
**核心机制**: deepseek-v4-flash = 1点/次，每日自动发放30点（当日有效）

---

## 🎯 系统设计

### 点数消耗规则

| 模型 | 每次对话消耗 | 说明 |
|------|------------|------|
| deepseek-v4-flash | **1点** | 标准模型，日常使用 |
| deepseek-v4-pro | 3点 | 高级模型，复杂任务 |

### 每日配额机制

- **普通用户**: 每天凌晨0点自动发放**30点**
- **点数有效期**: 当日23:59:59过期（不累积）
- **理论对话次数**: 30点 ÷ 1点/次 = **30次对话/天**

### 优势

✅ **灵活控制**: 通过调整每日点数即可控制使用量  
✅ **统一管理**: 所有模型都走点数系统，便于统计  
✅ **用户友好**: 无需选择模式，点击即用  
✅ **商业扩展**: 支持充值、套餐、促销等  

---

## 🚀 快速部署（4步完成）

### 步骤1: 更新模型点数配置

```bash
# 连接MySQL
mysql -u root -p ai_gateway

# 执行更新脚本
source src/main/resources/sql/update_model_points.sql;

# 验证结果
SELECT model_name, points_per_request, points_mode 
FROM model_config 
WHERE provider = 'deepseek';
```

**预期输出**:
```
+---------------------+--------------------+------------+
| model_name          | points_per_request | points_mode|
+---------------------+--------------------+------------+
| deepseek-v4-flash   | 1.000              | STANDARD   |
| deepseek-v4-pro     | 3.000              | PREMIUM    |
+---------------------+--------------------+------------+
```

### 步骤2: 重新编译并重启

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo

# 清理编译
mvn clean package -DskipTests

# 重启应用
./scripts/application/stop.sh
./scripts/application/start.sh

# 查看日志确认启动成功
tail -f logs/ai-gateway.log
```

### 步骤3: 测试每日点数发放

#### 方法1: 手动触发（推荐用于测试）

```bash
# 管理员登录获取Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: admin-device" \
  -d '{"username":"admin","password":"Admin@123456"}'

# 保存Token
export ADMIN_TOKEN="your_admin_token"

# 手动触发每日点数发放（给所有用户）
curl -X POST http://localhost:8080/admin/points/distribute-daily \
  -H "X-User-Token: $ADMIN_TOKEN"

# 或指定用户发放
curl -X POST http://localhost:8080/admin/points/distribute-daily \
  -H "X-User-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId": 52}'
```

#### 方法2: 等待自动执行

定时任务会在**每天凌晨0点**自动执行，无需手动干预。

### 步骤4: 运行完整测试

```bash
# 添加执行权限
chmod +x test_chat_with_points.sh

# 运行测试
./test_chat_with_points.sh
```

**测试流程**:
1. ✅ 注册用户
2. ✅ 登录获取Token
3. ✅ 查看初始点数（应为0）
4. ✅ 手动充值100点（用于测试）
5. ✅ 查看模型列表（显示点数消耗）
6. ✅ 测试非流式对话（消耗1点）
7. ✅ 测试流式对话（消耗1点）
8. ✅ 测试高级模型（消耗3点）
9. ✅ 查看剩余点数
10. ✅ 查看调用日志

---

## 📊 使用示例

### 用户视角

#### 1. 查看点数余额

```bash
curl -X GET http://localhost:8080/api/auth/balance \
  -H "X-User-Token: $USER_TOKEN"
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "userPoints": 30.000,
    "balance": 0
  }
}
```

#### 2. 查看模型列表

```bash
curl -X GET http://localhost:8080/api/models \
  -H "X-User-Token: $USER_TOKEN"
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "models": [
      {
        "modelName": "deepseek-v4-flash",
        "pointsPerRequest": 1.000,
        "description": "标准模型，每次对话消耗1点"
      },
      {
        "modelName": "deepseek-v4-pro",
        "pointsPerRequest": 3.000,
        "description": "高级模型，每次对话消耗3点"
      }
    ]
  }
}
```

#### 3. 发起对话（自动扣点）

```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $USER_TOKEN" \
  -d '{
    "model": "deepseek-v4-flash",
    "messages": [{"role": "user", "content": "你好"}],
    "maxTokens": 100
  }'
```

**系统自动**:
1. 检查点数是否充足（需要1点）
2. 预扣1点
3. 调用DeepSeek API
4. 返回对话结果
5. 结算实际消耗

**响应**:
```json
{
  "code": 200,
  "data": {
    "content": "你好！我是AI助手...",
    "model": "deepseek-v4-flash",
    "usage": {
      "prompt_tokens": 10,
      "completion_tokens": 20,
      "total_tokens": 30
    }
  }
}
```

#### 4. 查看剩余点数

```bash
curl -X GET http://localhost:8080/api/auth/balance \
  -H "X-User-Token: $USER_TOKEN"
```

**响应**（假设初始30点，已用1点）:
```json
{
  "code": 200,
  "data": {
    "userPoints": 29.000,
    "balance": 0
  }
}
```

---

## 🔧 管理员工具

### 1. 手动发放每日点数

```bash
# 给所有用户发放30点
curl -X POST http://localhost:8080/admin/points/distribute-daily \
  -H "X-User-Token: $ADMIN_TOKEN"

# 给指定用户发放30点
curl -X POST http://localhost:8080/admin/points/distribute-daily \
  -H "X-User-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId": 52}'
```

### 2. 调整模型点数消耗

```sql
-- 修改 deepseek-v4-flash 为 2点/次
UPDATE model_config 
SET points_per_request = 2.000
WHERE model_name = 'deepseek-v4-flash';

-- 修改 deepseek-v4-pro 为 5点/次
UPDATE model_config 
SET points_per_request = 5.000
WHERE model_name = 'deepseek-v4-pro';
```

### 3. 给用户赠送点数

```bash
curl -X POST http://localhost:8080/admin/points/adjust \
  -H "X-User-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 52,
    "points": 100,
    "reason": "新用户奖励"
  }'
```

### 4. 查看点数消耗统计

```bash
# 查看今日点数消耗
curl -X GET "http://localhost:8080/admin/statistics/dashboard?period=1d" \
  -H "X-User-Token: $ADMIN_TOKEN"
```

---

## 💡 配置建议

### 不同用户等级的每日点数

| 用户等级 | 每日点数 | 可对话次数 | 说明 |
|---------|---------|-----------|------|
| 普通用户 | 30点 | 30次 | 基础配额 |
| 会员用户 | 60点 | 60次 | 双倍配额 |
| VIP用户 | 100点 | 100次 | 高级配额 |

**实现方式**: 在`DailyPointsService`中根据用户等级发放不同点数：

```java
BigDecimal points = new BigDecimal("30"); // 默认30点

if ("VIP".equals(user.getMembership())) {
    points = new BigDecimal("100");
} else if ("MEMBER".equals(user.getMembership())) {
    points = new BigDecimal("60");
}

pointsService.addPoints(userId, points, "GRANT", businessId, description, expireTime);
```

### 点数定价策略

| 模型类型 | 建议点数 | 适用场景 |
|---------|---------|---------|
| 入门模型 | 0-1点 | 吸引用户，高频使用 |
| 标准模型 | 1-3点 | 主力产品，平衡成本 |
| 高级模型 | 5-10点 | 高价值服务，专业需求 |

---

## 🔍 故障排查

### 问题1: 每日点数未自动发放

**检查**:
```bash
# 查看应用日志
tail -f logs/ai-gateway.log | grep "每日点数"

# 检查定时任务是否启用
grep -r "@EnableScheduling" src/main/java/
```

**解决**:
- 确保`StatisticsSchedulerConfig`类上有`@EnableScheduling`注解
- 检查服务器时间是否正确
- 手动触发测试: `POST /admin/points/distribute-daily`

### 问题2: 点数扣除但对话失败

**症状**: 点数被扣了，但返回错误

**解决**:
```bash
# 查看调用日志
curl -X GET "http://localhost:8080/api/user/call-logs?page=1&size=10" \
  -H "X-User-Token: $USER_TOKEN"

# 检查应用日志
tail -f logs/ai-gateway.log | grep "点数结算"
```

系统会自动回滚失败的点数扣除。

### 问题3: 点数不生效（仍显示旧值）

**原因**: Redis缓存未更新

**解决**:
```bash
# 清除用户点数缓存
redis-cli DEL user:points:{userId}

# 或清除所有点数缓存
redis-cli KEYS "user:points:*" | xargs redis-cli DEL
```

---

## 📈 监控指标

### 关键指标

1. **每日点数发放总量**
   ```sql
   SELECT DATE(create_time) as date, SUM(points) as total_points
   FROM points_bill
   WHERE type = 'GRANT' AND description LIKE '%每日%'
   GROUP BY DATE(create_time)
   ORDER BY date DESC
   LIMIT 30;
   ```

2. **每日点数消耗总量**
   ```sql
   SELECT DATE(create_time) as date, SUM(points) as total_consumed
   FROM points_bill
   WHERE type = 'CONSUME'
   GROUP BY DATE(create_time)
   ORDER BY date DESC
   LIMIT 30;
   ```

3. **平均每用户每日对话次数**
   ```sql
   SELECT AVG(daily_count) as avg_conversations
   FROM (
     SELECT user_id, COUNT(*) as daily_count
     FROM call_log
     WHERE DATE(create_time) = CURDATE()
     GROUP BY user_id
   ) t;
   ```

---

## ✨ 最佳实践

### 1. 新用户引导

- 注册即送50点（永久）
- 首次充值额外赠送20%
- 连续登录7天额外赠送30点

### 2. 促销活动

- **周末特惠**: 充值100送50
- **节日活动**: 每日点数翻倍
- **邀请好友**: 每邀请1人送20点

### 3. 点数提醒

当用户点数低于阈值时发送通知：

```java
if (userPoints.compareTo(new BigDecimal("5")) < 0) {
    notificationService.sendNotification(
        userId, 
        "点数不足提醒", 
        "您的点数仅剩" + userPoints + "点，请及时充值"
    );
}
```

---

## 📝 相关文档

- [POINTS_BASED_CHAT_GUIDE.md](POINTS_BASED_CHAT_GUIDE.md) - 点数结算详细说明
- [DEEPSEEK_SETUP_GUIDE.md](DEEPSEEK_SETUP_GUIDE.md) - DeepSeek API配置指南
- [BUG_FIX_REPORT.md](BUG_FIX_REPORT.md) - Bug修复报告

---

**最后更新**: 2026-05-15  
**维护人员**: AI Assistant
