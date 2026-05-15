# 点数结算对话系统说明

**更新日期**: 2026-05-15  
**核心原则**: **所有对话统一使用点数结算，无免费/付费模式区分**

---

## 🎯 系统设计理念

### 核心特性

1. ✅ **统一点数结算**
   - 所有模型对话都消耗点数
   - 无"免费模式"和"付费模式"之分
   - 用户充值点数后即可使用任意模型

2. ✅ **模型商城透明化**
   - 每个模型显示每次对话消耗的点数
   - 用户可清楚了解成本
   - 点击模型直接进入对话（自动扣点）

3. ✅ **灵活的点数管理**
   - 支持充值获得点数
   - 支持管理员赠送点数
   - 支持套餐订阅赠送点数

---

## 📊 模型点数消耗示例

| 模型 | 每次对话消耗点数 | 说明 |
|------|----------------|------|
| deepseek-v4-flash | 0点 | 免费模型（仍走点数系统，但消耗为0） |
| deepseek-v4-pro | 3点 | 高级模型 |
| gpt-4 | 5点 | 高端模型 |
| claude-3 | 4点 | 高级模型 |

**注意**: 即使是"免费"模型，也通过点数系统管理（消耗0点），这样便于统一统计和管理。

---

## 🔧 API Key问题修复

### 问题现象

测试时返回错误：
```json
{
  "code": 401,
  "message": "API Key无效或已过期"
}
```

### 解决方案

#### 方法1: 执行SQL修复脚本

```bash
# 连接MySQL
mysql -u root -p ai_gateway

# 执行修复脚本
source src/main/resources/sql/check_and_fix_api_keys.sql;
```

#### 方法2: 手动更新

```sql
-- 更新 deepseek-v4-flash 的 API Key
UPDATE model_config 
SET api_key = 'REDACTED_PROVIDER_API_KEY',
    base_url = 'https://api.deepseek.com/v1/chat/completions',
    status = 1
WHERE model_name = 'deepseek-v4-flash';

-- 更新 deepseek-v4-pro 的 API Key
UPDATE model_config 
SET api_key = 'REDACTED_PROVIDER_API_KEY',
    base_url = 'https://api.deepseek.com/v1/chat/completions',
    status = 1
WHERE model_name = 'deepseek-v4-pro';

-- 验证
SELECT model_name, LEFT(api_key, 15) as api_key_prefix, status 
FROM model_config 
WHERE provider = 'deepseek';
```

#### 方法3: 清除Redis缓存

```bash
# 清除模型配置缓存
redis-cli DEL model:list:enabled

# 或者重启Redis
redis-cli FLUSHDB
```

---

## 🚀 快速测试指南

### 步骤1: 修复API Key

```bash
# 执行SQL修复
mysql -u root -p ai_gateway < src/main/resources/sql/check_and_fix_api_keys.sql
```

### 步骤2: 重新编译并重启

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo

# 清理编译
mvn clean package -DskipTests

# 重启应用
./scripts/application/stop.sh
./scripts/application/start.sh

# 查看日志
tail -f logs/ai-gateway.log
```

### 步骤3: 运行测试脚本

```bash
# 添加执行权限
chmod +x test_chat_with_points.sh

# 运行测试
./test_chat_with_points.sh
```

---

## 💡 使用流程

### 用户视角

1. **浏览模型商城**
   ```
   GET /api/models
   
   响应示例:
   {
     "models": [
       {
         "modelName": "deepseek-v4-flash",
         "pointsPerRequest": 0,
         "description": "免费快速模型"
       },
       {
         "modelName": "deepseek-v4-pro",
         "pointsPerRequest": 3,
         "description": "高级智能模型"
       }
     ]
   }
   ```

2. **点击模型进入对话**
   - 前端显示模型信息和点数消耗
   - 用户点击后直接进入对话界面
   - 无需选择"免费"或"付费"模式

3. **发送消息（自动扣点）**
   ```
   POST /api/chat/completions
   
   请求:
   {
     "model": "deepseek-v4-pro",
     "messages": [{"role": "user", "content": "你好"}],
     "maxTokens": 100
   }
   
   系统自动:
   1. 检查用户点数是否充足
   2. 预扣点数
   3. 调用AI API
   4. 结算实际消耗
   5. 返回对话结果
   ```

4. **查看点数余额**
   ```
   GET /api/auth/balance
   
   响应:
   {
     "userPoints": 97.000,  // 初始100点，消耗3点
     "balance": 0
   }
   ```

### 管理员视角

1. **配置模型点数消耗**
   ```sql
   UPDATE model_config 
   SET points_per_request = 5.000
   WHERE model_name = 'gpt-4';
   ```

2. **给用户赠送点数**
   ```
   POST /admin/users/grant-points
   
   {
     "userId": 123,
     "points": 100,
     "reason": "新用户奖励"
   }
   ```

3. **查看点数消耗统计**
   ```
   GET /admin/statistics/points?startDate=2026-05-01&endDate=2026-05-15
   ```

---

## 🔍 故障排查

### 问题1: API Key无效

**症状**: 返回401错误 "API Key无效或已过期"

**解决**:
```bash
# 1. 检查数据库中的API Key
mysql -u root -p ai_gateway -e "SELECT model_name, api_key FROM model_config WHERE provider='deepseek';"

