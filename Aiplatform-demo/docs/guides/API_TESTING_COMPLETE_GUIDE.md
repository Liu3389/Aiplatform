# API 全量测试完整指南

## 📋 目录

1. [测试准备](#测试准备)
2. [自动化测试](#自动化测试)
3. [手动测试](#手动测试)
4. [Postman测试](#postman测试)
5. [测试覆盖清单](#测试覆盖清单)
6. [常见问题](#常见问题)

---

## 测试准备

### 1. 环境要求

确保以下服务正常运行：

```bash
# 检查MySQL
mysql -h localhost -P 3307 -u root -p123456 -e "SELECT 1"

# 检查Redis
redis-cli ping

# 检查应用是否启动
curl -s http://localhost:8080/api/model-config/list | jq .code
```

### 2. 初始化测试数据

```bash
# 如果数据库为空，先初始化
./scripts/database/init_test_data.sh

# 或者只清除测试数据后重新生成
./scripts/database/clear_test_data.sh
./scripts/database/init_test_data.sh
```

### 3. 获取管理员Token

```bash
# 使用admin01账号登录
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin01","password":"Test@123456"}' \
  -H "X-Device-Id: test-device" | jq -r '.data.token')

echo "Admin Token: $ADMIN_TOKEN"
```

---

## 自动化测试

### 方法一：全量API测试（推荐）⭐

**脚本**: `test_all_apis.sh`

**功能**: 
- 测试80个API接口
- 覆盖用户认证、API Key管理、模型配置、聊天、管理员等所有功能
- 自动提取Token用于后续测试
- 生成详细的测试报告

**执行步骤**:

```bash
# 1. 添加执行权限
chmod +x test_all_apis.sh

# 2. 运行测试
./test_all_apis.sh

# 3. 查看结果
# 测试完成后会显示：
# - 通过的测试数
# - 失败的测试数
# - 跳过的测试数
# - 每个接口的详细状态
```

**预期输出**:
```
====================================================================
  Lingdian AI Gateway - 全量 API 测试
  Base URL: http://localhost:8080/api
  Start Time: 2026-05-15 11:00:00
====================================================================

━━━ 1. 用户认证 ━━━
  ✅ PASS [200] 注册新用户(备选)
       └─ USER_ID = 123
  ✅ PASS [200] 用户登录(testuser)
       └─ TOKEN = abc123...

━━━ 2. API Key管理 ━━━
  ✅ PASS [200] 生成API Key
       └─ API_KEY = sk-test...

... (更多测试)

====================================================================
  测试结果汇总
====================================================================
  总测试数: 80
  ✅ 通过: 78
  ❌ 失败: 2
  ⏭️  跳过: 0
  
  失败详情:
  ❌ 某个接口 (HTTP 500, expected 200)
```

---

### 方法二：管理员权限专项测试

**脚本**: `test_admin_permission.sh`

**功能**:
- 专门测试@RequireAdmin权限控制
- 验证普通用户无法访问管理员接口
- 验证管理员可以正常访问

**执行步骤**:

```bash
# 1. 添加执行权限
chmod +x test_admin_permission.sh

# 2. 运行测试
./test_admin_permission.sh
```

**预期输出**:
```
━━━ 第四步：测试权限拦截（普通用户访问管理员接口） ━━━
  预期结果：所有请求应返回 code: 403 (Forbidden)

  ✅ PASS [code:403] 获取系统统计数据
       └─ Message: 权限不足，需要管理员角色
  ✅ PASS [code:403] 获取完整分析数据
       └─ Message: 权限不足，需要管理员角色
  ...

🎉 所有测试通过！@RequireAdmin 权限检查正常工作
```

---

## 手动测试

### 1. 用户认证模块

#### 注册用户
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_user_manual",
    "password": "Test@123456",
    "email": "test@example.com"
  }' | jq .
```

**预期响应**:
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 123,
    "username": "test_user_manual",
    "role": "USER",
    ...
  }
}
```

#### 用户登录
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_user_manual","password":"Test@123456"}' \
  -H "X-Device-Id: manual-test" | jq -r '.data.token')

echo "Token: $TOKEN"
```

#### 查询用户信息
```bash
curl -s http://localhost:8080/api/auth/userinfo \
  -H "X-User-Token: $TOKEN" | jq .
```

---

### 2. API Key管理模块

#### 生成API Key
```bash
API_KEY=$(curl -s -X POST http://localhost:8080/api/api-key/generate \
  -H "X-User-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Manual Test Key","rateLimit":100}' | jq -r '.data.apiKey')

echo "API Key: $API_KEY"
```

#### 查询API Key列表
```bash
curl -s http://localhost:8080/api/api-key/list \
  -H "X-User-Token: $TOKEN" | jq .
```

#### 禁用API Key
```bash
# 假设API Key ID为1
curl -s -X PUT http://localhost:8080/api/api-key/1/disable \
  -H "X-User-Token: $TOKEN" | jq .
```

---

### 3. 模型配置模块

#### 查询模型列表
```bash
curl -s http://localhost:8080/api/model-config/list | jq .
```

**预期响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "modelCode": "gpt-4o",
      "modelName": "GPT-4 Optimized",
      "pricingMode": "STANDARD",
      ...
    },
    ...
  ]
}
```

---

### 4. 聊天模块

#### 发送聊天消息
```bash
curl -s -X POST http://localhost:8080/api/chat/send \
  -H "X-User-Token: $TOKEN" \
  -H "X-Device-Id: manual-test" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "stream": false
  }' | jq .
```

---

### 5. 管理员模块

#### 获取系统统计
```bash
curl -s http://localhost:8080/api/admin/stats \
  -H "X-User-Token: $ADMIN_TOKEN" | jq .
```

#### 分页查询用户
```bash
curl -s "http://localhost:8080/api/admin/users?page=1&size=20" \
  -H "X-User-Token: $ADMIN_TOKEN" | jq .
```

#### 仪表盘统计
```bash
curl -s "http://localhost:8080/api/admin/statistics/dashboard?period=7d" \
  -H "X-User-Token: $ADMIN_TOKEN" | jq .
```

---

## Postman测试

### 1. 导入集合

**用户API集合**:
- 文件: `API_TEST.postman_collection.json`
- 包含: 40+ 个普通用户接口

**管理员API集合**:
- 文件: `ADMIN_API.postman_collection.json`
- 包含: 38 个管理员接口

### 2. 设置环境变量

在Postman中创建环境变量：

```
baseUrl: http://localhost:8080/api
adminToken: <从登录接口获取>
userToken: <从登录接口获取>
apiKey: <从生成API Key接口获取>
```

### 3. 执行测试

#### 用户API测试流程：

1. **认证** → 注册/登录 → 获取Token
2. **API Key** → 生成/查询/启用/禁用
3. **模型** → 查询模型列表
4. **聊天** → 发送消息/查询历史
5. **用户信息** → 查询余额/修改密码

#### 管理员API测试流程：

1. **登录** → 使用admin01账号 → 获取Admin Token
2. **系统统计** → 获取统计数据
3. **用户管理** → 查询/封禁/充值
4. **统计分析** → 仪表盘/用户统计/对话统计
5. **批量操作** → 批量封禁/发放点数

### 4. 运行Collection Runner

1. 点击左上角 "Runner"
2. 选择对应的Collection
3. 设置迭代次数（建议1次）
4. 点击 "Run"
5. 查看测试结果

---

## 测试覆盖清单

### ✅ 核心功能测试

| 模块 | 接口数 | 测试状态 | 备注 |
|------|--------|---------|------|
| 用户认证 | 6 | ⬜ 待测试 | 注册、登录、刷新Token等 |
| API Key管理 | 6 | ⬜ 待测试 | 生成、查询、启用/禁用 |
| 模型配置 | 1 | ⬜ 待测试 | 查询模型列表 |
| 用户基础信息 | 6 | ⬜ 待测试 | 信息查询、余额、密码 |
| 聊天功能 | 8 | ⬜ 待测试 | 发送、历史、会话管理 |
| 优惠券 | 3 | ⬜ 待测试 | 领取、查询、使用 |
| 通知 | 6 | ⬜ 待测试 | 查询、已读、删除 |
| **小计** | **36** | | 普通用户接口 |

### ✅ 管理员功能测试

| 模块 | 接口数 | 测试状态 | 备注 |
|------|--------|---------|------|
| 系统统计 | 5 | ⬜ 待测试 | 基础统计、完整分析 |
| 核心统计 | 3 | ⬜ 待测试 | 仪表盘、用户、对话 |
| 用户管理 | 8 | ⬜ 待测试 | 查询、详情、状态、充值 |
| 批量操作 | 3 | ⬜ 待测试 | 批量封禁、发放点数 |
| 点数管理 | 2 | ⬜ 待测试 | 增减点数、免费次数 |
| 会员管理 | 3 | ⬜ 待测试 | 等级调整、有效期 |
| 统计分析 | 4 | ⬜ 待测试 | 日/周/月查询、导出 |
| 通知管理 | 6 | ⬜ 待测试 | 未读数、列表、已读 |
| 操作日志 | 1 | ⬜ 待测试 | 查询日志 |
| 优惠券 | 1 | ⬜ 待测试 | 发放优惠券 |
| 通知发送 | 1 | ⬜ 待测试 | 发送系统通知 |
| **小计** | **37** | | 管理员接口 |

### ✅ 权限控制测试

| 测试项 | 预期结果 | 测试状态 |
|--------|---------|---------|
| 普通用户访问/admin/stats | 403 Forbidden | ⬜ 待测试 |
| 普通用户访问/admin/users | 403 Forbidden | ⬜ 待测试 |
| 管理员访问/admin/stats | 200 OK | ⬜ 待测试 |
| 缺少Token访问受保护接口 | 401 Unauthorized | ⬜ 待测试 |
| Token过期访问接口 | 401 Unauthorized | ⬜ 待测试 |

---

## 常见问题

### Q1: 测试失败显示"Connection refused"

**原因**: 应用未启动或端口错误

**解决**:
```bash
# 检查应用是否运行
ps aux | grep AiGatewayApplication

# 检查端口占用
lsof -i :8080

# 重启应用
./scripts/application/stop.sh
./scripts/application/start.sh
```

---

### Q2: 登录失败显示"用户名或密码错误"

**原因**: 测试数据未初始化或密码错误

**解决**:
```bash
# 重新初始化测试数据
./scripts/database/init_test_data.sh

# 确认默认密码是 Test@123456
```

---

### Q3: API返回500错误

**原因**: 可能是数据库连接问题或代码bug

**解决**:
```bash
# 查看应用日志
tail -f logs/ai-gateway.log

# 检查数据库连接
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "SHOW TABLES"

# 检查Redis连接
redis-cli ping
```

---

### Q4: Postman测试时Token无效

**原因**: Token未正确设置或已过期

**解决**:
1. 重新登录获取新Token
2. 更新Postman环境变量中的token值
3. 确认Header中使用了正确的Key（X-User-Token）

---

### Q5: 某些接口测试超时

**原因**: 网络问题或接口响应慢

**解决**:
```bash
# 增加curl超时时间
curl --connect-timeout 30 --max-time 60 ...

# 检查服务器负载
top
htop
```

---

## 测试最佳实践

### 1. 测试顺序

```
1. 环境检查 → 2. 数据初始化 → 3. 自动化测试 → 4. 手动验证 → 5. 问题修复
```

### 2. 测试频率

- **开发阶段**: 每次代码修改后运行相关测试
- **提交前**: 运行全量测试 `test_all_apis.sh`
- **每周**: 运行权限测试 `test_admin_permission.sh`
- **发布前**: 完整的手动测试 + Postman Collection测试

### 3. 测试数据管理

```bash
# 测试前重置数据
./scripts/database/clear_test_data.sh
./scripts/database/init_test_data.sh

# 测试后清理（可选）
./scripts/database/clear_test_data.sh
```

### 4. 测试结果记录

创建测试报告模板：

```markdown
# 测试报告 - 2026-05-15

## 测试环境
- 应用版本: v1.0.0
- MySQL: 8.0
- Redis: 7.0
- 测试时间: 2026-05-15 11:00-12:00

## 测试结果
- 总测试数: 80
- 通过: 78
- 失败: 2
- 通过率: 97.5%

## 失败详情
1. 接口名称: XXX
   - 期望: 200
   - 实际: 500
   - 原因: XXX
   - 解决方案: XXX

## 结论
✅ 测试通过，可以发布
```

---

## 快速开始（5分钟测试）

如果想快速验证系统是否正常，执行以下步骤：

```bash
# 1. 检查服务
curl -s http://localhost:8080/api/model-config/list | jq '.code'
# 应该返回 200

# 2. 注册并登录
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"quick_test","password":"Test@123456","email":"quick@test.com"}' | jq -r '.data.id' > /dev/null && \
  curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"quick_test","password":"Test@123456"}' | jq -r '.data.token')

# 3. 查询用户信息
curl -s http://localhost:8080/api/auth/userinfo \
  -H "X-User-Token: $TOKEN" | jq '.code'
# 应该返回 200

# 4. 运行全量测试
./test_all_apis.sh

# 5. 运行权限测试
./test_admin_permission.sh
```

---

## 📚 相关文档

- [PERMISSION_FIX_RECORD.md](guides/PERMISSION_FIX_RECORD.md) - 权限控制修复记录
- [ADMIN_PERMISSION_TEST_QUICKSTART.md](guides/ADMIN_PERMISSION_TEST_QUICKSTART.md) - 权限测试快速开始
- [STATISTICS_SYSTEM_GUIDE.md](database/STATISTICS_SYSTEM_GUIDE.md) - 统计系统说明
- [DATABASE_SCRIPTS.md](database/DATABASE_SCRIPTS.md) - 数据库脚本说明

---

**祝你测试顺利！** 🎉
