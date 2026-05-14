# Postman接口文档更新说明

## 📅 更新日期
2026年5月14日

## 🎯 更新内容

本次更新将Postman集合文件 `API_TEST.postman_collection.json` 与P0优化功能同步，添加了所有新增的接口和字段。

---

## ✅ 主要更新

### 1. 集合信息更新

**描述字段**添加了最新更新说明：
```
**最新更新 (2026-05-14)**:
- ✅ 新增会话管理（单设备登录、异地踢下线）
- ✅ 新增对话版本控制（服务端存储）
- ✅ 新增用户限流（60次/分钟）
- ✅ 新增SSE断线重连支持
- ✅ 所有请求需携带 X-Device-Id Header
```

### 2. 新增环境变量

添加了3个新的集合变量：
```json
{
  "key": "deviceId",
  "value": ""
},
{
  "key": "userId", 
  "value": ""
}
```

### 3. 登录接口增强

**用户登录**接口更新：
- ✅ 添加 `X-Device-Id` Header
- ✅ Prerequest脚本自动生成UUID作为设备ID
- ✅ Test脚本自动保存token、deviceId、userId到环境变量

```javascript
// Prerequest脚本
var deviceId = pm.collectionVariables.get('deviceId');
if (!deviceId) {
  deviceId = require('uuid').v4();
  pm.collectionVariables.set('deviceId', deviceId);
}

// Test脚本
pm.collectionVariables.set('token', json.data.token);
pm.collectionVariables.set('deviceId', json.data.deviceId);
pm.collectionVariables.set('userId', json.data.userInfo.id);
```

### 4. 新增接口分组

#### 4.1 会话与对话管理（5个新接口）

| 接口名称 | 方法 | 路径 | 说明 |
|---------|------|------|------|
| 获取对话数据 | GET | `/api/conversations` | 获取服务端存储的对话列表 |
| 保存对话数据 | POST | `/api/conversations` | 保存对话到服务端，支持版本控制 |
| 检查对话版本 | GET | `/api/conversations/version?localVersion=0` | 检查本地缓存是否过期 |
| 清空对话数据 | DELETE | `/api/conversations/clear` | 账号切换时清空对话 |
| 检查会话状态 | GET | `/api/session/status` | 检查当前会话是否有效 |

**所有接口都需要携带**:
- `X-User-Token`: {{token}}
- `X-Device-Id`: {{deviceId}}

#### 4.2 限流与安全测试（3个测试接口）

| 接口名称 | 方法 | 路径 | 说明 |
|---------|------|------|------|
| 快速请求测试 | GET | `/api/auth/userinfo` | 触发限流测试（60次/分钟） |
| 异地登录测试（设备A） | POST | `/api/auth/login` | 设备A登录 |
| 异地登录测试（设备B） | POST | `/api/auth/login` | 设备B登录，踢掉设备A |

**快速请求测试特点**:
- 需要携带 `X-User-Id` Header
- Test脚本检测429状态码
- 控制台输出限流提示

**异地登录测试特点**:
- 使用固定的设备ID便于测试
- 设备A: `device-a-test-001`
- 设备B: `device-b-test-002`

### 5. SSE断线重连接口

在"聊天对话"分组中新增：
- **SSE断线重连测试**
  - 方法: POST
  - 路径: `/api/chat/stream`
  - 新增Header: `Last-Event-ID: {{lastEventId}}`
  - Body新增字段: `"reconnect": true`

### 6. 所有接口统一添加设备ID

以下所有接口的Header都添加了 `X-Device-Id: {{deviceId}}`:

#### 用户认证模块（6个接口）
- ✅ 查询用户信息
- ✅ 查询余额
- ✅ 刷新Token
- ✅ 退出登录
- ✅ 用户注册（不需要，公开接口）
- ✅ 用户登录（已单独处理）

#### API Key管理模块（6个接口）
- ✅ 生成API Key
- ✅ 查询API Key列表
- ✅ 启用API Key
- ✅ 禁用API Key
- ✅ 更新限流阈值
- ✅ 删除API Key

#### 聊天对话模块（4个接口）
- ✅ 免费模式 - 非流式对话
- ✅ 免费模式 - 流式对话(SSE)
- ✅ 自定义模型 - 非流式对话
- ✅ SSE断线重连测试

