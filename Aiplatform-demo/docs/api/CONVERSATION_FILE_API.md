# 会话与文件管理 API 文档

## 概述

本文档描述会话管理和历史文件查询的核心接口，支持：

- ✅ 登录鉴权与数据隔离
- ✅ 会话列表加载（带预览内容）
- ✅ 历史消息分页加载（默认15条）
- ✅ 会话关联文件详情查询（支持分页）
- ✅ 文件复用机制（解析结果缓存）
- ✅ **N+1查询优化**（批量预加载最新消息）
- ✅ **参数校验增强**（防止非法请求）

---

## ⚠️ 重要更新（2026-05-16）

### 性能优化

- **fetchConversations接口**: 优化N+1查询问题，响应时间从~500ms降至~100ms（提升80%）
- **实现方式**: 批量预加载所有会话的最新消息到Map，避免循环查询数据库

### 安全性增强

- **clearConversations接口**: 添加userId空值保护，防止误删全表数据
- **export-content接口**: 添加速率限制（5秒冷却时间），防止滥用
- **messages/page接口**: 添加@Valid参数校验，limit限制为1-50

### 异常处理改进

- **streamChat接口**: 统一异常处理，返回友好错误提示而非500错误

---

## 1. 获取会话列表

### 接口信息

- **URL**: `GET /api/user/conversations/fetch`
- **认证**: 需要 `X-User-Token` header
- **性能**: ✅ N+1查询已优化，响应时间 <150ms（100个会话场景）

### 请求参数

无

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversations": [
      {
        "id": "123",
        "title": "Python学习助手",
        "model": "deepseek-v4-flash",
        "messageCount": 6,
        "lastMessageTime": "2026-05-16T14:30:00",
        "createTime": "2026-05-16T10:00:00",
        "previewContent": "如何用Python实现快速排序？"
      }
    ],
    "version": 1715932800000
  }
}
```

### 字段说明

| 字段                 | 类型       | 说明                     |
|--------------------|----------|------------------------|
| id                 | String   | 会话ID                   |
| title              | String   | 会话标题                   |
| model              | String   | 使用的模型标识                |
| messageCount       | Integer  | 消息数量                   |
| lastMessageTime    | DateTime | 最后聊天时间                 |
| createTime         | DateTime | 创建时间                   |
| **previewContent** | String   | **简易预览内容（最后一条消息前50字）** |
| version            | Long     | 版本号（用于前端缓存同步）          |

---

## 2. 获取会话最新历史消息

### 接口信息

- **URL**: `GET /api/user/conversations/{conversationId}/messages/latest`
- **认证**: 需要 `X-User-Token` header

### 请求参数

| 参数             | 位置    | 必填 | 默认值    | 说明         |
|----------------|-------|----|--------|------------|
| conversationId | Path  | 是  | -      | 会话ID       |
| limit          | Query | 否  | **15** | 加载条数（最大50） |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "messages": [
      {
        "id": 1001,
        "role": "user",
        "content": "如何用Python实现快速排序？",
        "model": "deepseek-v4-flash",
        "tokens": 15,
        "createTime": "2026-05-16T10:00:00"
      },
      {
        "id": 1002,
        "role": "assistant",
        "content": "快速排序是一种高效的排序算法...",
        "model": "deepseek-v4-flash",
        "tokens": 180,
        "createTime": "2026-05-16T10:00:05"
      }
    ],
    "hasMore": true,
    "nextCursorId": 1002
  }
}
```

### 字段说明

| 字段                    | 类型       | 说明                       |
|-----------------------|----------|--------------------------|
| messages              | Array    | 消息列表（按时间正序）              |
| messages[].id         | Long     | 消息ID                     |
| messages[].role       | String   | 角色：user/assistant/system |
| messages[].content    | String   | 消息内容                     |
| messages[].model      | String   | 使用的模型                    |
| messages[].tokens     | Integer  | Token数量                  |
| messages[].createTime | DateTime | 发送时间                     |
| hasMore               | Boolean  | 是否有更多历史消息                |
| nextCursorId          | Long     | 下一页游标ID（用于上滑加载）          |

---

## 3. 游标分页查询历史消息

### 接口信息

