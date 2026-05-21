# 测试数据快速使用指南

## 🚀 一键初始化（推荐）

执行以下命令即可完成数据库重建和测试数据导入：

```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/rebuild.sql
```

执行完成后，您将看到完整的测试账户列表和数据说明。

---

## 📝 可用测试账户

所有账户密码均为：**Test@123456**

| 用户名           | 会员等级         | 余额   | 点数    | 适用场景    |
|---------------|--------------|------|-------|---------|
| superadmin    | SUPER_ADMIN  | 0    | 10000 | 管理员功能测试 |
| testuser      | USER         | 1000 | 1000  | 基础接口测试  |
| silver_user   | silver (月卡)  | 500  | 1200  | 白银会员功能  |
| gold_user     | gold (季卡)    | 1000 | 3000  | 黄金会员功能  |
| diamond_user  | diamond (年卡) | 5000 | 10000 | 钻石会员功能  |
| free_user     | 无会员          | 100  | 300   | 免费用户测试  |
| disabled_user | silver (已禁用) | 0    | 0     | 禁用状态测试  |

---

## 🎯 测试场景示例

### 场景1：基础接口测试

```bash
# 1. 重建数据库
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/rebuild.sql

# 2. 启动应用
./scripts/application/start.sh

# 3. 使用 testuser 登录并测试
```

### 场景2：会员功能测试

- 使用 `silver_user` 测试白银会员权益
- 使用 `gold_user` 测试黄金会员权益
- 使用 `diamond_user` 测试钻石会员权益
- 验证不同会员等级的模型访问权限

### 场景3：对话记录查询

数据库中已包含15个会话和示例消息：

- silver_user: Python学习助手、数据分析讨论
- gold_user: Java项目架构设计、微服务最佳实践、数据库优化方案
- diamond_user: 企业级系统设计、AI模型选型分析、性能调优指南
- free_user: Python基础问题、JavaScript入门
- testuser: 测试会话1、测试会话2

### 场景4：充值和消费记录

- 6条充值记录（支付宝、微信支付）
- 多条点数账单记录（对话消耗、每日赠送）
- 可测试充值查询、消费统计等功能

### 场景5：优惠券和通知

- 2张优惠券：新用户欢迎券、新年特惠券
- 3条系统通知：维护通知、新功能上线、优惠活动

---

## ⚠️ 注意事项

1. **一次性执行**: rebuild.sql 已包含所有测试数据，只需执行一次
2. **不要混用**: 不要在 rebuild.sql 后执行 testdata_large.sql（会导致ID冲突）
3. **Token有效期**: Token默认24小时有效，测试前建议重新登录
4. **会员过期**: 注意各会员的过期时间（30天/90天/365天）

---

## 📊 数据统计

rebuild.sql 包含的测试数据：

- ✅ 7个测试用户
- ✅ 5个API Key
- ✅ 15个会话
- ✅ 示例聊天消息（Python、微服务、电商系统）
- ✅ 6条充值记录
- ✅ 多条点数账单
- ✅ 3条用户订阅
- ✅ 2张优惠券
- ✅ 3条系统通知
- ✅ 3天统计数据

---

**最后更新**: 2026-05-17