#### 用户API Key模块（2个接口）
- ✅ 添加第三方API Key
- ✅ 查看第三方API Key列表

**总计更新**: 18个现有接口 + 8个新接口 = 26个接口

---

## 📊 更新统计

| 类别 | 数量 |
|------|------|
| 新增接口分组 | 2个 |
| 新增接口 | 8个 |
| 修改接口 | 18个 |
| 新增环境变量 | 2个 |
| 总接口数 | 26个 |

---

## 🔧 使用说明

### 1. 导入Postman集合

```bash
# 在Postman中导入
File → Import → 选择 API_TEST.postman_collection.json
```

### 2. 设置环境变量

首次使用前，确保设置以下变量：
- `baseUrl`: http://localhost:8080（默认已设置）
- `token`: 登录后自动填充
- `deviceId`: 登录后自动填充
- `userId`: 登录后自动填充
- `apiKey`: 生成API Key后自动填充

### 3. 测试流程

#### 基础测试流程
```
1. 用户注册 → 创建测试账号
2. 用户登录 → 自动保存token和deviceId
3. 查询用户信息 → 验证会话正常
4. 生成API Key → 用于聊天接口
5. 免费模式对话 → 测试聊天功能
```

#### 会话隔离测试流程
```
1. 异地登录测试（设备A）→ 记录返回的token
2. 异地登录测试（设备B）→ 踢掉设备A
3. 使用设备A的token请求 → 应被拒绝（401/403）
4. 使用设备B的token请求 → 正常访问
```

#### 限流测试流程
```
1. 使用Postman Runner
2. 运行"快速请求测试"接口
3. 设置迭代次数: 70次
4. 观察前60次成功，第61次开始返回429
```

#### 对话版本控制测试流程
```
1. 保存对话数据 → 上传对话到服务端
2. 检查对话版本 → 获取最新版本号
3. 获取对话数据 → 验证数据正确性
4. 清空对话数据 → 模拟账号切换
5. 再次获取 → 应返回空列表
```

---

## ⚠️ 注意事项

### 1. 设备ID的重要性

**所有需要认证的接口都必须携带 `X-Device-Id` Header**，否则：
- 会话验证失败
- 可能被判定为非法请求
- 无法享受会话隔离保护

### 2. 限流阈值

当前配置：
- **每分钟最多60次请求**
- 超过后返回429状态码
- 60秒后自动重置

如需调整，修改 `UserRateLimitInterceptor.java`:
```java
private static final int MAX_REQUESTS_PER_MINUTE = 60;
```

### 3. SSE断线重连

目前后端已实现SSE连接管理框架，但完整的断点续传功能需要：
1. 前端实现重连逻辑
2. 携带 `Last-Event-ID` Header
3. Body中添加 `"reconnect": true`

### 4. 对话数据格式

保存对话时的JSON格式示例：
```json
{
  "conversations": [
    {
      "id": "conv-001",
      "title": "测试对话",
      "messages": [],
      "createTime": "2026-05-14T10:00:00"
    }
  ],
  "version": 0
}
```

---

## 📝 接口变更对比

### 变更前
```http
GET /api/auth/userinfo
Headers:
  X-User-Token: {{token}}
```

### 变更后
```http
GET /api/auth/userinfo
Headers:
  X-User-Token: {{token}}
  X-Device-Id: {{deviceId}}  ← 新增
```

---

## 🚀 后续建议

### P1优先级
1. 添加管理员接口测试分组
2. 添加批量请求测试用例
3. 添加性能测试脚本
4. 添加错误场景测试（无效token、过期会话等）

### P2优先级
1. 导出为OpenAPI/Swagger格式
2. 添加自动化测试脚本
3. 添加Mock服务器配置
4. 添加接口文档网站

---

## 📞 问题反馈

如在使用过程中发现问题，请检查：
1. Postman版本是否为最新版
2. 后端服务是否正常运行
3. Redis是否正常连接
4. 环境变量是否正确设置

---

**更新时间**: 2026年5月14日  
**更新人员**: AI Gateway Platform Team  
**文档版本**: v2.0 (P0优化版)
