# DeepSeek API 配置与测试快速指南

**更新日期**: 2026-05-15  
**API Key数量**: 3个真实可用的DeepSeek Key

---

## 📋 可用的 DeepSeek API Key

```
Key 1: REDACTED_PROVIDER_API_KEY  (用于 deepseek-v4-flash)
Key 2: REDACTED_PROVIDER_API_KEY  (用于 deepseek-v4-pro)
Key 3: REDACTED_PROVIDER_API_KEY  (备用Key)
```

---

## 🚀 快速部署步骤

### 步骤1: 更新数据库中的API Key

```bash
# 连接MySQL数据库
mysql -u root -p ai_gateway

# 执行更新脚本
source src/main/resources/sql/update_deepseek_api_keys.sql;

# 或者直接执行SQL
DELETE FROM `model_config` WHERE provider = 'deepseek';

INSERT INTO `model_config` 
(`model_name`, `provider`, `base_url`, `api_key`, `input_price`, `output_price`, `points_mode`, `points_per_request`, `status`) 
VALUES
('deepseek-v4-flash', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 0.000000, 0.000000, 'FREE', 0.000, 1),
('deepseek-v4-pro', 'deepseek', 'https://api.deepseek.com/v1/chat/completions', 'REDACTED_PROVIDER_API_KEY', 0.000000, 0.000000, 'PREMIUM', 3.000, 1);

# 验证配置
SELECT model_name, api_key, status FROM model_config WHERE provider = 'deepseek';
```

### 步骤2: 重新编译并重启应用

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo

# 清理旧的编译产物
mvn clean

# 重新编译打包
mvn package -DskipTests

# 停止旧服务
./scripts/application/stop.sh

# 启动新服务
./scripts/application/start.sh

# 查看启动日志（确认无错误）
tail -f logs/ai-gateway.log
```

### 步骤3: 清除Redis缓存（如果有）

```bash
# 清除模型列表缓存
redis-cli DEL model:list:enabled

# 或者重启Redis
redis-cli FLUSHDB
```

---

## 🧪 测试对话功能

### 方法1: 使用自动化测试脚本（推荐）

```bash
# 添加执行权限
chmod +x test_deepseek_chat.sh

# 运行测试脚本
./test_deepseek_chat.sh
```

该脚本会自动测试：
- ✅ 用户注册和登录
- ✅ 非流式对话（deepseek-v4-flash）
- ✅ 流式对话（deepseek-v4-flash）
- ✅ 付费模式对话（deepseek-v4-pro）
- ✅ 调用日志查询

### 方法2: 手动测试（使用curl）

#### 2.1 获取Token

```bash
# 登录获取Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "username": "testuser",
    "password": "Test@123456"
  }'

# 保存返回的token
export TOKEN="your_token_here"
```

#### 2.2 测试非流式对话

```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "model": "deepseek-v4-flash",
    "messages": [
      {
        "role": "user",
        "content": "你好，请用一句话介绍你自己"
      }
    ],
    "mode": "free",
    "maxTokens": 100,
    "temperature": 0.7
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": "我是DeepSeek开发的人工智能助手...",
    "model": "deepseek-v4-flash",
    "usage": {
      "prompt_tokens": 15,
      "completion_tokens": 20,
      "total_tokens": 35
    }
  }
}
```

#### 2.3 测试流式对话

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "model": "deepseek-v4-flash",
    "messages": [
      {
        "role": "user",
        "content": "请写一首关于春天的短诗"
      }
    ],
    "mode": "free",
    "maxTokens": 150,
    "temperature": 0.8
  }'
```

**预期响应** (SSE流式输出):
```
data: {"content":"春"}
data: {"content":"天"}
data: {"content":"来"}
data: {"content":"了"}
...
data: {"finished":true,"inputTokens":12,"outputTokens":25,"cost":0}
```

#### 2.4 测试付费模式

```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: test-device-001" \
  -d '{
    "model": "deepseek-v4-pro",
    "messages": [
      {
        "role": "user",
        "content": "解释一下量子计算的基本原理"
      }
    ],
    "mode": "paid",
    "maxTokens": 300,
    "temperature": 0.7
  }'
```

**注意**: 付费模式需要用户有足够的点数。如果点数不足，可以先充值：

