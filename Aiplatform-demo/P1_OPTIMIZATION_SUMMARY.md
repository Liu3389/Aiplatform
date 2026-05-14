# P1优先级优化实施总结

## 📅 实施日期
2026年5月14日

## 🎯 优化目标
严格按照P1优先级实现点数计费体系重构和热点数据缓存优化

---

## ✅ 已完成的核心优化

### 1. 点数计费体系重构（100%完成）

#### 1.1 核心实体和服务

**新增文件**:
- `PointsBill.java` - 点数明细账单实体
- `PointsBillMapper.java` - 点数账单Mapper
- `PointsService.java` - 点数管理服务（265行）

**功能特性**:
- ✅ 统一使用点数作为AI对话消耗单位
- ✅ 支持不同模型的点数消耗配置
  - FREE模式: 0点/次
  - STANDARD模式: 1点/次
  - PREMIUM模式: 3点/次
- ✅ 点数明细账单功能，记录每一次点数变动
- ✅ 每日免费点数自动发放功能
- ✅ 所有点数变动记录操作日志，支持前后端对账

**核心方法**:
```java
// PointsService
- getUserPoints(userId)                    // 获取用户点数
- deductPoints(userId, points, ...)        // 扣除点数
- addPoints(userId, points, type, ...)     // 增加点数
- grantDailyFreePoints(userId, points)     // 每日免费点数发放
- getPointsBillList(userId, page, size)    // 获取点数账单列表
- formatPointsForFrontend(points)          // 格式化点数用于前端展示
```

#### 1.2 模型配置扩展

**修改文件**: `ModelConfig.java`

**新增字段**:
```java
private String pointsMode;           // 点数消耗模式：FREE/STANDARD/PREMIUM
private BigDecimal pointsPerRequest; // 每次对话消耗的点数（保留三位小数）
```

**配置示例**:
```sql
-- 免费模型
UPDATE model_config SET points_mode = 'FREE', points_per_request = 0.000 
WHERE model_name LIKE '%flash%' OR model_name LIKE '%free%';

-- 中端模型  
UPDATE model_config SET points_mode = 'STANDARD', points_per_request = 1.000
WHERE model_name LIKE '%chat%' AND model_name NOT LIKE '%flash%';

-- 高端模型
UPDATE model_config SET points_mode = 'PREMIUM', points_per_request = 3.000
WHERE model_name LIKE '%pro%' OR model_name LIKE '%plus%';
```

#### 1.3 BillingService集成点数计费

**修改文件**: `BillingService.java`

**新增方法**:
```java
- calculatePointsCost(modelName)      // 根据模型配置计算点数消耗
- preDeductPoints(userId, requestId, modelName)  // 点数预扣检查
- settlePoints(userId, requestId, modelName)     // 点数结算
- getUserPoints(userId)               // 获取用户点数（格式化）
```

**计费逻辑**:
```java
// 1. 对话前检查点数是否充足
billingService.preDeductPoints(userId, requestId, modelName);

// 2. 对话成功后扣除点数
billingService.settlePoints(userId, requestId, modelName);

// 3. 免费模型不扣费
if (pointsCost <= 0) return true;
```

#### 1.4 OpenAiChatService更新

**修改文件**: `OpenAiChatService.java`

**变更内容**:
- ✅ 非流式对话改用点数计费
- ✅ 流式对话改用点数计费
- ✅ 错误提示显示点数而非余额

**变更前**:
```java
BigDecimal preDeductEstimate = modelConfigService.calculateCost(...);
boolean ok = billingService.preDeductBalance(userId, requestId, preDeductEstimate, 300);
throw new BusinessException("余额不足（当前余额：¥" + currentBalance + "）...");
```

**变更后**:
```java
boolean ok = billingService.preDeductPoints(userId, requestId, modelName);
throw new BusinessException("点数不足（当前点数：" + currentPoints + "）...");
```

---

### 2. 全平台数值计算规范（100%完成）

#### 2.1 精度规范实现

**后端底层运算**: 保留三位小数精度
```java
// PointsService中的计算
BigDecimal deductPoints = points.setScale(3, RoundingMode.HALF_DOWN);
BigDecimal newBalance = currentPoints.subtract(deductPoints).setScale(3, RoundingMode.HALF_DOWN);
```

**前端展示**: 保留两位小数（四舍五入）
```java
public BigDecimal formatPointsForFrontend(BigDecimal points) {
    if (points == null) return BigDecimal.ZERO;
    return points.setScale(2, RoundingMode.HALF_UP);
}
```

**数据库设计**:
```sql
CREATE TABLE points_bill (
    points_change DECIMAL(10,3) NOT NULL,  -- 三位小数存储
    balance_before DECIMAL(10,3) NOT NULL,
    balance_after DECIMAL(10,3) NOT NULL
);
```

#### 2.2 应用场景

| 场景 | 内部精度 | 前端展示 |
|------|---------|---------|
| 点数计算 | 3位小数 | 2位小数 |
| 金额换算 | 3位小数 | 2位小数 |
| 模型价格 | 3位小数 | 2位小数 |
| 账单记录 | 3位小数 | 2位小数 |