- **URL**: `POST /api/user/conversations/messages/page`
- **认证**: 需要 `X-User-Token` header
- **校验**: ✅ @Valid参数校验（conversationId必填，limit范围1-50）

### 请求体

```json
{
  "conversationId": 123,
  "lastId": 1002,
  "limit": 15
}
```

### 请求参数

| 参数             | 必填 | 默认值    | 说明                     |
|----------------|----|--------|------------------------|
| conversationId | 是  | -      | 会话ID（@NotNull）         |
| lastId         | 否  | null   | 上一页最后一条消息ID（首次查询不传）    |
| limit          | 否  | **15** | 每页条数（@Min(1) @Max(50)） |

### 错误响应示例

```json
{
  "code": 400,
  "message": "会话ID不能为空"
}
```

或

```json
{
  "code": 400,
  "message": "每页条数最大为50"
}
```

### 响应示例

同接口2

---

## 4. 分页查询会话关联文件详情 ⭐新增

### 接口信息

- **URL**: `POST /api/user/conversations/files/page`
- **认证**: 需要 `X-User-Token` header

### 功能说明

- ✅ 支持大批量历史文件数量分页
- ✅ 左联查询file_parse_task表返回详细信息
- ✅ 仅返回摘要，不返回完整文本
- ✅ 权限校验：只能查询当前用户的文件

### 请求体

```json
{
  "conversationId": 123,
  "page": 1,
  "size": 10
}
```

### 请求参数

| 参数             | 必填 | 默认值 | 说明         |
|----------------|----|-----|------------|
| conversationId | 是  | -   | 会话ID       |
| page           | 否  | 1   | 页码（从1开始）   |
| size           | 否  | 10  | 每页大小（最大50） |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "files": [
      {
        "fileId": "test-file-001-uuid",
        "fileName": "test_report.pdf",
        "fileType": "pdf",
        "fileSize": 1024000,
        "parseStatus": "SUCCESS",
        "ossUrl": "https://oss.example.com/documents/1/test_report.pdf",
        "textSummary": "这是一份关于系统架构设计的PDF文档，包含了微服务设计、数据库优化、性能调优等核心内容。",
        "parsedTextLength": 15234,
        "uploadTime": "2026-05-16T10:00:00",
        "isUsed": false
      }
    ],
    "total": 5,
    "page": 1,
    "size": 10,
    "totalPages": 1,
    "hasNext": false
  }
}
```

### 字段说明

| 字段                       | 类型       | 说明                                  |
|--------------------------|----------|-------------------------------------|
| files                    | Array    | 文件列表                                |
| files[].fileId           | String   | 文件唯一ID                              |
| files[].fileName         | String   | 原始文件名                               |
| files[].fileType         | String   | 文件类型（txt/md/docx/xlsx/pdf等）         |
| files[].fileSize         | Long     | 文件大小（字节）                            |
| files[].parseStatus      | String   | 解析状态：PENDING/PARSING/SUCCESS/FAILED |
| files[].ossUrl           | String   | OSS访问地址                             |
| **files[].textSummary**  | String   | **文本摘要（AI生成，50-100字）**              |
| files[].parsedTextLength | Integer  | 解析后文本长度                             |
| files[].uploadTime       | DateTime | 上传时间                                |
| files[].isUsed           | Boolean  | 是否已在对话中使用                           |
| total                    | Long     | 总数量                                 |
| page                     | Integer  | 当前页码                                |
| size                     | Integer  | 每页大小                                |
| totalPages               | Integer  | 总页数                                 |
| hasNext                  | Boolean  | 是否有下一页                              |

---

## 5. 清除所有会话 ⭐新增说明

### 接口信息

- **URL**: `DELETE /api/user/conversations/clear`
- **认证**: 需要 `X-User-Token` header
- **安全**: ✅ userId空值保护，防止误删全表数据

### 功能说明

- 逻辑删除当前用户的所有会话（is_deleted = 1）
- 清空Redis中的会话缓存
- 记录影响行数，便于审计

### 响应示例

```json
{
  "code": 200,
  "message": "清除成功",
  "data": null
}
```

### 错误响应

```json
{
  "code": 401,
  "message": "用户认证失败"
}
```

---

## 6. 文件复用机制说明

### 工作流程

1. **用户上传文件** → POST `/v1/file/upload`
    - 立即返回fileId
    - 后台异步解析文件
    - 解析结果缓存到Redis（TTL 30分钟）
    - 同时存入file_parse_task表（持久化）

2. **前端轮询状态** → GET `/v1/file/parse-status/{fileId}`
    - 查询解析进度
    - 状态：PENDING → PARSING → SUCCESS/FAILED

3. **用户发起对话** → POST `/chat/stream-with-files`
    - 传入fileIds
    - 后端检查file_parse_task表
    - 如果已解析成功，直接从Redis读取
    - 标记文件为已使用（is_used = 1）

4. **再次使用同一文件**
    - 如果Redis缓存未过期 → 直接读取（毫秒级）
    - 如果Redis缓存过期 → 从OSS重新下载并解析
    - file_parse_task表记录永久保存

### 优势

- ✅ 避免重复解析（节省CPU和时间）
- ✅ Redis缓存加速（30分钟内秒级响应）
- ✅ 持久化存储（file_parse_task表）
- ✅ 文本摘要减少数据传输量

---

## 7. 冷热数据分离

### 热数据表

- **表名**: `chat_message`
- **范围**: 最近30天的消息
- **索引**: `idx_conversation_create`, `idx_user_conversation`

### 冷数据表

- **表名**: `chat_message_history`
- **范围**: 30天前的消息
- **归档**: DataArchiveService定时任务自动归档

### 查询逻辑

1. 优先查询热数据表
2. 如果数据不足，补充查询冷数据表
3. 合并结果后按时间排序返回
4. Redis缓存30分钟

---

## 8. 数据隔离与安全

### 登录鉴权

- 所有接口必须携带 `X-User-Token` header
- TokenService解析token获取userId
- userId为null时抛出异常

### 数据隔离

- 所有查询强制带 `userId` 条件
- 会话归属权校验（防止越权访问）
- 文件归属权校验（只能查看自己的文件）

### 权限校验示例

```java
// 会话查询
wrapper.eq(Conversation::getUserId, userId)

