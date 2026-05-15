# 管理员权限测试 - 快速开始指南

## 🚀 5分钟快速验证权限控制

### 前提条件

1. ✅ 应用已启动（`./scripts/application/start.sh`）
2. ✅ MySQL和Redis正常运行
3. ✅ 已初始化测试数据（可选，用于测试管理员账号）

---

## 方法一：自动化测试（推荐）⭐

### 步骤1: 运行测试脚本

```bash
cd $HOME/Documents/Aiplatform/Aiplatform-demo
chmod +x test_admin_permission.sh
./test_admin_permission.sh
```

### 步骤2: 查看结果

**如果所有测试通过**，你会看到：
```
🎉 所有测试通过！@RequireAdmin 权限检查正常工作

结论：
- 普通用户无法访问管理员接口（返回403）
- AdminAuthAspect 切面正确拦截了 /admin/** 路径
- 权限控制系统工作正常
```

**如果有失败的测试**，你会看到详细的错误信息，包括：
- HTTP状态码
- 错误消息
- 响应体内容

---

## 方法二：手动测试

### 步骤1: 注册一个普通用户

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_normal_user",
    "password": "Test@123456",
    "email": "test@example.com"
  }'
```

预期输出：
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 123,
    "username": "test_normal_user",
    "role": "USER",
    ...
  }
}
```

### 步骤2: 登录获取Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_normal_user","password":"Test@123456"}' \
  -H "X-Device-Id: test-device" | jq -r '.data.token')

echo "Token: $TOKEN"
```

### 步骤3: 验证用户角色

```bash
curl -s http://localhost:8080/api/auth/userinfo \
  -H "X-User-Token: $TOKEN" | jq '.data.role'
```

预期输出：
```
"USER"
```

### 步骤4: 尝试访问管理员接口（应该被拒绝）

```bash
# 测试1: 获取系统统计
curl -s http://localhost:8080/api/admin/stats \
  -H "X-User-Token: $TOKEN" | jq .
```

预期输出：
```json
{
  "code": 403,
  "message": "权限不足，需要管理员角色"
}
```

```bash
# 测试2: 仪表盘统计
curl -s http://localhost:8080/api/admin/statistics/dashboard?period=7d \
  -H "X-User-Token: $TOKEN" | jq .
```

预期输出：
```json
{
  "code": 403,
  "message": "权限不足，需要管理员角色"
}
```

### 步骤5: 验证管理员可以正常访问

```bash
# 使用admin01账号登录（需要先初始化测试数据）
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin01","password":"Test@123456"}' \
  -H "X-Device-Id: admin-device" | jq -r '.data.token')

# 访问管理员接口（应该成功）
curl -s http://localhost:8080/api/admin/stats \
  -H "X-User-Token: $ADMIN_TOKEN" | jq .
```

预期输出：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalUsers": 100,
    "todayNewUsers": 5,
    ...
  }
}
```

---

## 🔍 问题排查

### 场景1: 普通用户访问返回200而不是403

**这说明权限控制失效了！**

#### 立即检查：

1. **确认用户角色**
   ```bash
   curl -s http://localhost:8080/api/auth/userinfo \
     -H "X-User-Token: $TOKEN" | jq '.data.role'
   ```
   如果显示 `"ADMIN"` 或 `"SUPER_ADMIN"`，说明这个用户不是普通用户。

2. **检查日志**
   ```bash
   tail -f logs/ai-gateway.log | grep -E "AdminAuthAspect|管理员权限"
   ```
   如果没有看到任何日志，说明AOP切面未生效。

3. **重启应用**
   ```bash
   ./scripts/application/stop.sh
   ./scripts/application/start.sh
   ```

### 场景2: 返回401而不是403

**401 = 未授权（Token无效）**  
**403 = 禁止访问（权限不足）**

如果返回401，说明：
- Token格式错误
- Token已过期
- Token不存在

**解决方法**：重新登录获取新Token。

### 场景3: 管理员也返回403

**这说明admin01账号的role字段可能不是ADMIN。**

#### 检查数据库：

```sql
SELECT id, username, role FROM user WHERE username = 'admin01';
```

如果role不是`ADMIN`，需要修复：

```sql
UPDATE user SET role = 'ADMIN' WHERE username = 'admin01';
```

或者重新初始化测试数据：

```bash
./scripts/database/init_test_data.sh
```

---

## 📊 测试覆盖的接口

测试脚本会验证以下8个管理员接口：

| # | 接口 | 方法 | 预期状态码 |
|---|------|------|-----------|
| 1 | `/admin/stats` | GET | 403 |
| 2 | `/admin/analytics/full` | GET | 403 |
| 3 | `/admin/statistics/dashboard` | GET | 403 |
| 4 | `/admin/statistics/users` | GET | 403 |
| 5 | `/admin/statistics/conversations` | GET | 403 |
| 6 | `/admin/users` | GET | 403 |
| 7 | `/admin/user/status` | POST | 403 |
| 8 | `/admin/user/recharge` | POST | 403 |

---

## 🎯 验证清单

完成测试后，确认以下所有项都✅通过：

- [ ] 新用户注册时role字段为"USER"
- [ ] 普通用户登录后获取的Token有效
- [ ] 普通用户访问`/admin/stats`返回403
- [ ] 普通用户访问`/admin/statistics/dashboard`返回403
- [ ] 普通用户访问`/admin/users`返回403
- [ ] 管理员账号（admin01）可以正常访问所有管理员接口
- [ ] 应用日志中看到"管理员权限验证通过"或"权限不足"的消息

---

## 📚 相关文档

- [ADMIN_PERMISSION_TROUBLESHOOTING.md](ADMIN_PERMISSION_TROUBLESHOOTING.md) - 详细的问题诊断与修复指南
- [STATISTICS_SYSTEM_GUIDE.md](../database/STATISTICS_SYSTEM_GUIDE.md) - 统计系统使用说明
- [AdminAuthAspect.java](../../src/main/java/com/ai/gateway/aspect/AdminAuthAspect.java) - 权限切面源代码

---

## 💡 提示

1. **每次测试使用不同的用户名**，避免冲突：
   ```bash
   USERNAME="testuser_$(date +%s)"
   ```

2. **测试完成后清理数据**（可选）：
   ```sql
   DELETE FROM user WHERE username LIKE 'test_normal_user%';
   ```

3. **定期运行测试**，确保权限控制一直有效：
   ```bash
   # 每周执行一次
   0 9 * * 1 /path/to/test_admin_permission.sh >> /var/log/permission_test.log 2>&1
   ```

---

## ❓ 常见问题

**Q: 为什么测试脚本要注册新用户，而不是直接用现有用户？**

A: 为了确保测试的是真正的普通用户，避免使用可能被误设为管理员的账号。

**Q: 我可以跳过某些测试吗？**

A: 不建议。完整的测试才能确保所有管理员接口都被正确保护。

**Q: 测试失败怎么办？**

A: 
1. 查看详细的错误信息
2. 检查应用日志
3. 参考 [ADMIN_PERMISSION_TROUBLESHOOTING.md](ADMIN_PERMISSION_TROUBLESHOOTING.md)
4. 如果问题持续，联系开发团队

---

**祝你测试顺利！** 🎉