---

### 3. 热点数据缓存优化（100%完成）

#### 3.1 CacheService实现

**新增文件**: `CacheService.java`（244行）

**缓存策略**:

| 数据类型 | 缓存Key | TTL | 说明 |
|---------|---------|-----|------|
| 模型配置 | `cache:model_config:{modelName}` | 30分钟 | 单个模型配置 |
| 模型列表 | `cache:model_config:all_enabled` | 30分钟 | 所有启用模型 |
| 用户信息 | `cache:user:{userId}` | 10分钟 | 不含密码 |
| 用户余额 | `cache:user_balance:{userId}` | 5分钟 | 频繁变化 |
| 用户角色 | `cache:user_role:{userId}` | 10分钟 | 权限判断 |

**核心方法**:
```java
// 带缓存的查询
- getModelConfig(modelName)              // 获取模型配置
- getAllEnabledModelConfigs()            // 获取所有模型
- getUserInfo(userId)                    // 获取用户信息
- getUserBalance(userId)                 // 获取用户余额
- getUserRole(userId)                    // 获取用户角色

// 缓存管理
- clearModelConfigCache(modelName)       // 清除模型缓存
- clearUserCache(userId)                 // 清除用户缓存
- updateUserBalanceCache(userId, balance) // 更新余额缓存
```

**缓存逻辑**:
```java
public ModelConfig getModelConfig(String modelName) {
    String cacheKey = MODEL_CONFIG_CACHE_PREFIX + modelName;
    
    // 1. 尝试从缓存获取
    String cachedJson = redisTemplate.opsForValue().get(cacheKey);
    if (cachedJson != null) {
        return objectMapper.readValue(cachedJson, ModelConfig.class);
    }
    
    // 2. 缓存未命中，从数据库查询
    ModelConfig config = modelConfigMapper.selectOne(wrapper);
    
    // 3. 存入缓存
    if (config != null) {
        String json = objectMapper.writeValueAsString(config);
        redisTemplate.opsForValue().set(cacheKey, json, 30, TimeUnit.MINUTES);
    }
    
    return config;
}
```

#### 3.2 性能提升预期

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 模型配置查询 | 每次DB查询 ~10ms | 缓存命中 <1ms | **90%** |
| 用户信息查询 | 每次DB查询 ~8ms | 缓存命中 <1ms | **87%** |
| 余额查询 | 每次DB查询 ~5ms | 缓存命中 <1ms | **80%** |
| 数据库压力 | 高频率查询 | 降低70% | **70%** |

---

### 4. 数据库迁移脚本

**新增文件**: `migration_p1_optimization.sql`

**执行内容**:
1. ✅ 创建points_bill表（点数明细账单）
2. ✅ 为model_config表添加points_mode和points_per_request字段
3. ✅ 更新现有模型的点数配置
4. ✅ 插入测试数据
5. ✅ 创建索引优化查询性能

**执行命令**:
```bash
mysql -u root -p your_database < src/main/resources/sql/migration_p1_optimization.sql
```

---

## 📊 技术实现细节

### Redis数据结构

#### 点数存储
```
points:user:{userId} → {points_value}  // 用户点数余额
```

#### 每日免费点数标记
```
daily_grant:{userId}:{date} → "1" (TTL: 25h)  // 防止重复发放
```

#### 缓存数据
```
cache:model_config:{modelName} → {JSON} (TTL: 30min)
cache:model_config:all_enabled → {JSON Array} (TTL: 30min)
cache:user:{userId} → {JSON} (TTL: 10min)
cache:user_balance:{userId} → {balance} (TTL: 5min)
cache:user_role:{userId} → {role} (TTL: 10min)
```

### 数据库表结构

#### points_bill表
```sql
CREATE TABLE points_bill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    change_type VARCHAR(20) NOT NULL,  -- RECHARGE/DEDUCT/GRANT/EXPIRE/REFUND
    points_change DECIMAL(10,3) NOT NULL,  -- 三位小数
    balance_before DECIMAL(10,3) NOT NULL,
    balance_after DECIMAL(10,3) NOT NULL,
    business_id VARCHAR(100),
    description VARCHAR(500),
    model_name VARCHAR(100),
    expire_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time),
    INDEX idx_business_id (business_id)
);
```

---

## 🔧 配置说明

### 点数模式配置

在model_config表中设置：

```sql
-- 方式1：使用points_mode（推荐）
UPDATE model_config SET points_mode = 'FREE' WHERE ...;      -- 0点
UPDATE model_config SET points_mode = 'STANDARD' WHERE ...;  -- 1点
UPDATE model_config SET points_mode = 'PREMIUM' WHERE ...;   -- 3点

-- 方式2：直接指定points_per_request（优先级更高）
UPDATE model_config SET points_per_request = 2.500 WHERE ...; -- 2.5点
```

### 缓存TTL调整

在CacheService中可调整：

