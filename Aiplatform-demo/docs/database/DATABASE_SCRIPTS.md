# 数据库管理脚本说明

## 📦 SQL文件清单（3个核心文件）

| 文件 | 用途 | 适用场景 |
| :--- | :--- | :--- |
| **[rebuild.sql](../../src/main/resources/sql/rebuild.sql)** ⭐ | **一键重建**（删表+建表+基础数据） | 首次安装 / 完全重置 |
| **[testdata_large.sql](../../src/main/resources/sql/testdata_large.sql)** | **测试数据**（50用户、110对话） | 性能测试 / 功能演示 |
| **[clean.sql](../../src/main/resources/sql/clean.sql)** | **清空数据**（保留表结构） | 快速清空 / 重新初始化 |

> **注意**: 所有脚本均使用 `SET FOREIGN_KEY_CHECKS = 0` 确保幂等执行，可随时安全重置。

---

## 🚀 快速使用

### 场景1：首次安装 / 完全重建
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/rebuild.sql
```

### 场景2：添加测试数据
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/testdata_large.sql
```

### 场景3：快速清空数据
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < src/main/resources/sql/clean.sql
```

---

## 📋 rebuild.sql 详细说明

**执行时间**：约2-5秒

**包含内容**：
- ✅ 删除所有旧表（22个表）
- ✅ 创建所有新表（完整表结构+索引）
- ✅ 插入基础数据：
  - 10个平台模型配置（DeepSeek、GPT-4o、Claude等）
  - 3个厂商配置（OpenAI、Anthropic、DeepSeek）
  - 4个套餐模板（免费版、基础版、专业版、旗舰版）
  - 1个超级管理员账号（superadmin / Test@123456）
  - 2个测试用户（testuser01, testuser02）
  - 计数器初始值

---

## 📊 testdata_large.sql 详细说明

**执行时间**：约5-10秒

**包含内容**：
- ✅ 50个测试用户（cus001 ~ cus050）
- ✅ 110个对话会话
- ✅ 500+条消息记录
- ✅ 各种会员等级和点数余额
- ✅ 不同状态的订阅记录

---

## 🔍 验证初始化结果

```bash
# 检查数据统计
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "
SELECT 
    (SELECT COUNT(*) FROM user) as user_count,
    (SELECT COUNT(*) FROM conversation) as conversation_count,
    (SELECT COUNT(*) FROM chat_message) as message_count,
    (SELECT COUNT(*) FROM platform_model_config) as model_count;
"
```

**预期输出**：
- 执行rebuild.sql后：用户数=3，模型数=10
- 执行testdata_large.sql后：用户数=53，对话数=110，消息数=500+

---

## ⚙️ Shell脚本（3个）

项目提供了3个自动化脚本简化操作：

| 脚本 | 作用 |
|------|------|
| `scripts/database/init_full.sh` | 完整初始化（rebuild + 测试数据） |
| `scripts/database/init_local_db.sh` | 本地初始化（仅rebuild） |
| `scripts/database/reset_db.sh` | 快速重置（clean + rebuild） |

**推荐使用**：
```bash
chmod +x scripts/database/*.sh
./scripts/database/init_full.sh
```

---

## ⚠️ 注意事项

1. **备份重要数据**: 在执行任何数据库操作前，请确保已备份重要数据
2. **权限要求**: 确保MySQL用户具有足够的权限执行DDL和DML操作
3. **端口配置**: 本地数据库默认使用3307端口，根据实际情况调整
4. **密码安全**: 生产环境请修改默认密码
5. **不可逆操作**: clean.sql和rebuild.sql会删除数据，请谨慎使用

---

## 🆘 常见问题

### Q1: 执行rebuild.sql后没有数据？
**A**: rebuild.sql已经包含了基础数据，不需要额外执行其他脚本。

### Q2: 如何只重置某个表的数据？
**A**: 单独TRUNCATE该表
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "TRUNCATE TABLE chat_message;"
```

### Q3: testdata_large.sql执行失败？
**A**: 先执行clean.sql清空数据，再执行testdata_large.sql。
