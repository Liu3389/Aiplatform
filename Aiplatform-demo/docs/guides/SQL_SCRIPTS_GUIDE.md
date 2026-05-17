# SQL脚本使用说明

## 📋 简化后的脚本清单

现在只需要 **3个核心脚本**：

| 脚本 | 作用 | 使用场景 |
|------|------|---------|
| **rebuild.sql** | 一键重建数据库 | 首次初始化 / 完全重置 |
| **testdata_large.sql** | 插入大量测试数据 | 性能测试 / 功能演示 |
| **clean.sql** | 清空所有数据 | 快速清空（保留表结构） |

---

## 🚀 快速开始

### 场景1：全新安装（首次部署）

```bash
# 一步到位：重建数据库 + 基础数据 + 测试数据
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/rebuild.sql
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/testdata_large.sql
```

或者使用自动化脚本：
```bash
./scripts/database/init_full.sh
```

---

### 场景2：开发中重置数据

#### 方式A：完全重建（推荐）
```bash
# 删除所有表 → 重新建表 → 插入基础数据
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/rebuild.sql
```

#### 方式B：只清空数据（更快）
```bash
# 清空所有数据，保留表结构
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/clean.sql

# 然后重新插入基础数据
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/rebuild.sql
```

---

### 场景3：添加测试数据

```bash
# 在已有基础上插入大量测试数据
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/testdata_large.sql
```

---

## 📊 各脚本详细说明

### 1. rebuild.sql - 一键重建数据库

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

**适用场景**：
- 首次安装项目
- 数据库损坏需要重建
- 开发中需要完全重置

**注意事项**：
- ⚠️ 会删除所有现有数据，不可恢复
- ✅ 幂等操作，可重复执行

---

### 2. testdata_large.sql - 插入大量测试数据

**执行时间**：约5-10秒

**包含内容**：
- ✅ 50个测试用户（cus001 ~ cus050）
- ✅ 110个对话会话
- ✅ 500+条消息记录
- ✅ 各种会员等级和点数余额
- ✅ 不同状态的订阅记录

**适用场景**：
- 性能测试（测试分页、查询速度）
- 功能演示（展示丰富的数据）
- UI测试（测试列表渲染）

**注意事项**：
- ⚠️ 需要先执行rebuild.sql
- ✅ 可以重复执行（会先清空再插入）

---

### 3. clean.sql - 清空所有数据

**执行时间**：约1秒

**包含内容**：
- ✅ 清空所有22个表的数据
- ✅ 保留表结构和索引
- ✅ 重置自增ID

**适用场景**：
- 快速清空测试数据
- 准备重新导入数据
- 清理脏数据

**注意事项**：
- ⚠️ 不会删除表结构
- ⚠️ 操作不可逆，请谨慎使用
- ✅ 比重建数据库快很多

---

## ❌ 已删除的脚本

以下脚本已被删除或合并，**不再使用**：

| 原脚本 | 原因 | 替代方案 |
|--------|------|---------|
| `init_data.sql` | 功能已合并到rebuild.sql | 使用rebuild.sql |
| `init_test_data.sql` | 与testdata_large.sql重复 | 使用testdata_large.sql |
| `migration_v3.1.sql` | 索引已包含在rebuild.sql | 使用rebuild.sql |

---

## 🔍 验证初始化结果

### 检查基础数据

```bash
# 检查模型配置
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "
SELECT display_name, actual_model, status 
FROM platform_model_config;
"

# 检查管理员账号
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "
SELECT username, role, status 
FROM user 
WHERE role IN ('ADMIN', 'SUPER_ADMIN');
"

# 检查数据统计
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "
SELECT 
    (SELECT COUNT(*) FROM user) as user_count,
    (SELECT COUNT(*) FROM conversation) as conversation_count,
    (SELECT COUNT(*) FROM chat_message) as message_count,
    (SELECT COUNT(*) FROM platform_model_config) as model_count;
"
```

### 预期输出

执行rebuild.sql后：
```
+----------+--------------------+-------------+-------------+
| user_count | conversation_count | message_count | model_count |
+----------+--------------------+-------------+-------------+
|        3 |                  0 |           0 |          10 |
+----------+--------------------+-------------+-------------+
```

执行testdata_large.sql后：
```
+----------+--------------------+-------------+-------------+
| user_count | conversation_count | message_count | model_count |
+----------+--------------------+-------------+-------------+
|       53 |                110 |         500+ |          10 |
+----------+--------------------+-------------+-------------+
```

---

## 💡 最佳实践

### 开发环境

```bash
# 每天开始工作时重置数据
./scripts/database/init_full.sh

# 或者手动执行
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/rebuild.sql
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/testdata_large.sql
```

### 测试环境

```bash
# 每次测试前清空数据
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/clean.sql

# 重新插入测试数据
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/testdata_large.sql
```

### 生产环境

```bash
# 仅执行一次rebuild.sql（首次部署）
mysql -h production-db -u admin -p ai_gateway < ./src/main/resources/sql/rebuild.sql

# ⚠️ 不要执行testdata_large.sql
```

---

## 🆘 常见问题

### Q1: 执行rebuild.sql后没有数据？

**原因**：rebuild.sql已经包含了基础数据，不需要额外执行init_data.sql

**解决**：直接验证数据是否存在
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "SELECT COUNT(*) FROM platform_model_config;"
```

### Q2: 如何只重置某个表的数据？

**解决**：单独TRUNCATE该表
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway -e "TRUNCATE TABLE chat_message;"
```

### Q3: testdata_large.sql执行失败？

**原因**：可能外键约束导致

**解决**：先执行clean.sql清空数据
```bash
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/clean.sql
mysql -h localhost -P 3307 -u root -p123456 ai_gateway < ./src/main/resources/sql/testdata_large.sql
```

### Q4: 如何备份当前数据？

**解决**：使用mysqldump
```bash
mysqldump -h localhost -P 3307 -u root -p123456 ai_gateway > backup_$(date +%Y%m%d).sql
```

---

## 📝 总结

**只需记住3个命令**：

1. **完全重建**：`rebuild.sql`
2. **添加测试数据**：`testdata_large.sql`
3. **快速清空**：`clean.sql`

**推荐使用自动化脚本**：
```bash
./scripts/database/init_full.sh  # 一键完成所有初始化
```
