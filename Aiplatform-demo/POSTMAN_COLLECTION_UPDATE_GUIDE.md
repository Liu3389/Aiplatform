# Postman Collection 更新指南

**更新日期**: 2026-05-15  
**说明**: 严格更新两个JSON接口文档，确保与实际接口完全一致

---

## 📋 需要更新的接口

### API_TEST.postman_collection.json（用户接口）

#### 1. 移除mode参数

**所有对话接口**中删除`mode`字段：

**修改前**:
```json
{
  "model": "deepseek-v4-flash",
  "messages": [...],
  "mode": "free",  // ❌ 删除此行
  "maxTokens": 100
}
```

**修改后**:
```json
{
  "model": "deepseek-v4-flash",
  "messages": [...],
  "maxTokens": 100  // ✅ 无mode参数
}
```

**影响的接口**:
- POST /api/chat/completions (非流式对话)
- POST /api/chat/stream (流式对话)

#### 2. 添加点数说明

在模型列表接口描述中添加：

```json
"description": "获取可用模型列表\n\n每个模型显示每次对话消耗的点数：\n- deepseek-v4-flash: 1点/次\n- deepseek-v4-pro: 3点/次"
```

#### 3. 更新充值接口示例

确保充值接口返回包含点数信息：

```json
{
  "code": 200,
  "message": "充值成功",
  "data": {
    "amount": 100,
    "userPoints": 130.000  // 新增：显示当前点数
  }
}
```

#### 4. 添加每日点数说明

在用户信息接口中添加：

```json
"description": "用户每天凌晨0点自动获得30点（当日有效）"
```

---

### ADMIN_API.postman_collection.json（管理员接口）

#### 1. 添加手动发放每日点数接口

**新增接口**:

```json
{
  "name": "手动发放每日点数",
  "request": {
    "method": "POST",
    "header": [
      {
        "key": "X-User-Token",
        "value": "{{admin_token}}"
      }
    ],
    "body": {
      "mode": "raw",
      "raw": "{\n  \"userId\": 52  // 可选，不填则给所有用户发放\n}",
      "options": {
        "raw": {
          "language": "json"
        }
      }
    },
    "url": {
      "raw": "{{base_url}}/admin/points/distribute-daily",
      "host": ["{{base_url}}"],
      "path": ["admin", "points", "distribute-daily"]
    }
  },
  "response": []
}
```

**位置**: 添加到"点数管理"分组中

#### 2. 更新批量操作接口说明

在以下接口中添加类型兼容性说明：

- POST /admin/users/batch/update-status
- POST /admin/users/batch/grant-points
- POST /admin/users/batch/activate-membership

**说明文本**:
```
注意：userIds数组中的元素可以是Integer或Long类型，系统会自动转换
```

#### 3. 添加套餐模板管理接口

确保以下6个接口已添加：

1. GET /admin/packages/list - 获取套餐列表
2. GET /admin/packages/{id} - 获取套餐详情
3. POST /admin/packages/create - 创建套餐模板
4. PUT /admin/packages/update - 更新套餐模板
5. POST /admin/packages/toggle-status - 上架/下架套餐
6. DELETE /admin/packages/{id} - 删除套餐模板

---

## 🔧 更新步骤

### 方法1: 手动更新（推荐）

1. **打开Postman应用**
2. **导入现有Collection**
3. **按照上述说明逐个修改接口**
4. **导出更新后的JSON文件**

### 方法2: 使用脚本更新

```bash
# 备份原文件
cp API_TEST.postman_collection.json API_TEST.postman_collection.json.bak
cp ADMIN_API.postman_collection.json ADMIN_API.postman_collection.json.bak

# 使用Python脚本更新（需要编写）
python3 update_postman_collections.py
```

---

## ✅ 验证清单

### API_TEST.postman_collection.json

- [ ] 所有对话接口已移除mode参数
- [ ] 模型列表接口添加了点数说明
- [ ] 充值接口示例包含点数信息
- [ ] 用户信息接口添加了每日点数说明
- [ ] 接口总数正确（约40+个）
- [ ] 可以成功导入Postman

### ADMIN_API.postman_collection.json

- [ ] 添加了手动发放每日点数接口
- [ ] 批量操作接口添加了类型兼容说明
- [ ] 套餐模板管理6个接口齐全
- [ ] 统计接口描述准确
- [ ] 接口总数正确（约38个）
- [ ] 可以成功导入Postman

---

## 📝 注意事项

1. **保持格式一致**: 确保JSON格式与原有风格一致
2. **变量引用**: 使用`{{base_url}}`、`{{token}}`等变量
3. **示例数据**: 提供真实可用的示例请求体
4. **响应示例**: 添加典型的成功/失败响应示例
5. **描述清晰**: 每个接口都要有清晰的描述

---

## 🎯 最终检查

完成更新后，执行以下检查：

```bash
# 1. JSON语法检查
python3 -m json.tool API_TEST.postman_collection.json > /dev/null && echo "✅ API_TEST格式正确"
python3 -m json.tool ADMIN_API.postman_collection.json > /dev/null && echo "✅ ADMIN_API格式正确"

# 2. 接口数量统计
grep -o '"name":' API_TEST.postman_collection.json | wc -l  # 应约40+
grep -o '"name":' ADMIN_API.postman_collection.json | wc -l  # 应约38

# 3. 检查是否还有mode参数
grep -i '"mode"' API_TEST.postman_collection.json  # 应该没有对话接口的mode
```

---

**最后更新**: 2026-05-15  
**维护人员**: AI Assistant
