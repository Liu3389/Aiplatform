# 测试数据一致性说明

## 📋 统一测试账户

### 管理员账户
- **用户名**: `superadmin`
- **密码**: `Test@123456`
- **角色**: `SUPER_ADMIN`
- **点数**: 10000

### 完整测试账户列表

| 用户名 | 密码 | 会员等级 | 余额 | 点数 | 用途 |
|--------|------|---------|------|------|------|
| superadmin | Test@123456 | SUPER_ADMIN | 0 | 10000 | 管理员功能测试 |
| testuser | Test@123456 | USER | 1000 | 1000 | 基础接口测试 |
| silver_user | Test@123456 | silver (月卡) | 500 | 1200 | 白银会员功能测试 |
| gold_user | Test@123456 | gold (季卡) | 1000 | 3000 | 黄金会员功能测试 |
| diamond_user | Test@123456 | diamond (年卡) | 5000 | 10000 | 钻石会员功能测试 |
| free_user | Test@123456 | 无会员 | 100 | 300 | 免费用户测试 |
| disabled_user | Test@123456 | silver (已禁用) | 0 | 0 | 禁用状态测试 |

---

## 🗄️ 数据库脚本

### rebuild.sql（完整重建脚本）⭐
**位置**: `/src/main/resources/sql/rebuild.sql`

**用途**: 
- 开发环境初始化
- 测试环境重置
- 包含完整的表结构、基础数据和测试数据

**包含内容**:
- ✅ 所有表结构定义（22张表）
- ✅ 平台模型配置（10个模型）
- ✅ 套餐模板（4个套餐）
- ✅ 7个测试用户（不同会员等级）
- ✅ API Key（每个用户一个）
- ✅ 15个会话记录
- ✅ 示例聊天消息（Python、微服务、电商系统等话题）
- ✅ 6条充值记录
- ✅ 多条点数账单记录（消费和赠送）
- ✅ 3条用户订阅记录
- ✅ 2张优惠券
- ✅ 3条系统通知
- ✅ 3天统计数据

**执行方式**: 
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/rebuild.sql
```

### testdata_large.sql（大量测试数据）
**位置**: `/src/main/resources/sql/testdata_large.sql`

**用途**:
- 性能测试
- 压力测试
- 分页功能测试
- 包含50个用户、110个会话、数百条消息

**注意**: 此文件**不用于日常接口测试**，仅用于性能验证。

---

## 📡 接口测试

### Postman Collection
**位置**: `/API_TEST.postman_collection.json`

**使用的账户**:
- 登录接口: `testuser / Test@123456`
- 所有需要认证的接口都使用 `testuser` 的 Token

**一致性**: ✅ 已与 `rebuild.sql` 同步

---

## 🧪 测试流程

### 标准测试流程（推荐）

1. **重建数据库**（首次或需要重置时）:
   ```bash
   mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/rebuild.sql
   ```

2. **启动应用**:
   ```bash
   ./scripts/application/start.sh
   ```

3. **运行接口测试**:
   - 导入 `API_TEST.postman_collection.json` 到 Postman
   - 执行 "用户登录" 接口获取 Token
   - 依次测试其他接口
   - 可以使用不同会员等级的账户测试各种场景

### 性能测试流程（可选）

1. **先执行 rebuild.sql** 初始化基础数据
2. **再执行 testdata_large.sql** 加载大量数据:
   ```bash
   mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/testdata_large.sql
   ```
3. **进行性能测试**（分页、查询等）

---

## ⚠️ 注意事项

1. **脚本执行顺序**:
   - ✅ rebuild.sql（一次性执行，包含所有测试数据）
   - ❌ rebuild.sql → testdata_large.sql（会导致用户ID冲突）
   - ✅ 如需大量数据，应先清空再执行 testdata_large.sql

2. **密码哈希**:
   - 所有测试用户的密码都是 `Test@123456`
   - BCrypt Hash: `$2a$10$h2b3MoYCcBJEwgmGMH8Ome5fKxkUtlBTXNJbMpuv6M4/KabSdVtKK`

3. **Token 有效期**:
   - Token 默认有效期 24 小时
   - 测试前建议重新登录获取新 Token

4. **点数消耗**:
   - `testuser` 有 1000 点数
   - `silver_user` 每日赠送 80 点数
   - `gold_user` 每日赠送 150 点数
   - `diamond_user` 每日赠送 300 点数
   - `free_user` 每日赠送 30 点数
   - 每次对话消耗 1-5 点数（取决于模型）
   - 点数不足时接口会返回错误

5. **会员过期时间**:
   - `silver_user`: 30天后过期
   - `gold_user`: 90天后过期
   - `diamond_user`: 365天后过期
   - 测试时注意检查会员状态

---

## 📊 数据一致性检查清单

| 项目 | rebuild.sql | Postman | 状态 |
|------|-------------|---------|------|
| 测试用户名 | testuser + 6个其他用户 | testuser | ✅ 一致 |
| 密码 | Test@123456 | Test@123456 | ✅ 一致 |
| 邮箱 | 多个邮箱 | - | ✅ OK |
| 初始点数 | 不同用户不同点数 | - | ✅ OK |
| 角色 | 多种角色 | - | ✅ OK |
| 对话记录 | ✅ 有（15个会话） | - | ✅ OK |
| 充值记录 | ✅ 有（6条） | - | ✅ OK |
| 点数账单 | ✅ 有（多条） | - | ✅ OK |

---

**最后更新**: 2026-05-17  
**维护者**: 后端开发团队