// 文件查询
wrapper.eq(FileParseTask::getUserId, userId)
```

---

## 9. 分页策略对比

### 游标分页（消息）

- **适用场景**: 历史消息加载
- **优点**: 性能好，不受数据偏移影响
- **参数**: `lastId`（上一页最后一条记录ID）
- **SQL**: `WHERE id < lastId ORDER BY id DESC LIMIT 15`

### 传统分页（文件）

- **适用场景**: 文件列表展示
- **优点**: 易于理解，支持跳转到任意页
- **参数**: `page`, `size`
- **SQL**: `LIMIT (page-1)*size, size`

---

## 10. 错误码

| 错误码 | 说明         | 解决方案                        |
|-----|------------|-----------------------------|
| 401 | Token无效或过期 | 重新登录获取新token                |
| 403 | 无权访问该资源    | 检查会话/文件归属权                  |
| 404 | 资源不存在      | 检查conversationId/fileId是否正确 |
| 500 | 服务器内部错误    | 联系管理员查看日志                   |

---

## 11. 最佳实践

### 前端加载策略

1. **首次进入页面**
    - 调用接口1获取会话列表
    - 选中第一个会话
    - 调用接口2加载最新15条消息

2. **上滑加载更多**
    - 监听滚动事件（触顶）
    - 调用接口3，传入lastId
    - 追加到消息列表顶部

3. **查看关联文件**
    - 点击"查看文件"按钮
    - 调用接口4，page=1, size=10
    - 显示文件卡片（文件名、摘要、大小）

### 性能优化建议

- ✅ 使用Redis缓存会话列表（90天）
- ✅ 使用Redis缓存消息（30分钟）
- ✅ 限制单次加载条数（最大50）
- ✅ 文件摘要不超过100字
- ✅ 懒加载文件详情（按需查询）

---

**文档版本**: v1.1  
**最后更新**: 2026-05-16  
**维护者**: 后端开发团队  
**更新记录**:

- v1.1 (2026-05-16): 性能优化（N+1查询）、安全性增强（参数校验、速率限制）、异常处理改进
- v1.0 (2026-05-16): 初始版本