# 2. 如果Key不正确，执行修复脚本
mysql -u root -p ai_gateway < src/main/resources/sql/check_and_fix_api_keys.sql

# 3. 清除Redis缓存
redis-cli DEL model:list:enabled

# 4. 重启应用
./scripts/application/restart.sh
```

### 问题2: 点数不足

**症状**: 返回错误 "点数不足"

**解决**:
```bash
# 查看当前点数
curl -X GET http://localhost:8080/api/auth/balance \
  -H "X-User-Token: $TOKEN"

# 充值点数
curl -X POST http://localhost:8080/user/recharge \
  -H "Content-Type: application/json" \
  -H "X-User-Token: $TOKEN" \
  -d '{"amount": 100}'

# 或联系管理员赠送点数
```

### 问题3: 模型不存在

**症状**: 返回404错误 "模型不存在"

**解决**:
```sql
-- 查看所有可用模型
SELECT model_name, status FROM model_config;

-- 确保模型已启用
UPDATE model_config SET status = 1 WHERE model_name = 'deepseek-v4-flash';
```

### 问题4: 流式对话卡住

**症状**: 流式输出无响应

**解决**:
```bash
# 查看应用日志
tail -f logs/ai-gateway.log | grep -i "stream\|error"

# 检查网络连接
curl -I https://api.deepseek.com/v1/models \
  -H "Authorization: Bearer REDACTED_PROVIDER_API_KEY"
```

---

## 📝 相关API接口

### 用户接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/models` | GET | 获取模型列表（含点数消耗） |
| `/api/chat/completions` | POST | 非流式对话（自动扣点） |
| `/api/chat/stream` | POST | 流式对话（自动扣点） |
| `/api/auth/balance` | GET | 查询点数余额 |
| `/user/recharge` | POST | 充值点数 |

### 管理员接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/admin/models` | GET/POST/PUT | 模型管理 |
| `/admin/users/grant-points` | POST | 赠送点数 |
| `/admin/statistics/points` | GET | 点数消耗统计 |

---

## ✨ 优势总结

### 相比免费/付费模式的优势

1. **统一管理**
   - 所有对话通过点数系统管理
   - 便于统计和分析
   - 简化前端逻辑

2. **灵活定价**
   - 不同模型可设置不同点数消耗
   - 可随时调整点数价格
   - 支持促销活动（如半价）

3. **用户体验**
   - 无需选择模式，点击即用
   - 点数消耗透明可见
   - 充值一次，畅用所有模型

4. **商业价值**
   - 点数可销售，产生收入
   - 套餐订阅赠送点数
   - 积分兑换点数

---

## 🎓 最佳实践

### 1. 点数定价策略

- **入门模型**: 0-1点/次（吸引用户）
- **标准模型**: 2-3点/次（主力产品）
- **高级模型**: 5-10点/次（高价值服务）

### 2. 充值套餐设计

- **小额充值**: 10元=100点（适合试用）
- **中额充值**: 50元=600点（优惠20%）
- **大额充值**: 100元=1500点（优惠50%）

### 3. 用户引导

- 新用户注册赠送50点
- 首次充值额外赠送20%
- 连续登录每日赠送5点

---

**最后更新**: 2026-05-15  
**维护人员**: AI Assistant