```bash
curl -X POST http://localhost:8080/user/recharge \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -d '{"amount": 100}'
```

---

## 🔍 故障排查

### 问题1: API返回401未授权

**原因**: 数据库中的API Key无效或已过期

**解决方案**:
```sql
-- 检查当前配置的API Key
SELECT model_name, api_key FROM model_config WHERE provider = 'deepseek';

-- 如果Key不正确，重新执行更新脚本
source src/main/resources/sql/update_deepseek_api_keys.sql;
```

### 问题2: 流式对话卡住不动

**原因**: 可能是网络超时或DeepSeek API响应慢

**解决方案**:
```bash
# 查看应用日志
tail -f logs/ai-gateway.log | grep -i "stream\|error"

# 检查网络连接
curl -I https://api.deepseek.com/v1/models \
  -H "Authorization: Bearer REDACTED_PROVIDER_API_KEY"
```

### 问题3: 点数不足错误

**原因**: 付费模式需要消耗点数

**解决方案**:
```bash
# 查看当前点数
curl -X GET http://localhost:8080/api/auth/balance \
  -H "X-User-Token: $TOKEN"

# 充值点数
curl -X POST http://localhost:8080/user/recharge \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -d '{"amount": 100}'

# 或使用免费模式
# 将 "mode": "paid" 改为 "mode": "free"
```

### 问题4: 模型不存在错误

**原因**: model_config表中没有对应的模型配置

**解决方案**:
```sql
-- 查看所有可用模型
SELECT model_name, provider, status FROM model_config;

-- 确保deepseek-v4-flash和deepseek-v4-pro存在且status=1
UPDATE model_config SET status = 1 WHERE model_name IN ('deepseek-v4-flash', 'deepseek-v4-pro');
```

---

## 📊 监控和日志

### 查看实时日志

```bash
# 查看所有日志
tail -f logs/ai-gateway.log

# 只看对话相关日志
tail -f logs/ai-gateway.log | grep -i "chat\|dialogue\|completion"

# 只看错误日志
tail -f logs/ai-gateway.log | grep -i "error\|exception"
```

### 查看调用统计

```bash
# 管理员查看系统统计
curl -X GET http://localhost:8080/api/admin/statistics/dashboard?period=7d \
  -H "X-User-Token: $ADMIN_TOKEN"

# 查看用户调用日志
curl -X GET http://localhost:8080/api/user/call-logs?page=1&size=10 \
  -H "X-User-Token: $TOKEN"
```

---

## 💡 优化建议

### 1. API Key负载均衡

如果需要更高的可用性，可以实现API Key轮询机制：

```java
// 在 ModelConfigService 中添加
private static final List<String> DEEPSEEK_API_KEYS = Arrays.asList(
    "REDACTED_PROVIDER_API_KEY",
    "REDACTED_PROVIDER_API_KEY",
    "REDACTED_PROVIDER_API_KEY"
);

private String getRotatedApiKey() {
    int index = (int) (System.currentTimeMillis() / 1000) % DEEPSEEK_API_KEYS.size();
    return DEEPSEEK_API_KEYS.get(index);
}
```

### 2. 增加重试机制

对于临时失败，可以添加自动重试：

```java
@Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
public String chatWithRetry(...) {
    // 对话逻辑
}
```

### 3. 缓存模型配置

```java
// 已在 ModelConfigService 中实现
// Redis缓存5分钟，减少数据库查询
```

---

## ✅ 验证清单

完成以下检查确保一切正常：

- [ ] 数据库中已更新正确的DeepSeek API Key
- [ ] 应用已成功重启且无错误日志
- [ ] Redis缓存已清除
- [ ] 非流式对话测试通过
- [ ] 流式对话测试通过
- [ ] 付费模式对话测试通过（如有点数）
- [ ] 调用日志正常记录
- [ ] 错误处理正常工作

---

## 📞 支持

如果遇到问题：

1. 查看应用日志: `tail -f logs/ai-gateway.log`
2. 检查数据库配置: `SELECT * FROM model_config WHERE provider = 'deepseek';`
3. 验证API Key有效性: 直接使用curl测试DeepSeek API
4. 查看详细文档: `BUG_FIX_REPORT.md`

---

**最后更新**: 2026-05-15  
**维护人员**: AI Assistant
