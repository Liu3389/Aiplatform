# 凌点AI平台 - 套餐制度业务逻辑说明

**文档版本**: v1.0  
**更新日期**: 2026年5月15日  
**适用系统**: 凌点AI模型调度网关

---

## 📋 目录

1. [业务模式概述](#业务模式概述)
2. [核心概念](#核心概念)
3. [充值流程](#充值流程)
4. [套餐购买流程](#套餐购买流程)
5. [会员权益体系](#会员权益体系)
6. [点数计费规则](#点数计费规则)
7. [数据库设计](#数据库设计)
8. [API接口清单](#api接口清单)
9. [管理员后台功能](#管理员后台功能)
10. [常见问题](#常见问题)

---

## 业务模式概述

凌点AI平台采用**双层货币体系 + 套餐订阅制**的商业模式：

### 💰 双层货币体系

| 货币类型 | 用途 | 获取方式 |
|---------|------|---------|
| **Balance（余额）** | 用于购买套餐、支付费用 | 用户充值（人民币） |
| **Points（点数）** | 用于API调用计费 | 购买套餐获得、活动赠送 |

### 📦 两种套餐模式

#### 模式A：按点数充值套餐（一次性购买）
- **特点**: 越贵的套餐，单价越便宜
- **适合**: 偶尔使用、灵活消费的用户
- **示例**:
  ```
  基础版: 10元 = 100点   (1元=10点)
  标准版: 50元 = 600点   (1元=12点) ✅ 优惠20%
  高级版: 100元 = 1500点 (1元=15点) ✅ 优惠50%
  ```

#### 模式B：按时长订阅套餐（会员制度）
- **特点**: 按月/季/年付费，享受固定额度和特权
- **适合**: 高频使用、稳定需求的用户
- **示例**:
  ```
  白银会员: 30元/月  = 每月500点额度 + 基础权益
  黄金会员: 80元/月  = 每月1500点额度 + 优先响应
  钻石会员: 200元/月 = 每月5000点额度 + 专属通道
  ```

---

## 核心概念

### 1. Balance（余额）
- **定义**: 用户的人民币账户余额
- **存储**: `user.balance` (DECIMAL 10,2)
- **Redis Key**: `balance:{userId}`
- **用途**: 
  - 购买套餐时扣款
  - 不支持直接用于API调用

### 2. Points（点数）
- **定义**: 平台的虚拟货币，用于API调用计费
- **存储**: Redis `points:user:{userId}` (DECIMAL 10,3)
- **账单表**: `points_bill`
- **用途**:
  - API调用时消耗点数
  - 不同模型消耗不同点数

### 3. Package Template（套餐模板）
- **定义**: 管理员配置的套餐方案
- **表**: `package_template`
- **字段**:
  - `package_code`: 套餐代码（唯一标识）
  - `package_name`: 套餐名称
  - `identity_label`: 身份标识（如：至尊会员）
  - `points`: 包含点数
  - `price`: 价格（元）
  - `duration_days`: 有效期天数
  - `daily_call_limit`: 每日调用限制
  - `monthly_call_limit`: 每月调用限制
  - `priority_level`: 优先级等级

### 4. User Subscription（用户订阅）
- **定义**: 用户购买套餐的记录
- **表**: `user_subscription`
- **状态**: ACTIVE（生效中）、EXPIRED（已过期）、CANCELLED（已取消）

---

## 充值流程

### 流程图

```
用户发起充值 → 增加balance → 同步Redis → 完成
```

### 详细说明

1. **用户调用接口**: `POST /api/user/recharge`
   ```json
   {
     "amount": 100.00
   }
   ```

2. **后端处理** (`UserService.recharge()`):
   ```java
   @Transactional(rollbackFor = Exception.class)
   public void recharge(Long userId, BigDecimal amount) {
       // 1. 查询用户
       User user = userMapper.selectById(userId);
       
       // 2. 更新余额
       BigDecimal balanceAfter = user.getBalance().add(amount);
       user.setBalance(balanceAfter);
       userMapper.updateById(user);
       
       // 3. 同步到Redis
       billingService.syncUserBalanceToRedis(userId, balanceAfter);
       
       // ⚠️ 注意：此时不增加点数！
       // 用户需要用余额购买套餐才能获得点数
   }
   ```

3. **结果**:
   - ✅ `user.balance` 增加
   - ✅ Redis `balance:{userId}` 更新
   - ❌ **不会**增加 points
   - ❌ **不会**记录 points_bill

---

## 套餐购买流程

### 流程图

```
选择套餐 → 检查余额 → 扣款 → 授予点数 → 创建订阅 → 更新会员标识 → 完成
```

### 详细说明

1. **用户查看可用套餐**: `GET /api/user/subscriptions/packages`
   
   返回示例：
   ```json
   [
     {
       "packageCode": "basic",
       "packageName": "基础版",
       "identityLabel": "畅享会员",
       "points": 100,
       "price": 29.9,
       "durationDays": 30,
       "dailyCallLimit": 50,
       "monthlyCallLimit": 1500,
       "priorityLevel": 1
     },
     {
       "packageCode": "premium",
       "packageName": "专业版",
       "identityLabel": "尊享会员",
       "points": 500,
       "price": 99.9,
       "durationDays": 30,
       "dailyCallLimit": 200,
       "monthlyCallLimit": 6000,
       "priorityLevel": 2
     }
   ]
   ```

2. **用户购买套餐**: `POST /api/user/subscriptions/purchase`
   ```json
   {
     "packageCode": "premium",
     "couponId": 1  // 可选，使用优惠券
   }
   ```

3. **后端处理** (`UserSubscriptionService.purchasePackage()`):
   ```java
   @Transactional
   public Map<String, Object> purchasePackage(Long userId, String packageCode, Long couponId) {
       // 1. 获取套餐信息
       PackageTemplate pkg = packageTemplateService.getByCode(packageCode);
       
       // 2. 计算价格和优惠
       BigDecimal originalPrice = pkg.getPrice();
       BigDecimal discount = couponService.useCoupon(...); // 如果有优惠券
       BigDecimal actualPrice = originalPrice.subtract(discount);
       
       // 3. 检查用户余额是否充足
       User user = userMapper.selectById(userId);
       if (user.getBalance().compareTo(actualPrice) < 0) {
           throw new RuntimeException("余额不足");
       }
       
       // 4. 扣除余额
       user.setBalance(user.getBalance().subtract(actualPrice));
       userMapper.updateById(user);
       billingService.syncUserBalanceToRedis(userId, user.getBalance());
       
       // 5. 创建订阅记录
       UserSubscription subscription = new UserSubscription();
       subscription.setUserId(userId);
       subscription.setPackageId(pkg.getId());
       subscription.setPointsGranted(pkg.getPoints());
       subscription.setPricePaid(actualPrice);
       subscription.setStartTime(LocalDateTime.now());
       subscription.setExpireTime(LocalDateTime.now().plusDays(pkg.getDurationDays()));
       subscription.setStatus("ACTIVE");
       userSubscriptionMapper.insert(subscription);
       
       // 6. 授予点数（关键步骤！）
       if (pkg.getPoints().compareTo(BigDecimal.ZERO) > 0) {
           String businessId = "sub_" + subscription.getId();
           String description = "购买套餐[" + pkg.getPackageName() + "]赠送点数";
           pointsService.addPoints(userId, pkg.getPoints(), "GRANT", businessId, 
                                   description, subscription.getExpireTime());
       }
       
       // 7. 更新用户会员标识
       updateUserMembership(userId, pkg.getIdentityLabel(), subscription.getExpireTime());
       
       return result;
   }
   ```

4. **结果**:
   - ✅ `user.balance` 减少（扣款）
   - ✅ `points` 增加（获得套餐点数）
   - ✅ 记录 `points_bill`（变动类型为 GRANT）
   - ✅ 创建 `user_subscription` 记录
   - ✅ 用户获得会员身份标识

---

## 会员权益体系

### 权益对比表

| 权益项 | 免费版 | 畅享会员 | 尊享会员 | 至尊会员 |
|-------|--------|---------|---------|---------|
| **身份标识** | 普通用户 | 畅享会员 | 尊享会员 | 至尊会员 |
| **初始点数** | 0 | 100点 | 500点 | 2000点 |
| **价格** | 免费 | 29.9元 | 99.9元 | 299.9元 |
| **有效期** | 永久 | 30天 | 30天 | 30天 |
| **每日调用限制** | 10次 | 50次 | 200次 | 1000次 |
| **每月调用限制** | 300次 | 1500次 | 6000次 | 30000次 |
| **单次最大Token** | 4096 | 8192 | 16384 | 32768 |
| **优先级** | 0（最低） | 1 | 2 | 3（最高） |
| **技术支持** | ❌ | ❌ | ✅ | ✅ 专属 |
| **API专属通道** | ❌ | ❌ | ❌ | ✅ |

### 性价比分析

以30天为例：

| 套餐 | 价格 | 点数 | 每元获得点数 | 日均成本 |
|------|------|------|-------------|---------|
| 基础版 | 29.9元 | 100点 | 3.34点/元 | 0.997元/天 |
| 专业版 | 99.9元 | 500点 | 5.01点/元 ✅ | 3.33元/天 |
| 旗舰版 | 299.9元 | 2000点 | 6.67点/元 ✅✅ | 9.997元/天 |

**结论**: 越贵的套餐，每元获得的点数越多，性价比越高！

---

## 点数计费规则

### 模型点数消耗

不同AI模型调用消耗的点数不同：

| 模型 | 每次调用消耗 | 说明 |
|------|------------|------|
| deepseek-v4-flash | 0.5点 | 免费模型，快速响应 |
| deepseek-v4-pro | 2.0点 | 高级模型，更准确 |
| gpt-4 | 5.0点 | OpenAI顶级模型 |
| claude-3-opus | 8.0点 | Anthropic最强模型 |

### 计费公式

```
实际消耗点数 = 基础点数 × Token系数 × 复杂度系数
```

- **Token系数**: 根据输入+输出Token数计算
- **复杂度系数**: 根据模型难度调整

---

## 数据库设计

### 核心表结构

#### 1. user（用户表）
```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `balance` DECIMAL(10,2) DEFAULT 0 COMMENT '余额（元）',
  `membership` VARCHAR(32) DEFAULT 'free' COMMENT '会员等级',
  `identity_label` VARCHAR(32) COMMENT '身份标识',
  PRIMARY KEY (`id`)
);
```

#### 2. package_template（套餐模板表）
```sql
CREATE TABLE `package_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `package_code` VARCHAR(32) NOT NULL UNIQUE COMMENT '套餐代码',
  `package_name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `identity_label` VARCHAR(32) COMMENT '身份标识',
  `points` DECIMAL(10,3) NOT NULL DEFAULT 0 COMMENT '包含点数',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价格（元）',
  `duration_days` INT COMMENT '有效期天数',
  `daily_call_limit` INT DEFAULT 0 COMMENT '每日调用限制',
  `monthly_call_limit` INT DEFAULT 0 COMMENT '每月调用限制',
  `priority_level` INT DEFAULT 0 COMMENT '优先级',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  PRIMARY KEY (`id`)
);
```

#### 3. user_subscription（用户订阅表）
```sql
CREATE TABLE `user_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `package_id` BIGINT NOT NULL,
  `package_code` VARCHAR(32) NOT NULL,
  `points_granted` DECIMAL(10,3) COMMENT '授予点数',
  `price_paid` DECIMAL(10,2) COMMENT '实际支付',
  `start_time` DATETIME NOT NULL,
  `expire_time` DATETIME COMMENT '过期时间',
  `status` VARCHAR(20) DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
);
```

#### 4. points_bill（点数账单表）
```sql
CREATE TABLE `points_bill` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `change_type` VARCHAR(20) NOT NULL COMMENT 'RECHARGE/CONSUME/GRANT/DEDUCT',
  `points_change` DECIMAL(10,3) NOT NULL COMMENT '点数变动（正数增加，负数减少）',
  `balance_after` DECIMAL(10,3) COMMENT '变动后余额',
  `business_id` VARCHAR(64) COMMENT '业务ID',
  `description` VARCHAR(500) COMMENT '描述',
  `expire_time` DATETIME COMMENT '过期时间',
  `create_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
);
```

---

## API接口清单

### 用户端接口

#### 充值相关
- `POST /api/user/recharge` - 充值（增加balance）
- `POST /api/user/recharge-with-coupon` - 使用优惠券充值
- `GET /api/user/balance` - 查询余额
- `GET /api/user/recharge-records` - 充值记录

#### 套餐订阅相关
- `GET /api/user/subscriptions/packages` - 获取可用套餐列表
- `GET /api/user/subscriptions/current` - 获取当前订阅状态
- `GET /api/user/subscriptions/history` - 订阅历史
- `POST /api/user/subscriptions/purchase` - 购买套餐
- `POST /api/user/subscriptions/cancel/{id}` - 取消订阅

#### 点数账单相关
- `GET /api/user/points-bills` - 获取点数账单列表 ⭐NEW
  - 支持分页：`?page=1&size=20`
  - 支持筛选：`?changeType=GRANT`

### 管理员端接口

#### 套餐管理
- `GET /api/admin/packages/list` - 获取套餐列表
- `GET /api/admin/packages/{id}` - 套餐详情
- `POST /api/admin/packages/create` - 创建套餐
- `PUT /api/admin/packages/update` - 更新套餐
- `POST /api/admin/packages/toggle-status` - 上架/下架
- `DELETE /api/admin/packages/{id}` - 删除套餐

#### 用户管理
- `POST /api/admin/users/grant-points` - 手动发放点数
- `POST /api/admin/batch/grant-points` - 批量发放点数
- `POST /api/admin/batch/activate-membership` - 批量开通会员

---

## 管理员后台功能

### 1. 套餐配置管理

管理员可以：
- ✅ 创建新套餐（设置价格、点数、有效期等）
- ✅ 修改现有套餐（调整价格、增加点数等）
- ✅ 上架/下架套餐
- ✅ 删除不再使用的套餐

**操作路径**: 管理员后台 → 套餐模板管理

### 2. 用户点数管理

管理员可以：
- ✅ 手动给单个用户发放点数（活动奖励、补偿等）
- ✅ 批量给多个用户发放点数
- ✅ 查看用户的点数账单
- ✅ 扣除违规用户的点数

**操作路径**: 管理员后台 → 点数管理

### 3. 会员管理

管理员可以：
- ✅ 批量为用户开通会员
- ✅ 延长会员有效期
- ✅ 降级/升级会员等级
- ✅ 查看会员统计数据

**操作路径**: 管理员后台 → 会员管理

### 4. 数据统计

管理员可以查看：
- 📊 充值统计（每日/每周/每月）
- 📊 套餐销售统计
- 📊 点数消耗统计
- 📊 会员转化率

**操作路径**: 管理员后台 → 统计分析

---

## 常见问题

### Q1: 为什么充值不直接增加点数？

**A**: 这是为了保持商业模式的灵活性：
1. **充值**只是将人民币存入账户（balance）
2. **购买套餐**才是消费行为，获得对应的点数和权益
3. 这样用户可以自由选择何时购买、购买哪种套餐
4. 方便实现"越贵越划算"的阶梯定价策略

### Q2: 余额和点数的关系是什么？

**A**: 
- **余额（balance）**: 人民币，用于购买套餐
- **点数（points）**: 虚拟货币，用于API调用计费
- **转换关系**: 通过购买套餐实现，不是固定比例

### Q3: 套餐的点数有有效期吗？

**A**: 
- 如果套餐设置了 `duration_days`，则点数会在过期时清理
- 例如：30天套餐，30天后未使用的点数会被清空
- 这鼓励用户在有效期内充分使用

### Q4: 如何体现"越贵越划算"？

**A**: 通过套餐配置实现：
```
基础版: 29.9元 = 100点  → 每元3.34点
专业版: 99.9元 = 500点  → 每元5.01点（优惠50%）
旗舰版: 299.9元 = 2000点 → 每元6.67点（优惠100%）
```

管理员在后台配置套餐时，应该确保高价套餐的性价比更高。

### Q5: 用户可以同时拥有多个套餐吗？

**A**: 
- 可以！用户可以多次购买同一套餐或不同套餐
- 每个套餐会创建独立的 `user_subscription` 记录
- 点数会累加到用户的总点数中
- 会员权益取最高等级的套餐

### Q6: 点数用完了怎么办？

**A**: 
1. **继续充值并购买套餐**（推荐，性价比高）
2. **等待每日免费额度重置**（如果还有免费次数）
3. **参与活动获得赠送点数**（关注平台活动）

### Q7: 如何查看点数使用记录？

**A**: 调用接口 `GET /api/user/points-bills`
- 可以看到所有点数变动：充值、消费、赠送、扣除
- 支持按类型筛选：`?changeType=CONSUME` 只看消费记录
- 支持分页：`?page=1&size=50`

---

## 技术实现要点

### 1. 事务一致性

所有涉及余额和点数变动的操作都使用了 `@Transactional`：
```java
@Transactional(rollbackFor = Exception.class)
public Map<String, Object> purchasePackage(...) {
    // 扣款、授点、创建订阅都在一个事务中
    // 任何一步失败都会回滚
}
```

### 2. Redis缓存同步

余额和点数都同步到Redis，提高性能：
```java
// 余额
billingService.syncUserBalanceToRedis(userId, balance);

// 点数
redisTemplate.opsForValue().set("points:user:" + userId, points);
```

### 3. 点数过期清理

定时任务清理过期的点数：
```java
@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
public void cleanExpiredPoints() {
    // 查询已过期的GRANT记录
    // 扣除相应点数
    // 更新账单描述
}
```

### 4. 并发控制

使用Redis锁防止并发问题：
```java
String lockKey = "points:lock:" + userId;
Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
if (!locked) {
    throw new BusinessException("操作过于频繁，请稍后再试");
}
try {
    // 执行点数变动
} finally {
    redisTemplate.delete(lockKey);
}
```

---

## 总结

凌点AI平台的套餐制度设计遵循以下原则：

1. **双层货币分离**: balance（人民币）和 points（点数）职责明确
2. **套餐驱动消费**: 通过购买套餐获得点数，而非直接充值点数
3. **阶梯定价策略**: 越贵的套餐性价比越高，鼓励大额消费
4. **会员权益体系**: 不同等级享受不同服务和优先级
5. **灵活订阅模式**: 支持一次性购买和周期性订阅两种方式
6. **完整审计追踪**: 所有点数变动都有账单记录
7. **管理员可控**: 后台可灵活配置套餐和管理用户

这套系统既保证了平台的收入，又给用户提供了灵活多样的选择，实现了双赢！

---

**文档维护**: AI Assistant  
**最后更新**: 2026年5月15日  
**下次审查**: 2026年6月15日