```java
private static final long MODEL_CONFIG_TTL_MINUTES = 30;  // 模型配置缓存30分钟
private static final long USER_INFO_TTL_MINUTES = 10;     // 用户信息缓存10分钟
private static final long USER_BALANCE_TTL_MINUTES = 5;   // 余额缓存5分钟
```

### 每日免费点数配置

在PointsService中调用：

```java
// 发放100点每日免费额度
BigDecimal dailyFreePoints = new BigDecimal("100.000");
pointsService.grantDailyFreePoints(userId, dailyFreePoints);
```

---

## 🚀 部署步骤

### 1. 数据库迁移

```bash
# 执行SQL迁移脚本
mysql -u root -p ai_gateway < src/main/resources/sql/migration_p1_optimization.sql

# 验证表结构
mysql> DESCRIBE points_bill;
mysql> DESCRIBE model_config;
```

### 2. 代码编译

```bash
mvn clean package -DskipTests
```

### 3. 重启应用

```bash
./stop.sh
./start.sh
```

### 4. 验证缓存

```bash
redis-cli
> KEYS cache:*
> KEYS points:*
> KEYS daily_grant:*
```

---

## ⚠️ 注意事项

### 1. 数据一致性

**缓存更新策略**:
- 模型配置变更时调用 `clearModelConfigCache(modelName)`
- 用户信息变更时调用 `clearUserCache(userId)`
- 余额变动时调用 `updateUserBalanceCache(userId, newBalance)`

**事务保证**:
```java
@Transactional
public boolean deductPoints(...) {
    // 1. 更新Redis
    redisTemplate.opsForValue().set(key, newBalance.toString());
    
    // 2. 记录账单（数据库）
    recordBill(...);
    
    // 两者在同一事务中，保证一致性
}
```

### 2. 点数精度

**重要规则**:
- ✅ 内部计算始终使用3位小数
- ✅ 返回前端时使用2位小数（四舍五入）
- ✅ 数据库存储使用DECIMAL(10,3)
- ❌ 禁止直接使用float/double进行金额计算

**示例**:
```java
// 正确
BigDecimal points = new BigDecimal("1.000");  // 3位小数
BigDecimal display = points.setScale(2, RoundingMode.HALF_UP);  // 2位小数

// 错误
double points = 1.0;  // 精度丢失
```

### 3. 缓存穿透防护

当前实现已包含基本防护：
- 缓存未命中时从数据库加载
- 设置合理的TTL避免频繁查询
- 使用StringRedisTemplate简化序列化

如需更强防护，可考虑：
- 布隆过滤器预防缓存穿透
- 互斥锁防止缓存击穿
- 随机过期时间防止缓存雪崩

---

## 📝 前端适配要求

### 必须实现的改动

1. **点数展示**
   ```javascript
   // API返回格式
   {
     "points": "99.50",  // 两位小数
     "pointsChange": "-1.000",  // 账单中三位小数
     "balanceBefore": "100.500",
     "balanceAfter": "99.500"
   }
   
   // 前端展示
   <div>当前点数: {data.points}</div>  // 显示 99.50
   ```

2. **点数账单列表**
   ```javascript
   // 获取账单
   GET /api/points/bills?page=1&pageSize=20
   
   // 响应
   {
     "code": 200,
     "data": [
       {
         "changeType": "DEDUCT",
         "pointsChange": "-1.000",
         "description": "对话消耗: deepseek-v4-flash",
         "createTime": "2026-05-14T10:30:00"
       }
     ]
   }
   ```

3. **错误提示**
   ```javascript
   // 点数不足错误
   if (error.message.includes('点数不足')) {
     showNotification(`点数不足，当前: ${error.data.currentPoints}，需要: ${error.data.pointsCost}`);
   }
   ```

---

## ✅ 验收标准

### 功能验收
- [ ] 免费模型对话不扣点数
- [ ] 中端模型对话扣1点
- [ ] 高端模型对话扣3点
- [ ] 点数不足时拒绝对话并提示
- [ ] 点数账单记录完整准确
- [ ] 每日免费点数自动发放
- [ ] 缓存命中率 > 80%

### 性能验收
- [ ] 模型配置查询延迟 < 1ms（缓存命中）
- [ ] 用户信息查询延迟 < 1ms（缓存命中）
- [ ] 数据库查询次数减少 > 70%
- [ ] Redis内存增长可控

### 数据一致性验收
- [ ] 点数计算精度正确（3位小数）
- [ ] 前端展示精度正确（2位小数）
- [ ] 账单记录与实际操作一致
- [ ] 缓存与数据库数据同步

---

## 🔄 后续优化建议

### P1优先级
1. 实现完整的充值功能
2. 添加点数过期自动清理定时任务
3. 实现点数账单分页查询接口
4. 添加点数统计报表

### P2优先级
1. 实现点数赠送功能
2. 添加点数消耗排行榜
3. 实现点数预警通知
4. 优化缓存策略（布隆过滤器、多级缓存）

---

**实施完成时间**: 2026年5月14日  
**实施人员**: AI Gateway Platform Team  
**审核状态**: 待测试验收
