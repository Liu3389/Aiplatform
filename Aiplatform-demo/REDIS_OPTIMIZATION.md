# Redis缓存优化配置
# 更新日期: 2026-05-15

## 缓存策略

### 1. 模型配置缓存
- **Key**: `model:config:{modelName}`
- **TTL**: 300秒（5分钟）
- **说明**: 缓存模型配置信息，减少数据库查询

### 2. 用户点数缓存
- **Key**: `user:points:{userId}`
- **TTL**: 60秒（1分钟）
- **说明**: 缓存用户点数余额，高频访问

### 3. 用户信息缓存
- **Key**: `user:info:{userId}`
- **TTL**: 300秒（5分钟）
- **说明**: 缓存用户基本信息

### 4. Token黑名单缓存
- **Key**: `token:blacklist:{token}`
- **TTL**: 剩余有效期
- **说明**: 登出时加入黑名单

### 5. 限流计数器
- **Key**: `rate:limit:{ip}:{api}`
- **TTL**: 60秒
- **说明**: IP限流计数

### 6. 免费额度计数
- **Key**: `free:quota:{userId}:{date}`
- **TTL**: 当日剩余时间
- **说明**: 每日免费额度使用计数

### 7. 会话列表缓存
- **Key**: `conversation:list:{userId}`
- **TTL**: 300秒
- **说明**: 用户会话列表

### 8. 统计仪表盘缓存
- **Key**: `stats:dashboard:{period}`
- **TTL**: 3600秒（1小时）
- **说明**: 管理员仪表盘统计数据

## 缓存清理策略

### 定期清理
```bash
# 每天凌晨3点清理过期缓存
redis-cli KEYS "model:config:*" | xargs -I {} redis-cli EXPIRE {} 300
redis-cli KEYS "user:points:*" | xargs -I {} redis-cli EXPIRE {} 60
redis-cli KEYS "stats:dashboard:*" | xargs -I {} redis-cli DEL {}
```

### 事件触发清理
- 用户充值 → 清除 `user:points:{userId}`
- 模型配置更新 → 清除 `model:config:*`
- 用户信息更新 → 清除 `user:info:{userId}`

## 内存优化建议

### 1. 设置最大内存
```conf
maxmemory 2gb
maxmemory-policy allkeys-lru
```

### 2. 启用压缩
```conf
# 对于大value启用压缩
redis.conf: activedefrag yes
```

### 3. 监控命令
```bash
# 查看内存使用
redis-cli INFO memory

# 查看key数量
redis-cli DBSIZE

# 查看慢查询
redis-cli SLOWLOG GET 10
```

## 性能优化

### 1. Pipeline批量操作
```java
// 批量获取用户点数
List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (Long userId : userIds) {
        connection.stringCommands().get(("user:points:" + userId).getBytes());
    }
    return null;
});
```

### 2. Lua脚本原子操作
```lua
-- 扣减点数（原子操作）
local key = KEYS[1]
local deduct = tonumber(ARGV[1])
local current = tonumber(redis.call('GET', key) or '0')
if current >= deduct then
    redis.call('SET', key, current - deduct)
    return 1
else
    return 0
end
```

### 3. 连接池优化
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 50    # 最大连接数
        max-idle: 20      # 最大空闲连接
        min-idle: 5       # 最小空闲连接
        max-wait: 3000    # 最大等待时间(ms)
```
