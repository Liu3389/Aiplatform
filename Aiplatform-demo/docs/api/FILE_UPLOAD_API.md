# 文件上传与对话 API 文档

## 📋 概述

本文档描述文件上传、解析和带文件对话的标准流程接口。

### 核心流程

```
1. 用户上传文件（最多8个） → POST /v1/file/upload
2. 后端立即返回fileId列表
3. 后台异步解析文件
4. 前端轮询查询状态 → GET /v1/file/parse-status/{fileId}
5. 解析成功后，用户发送问题 → POST /chat/stream-with-files
6. 后端读取已解析内容，拼接prompt，流式返回AI回答
```

---

## 🔧 接口列表

### 1. 批量上传文件

**接口**: `POST /v1/file/upload`

**认证**: 需要 `X-User-Token`

**请求**:
```http
POST /v1/file/upload
Content-Type: multipart/form-data
X-User-Token: {user_token}

files: [File, File, ...]  // 最多8个文件
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "uploadedCount": 3,
    "files": [
      {
        "fileId": "abc123def456...",
        "fileName": "report.pdf",
        "fileSize": 1024000,
        "fileType": "pdf",
        "status": "PENDING",
        "uploadTime": 1715960400000
      },
      {
        "fileId": "xyz789uvw012...",
        "fileName": "data.xlsx",
        "fileSize": 512000,
        "fileType": "xlsx",
        "status": "PENDING",
        "uploadTime": 1715960400000
      }
    ]
  }
}
```

**错误响应**:
```json
{
  "code": 400,
  "message": "最多支持上传8个文件"
}
```

**说明**:
- 最多支持一次上传8个文件
- 单个文件大小不超过10MB
- 支持的文件格式：`.txt`, `.md`, `.java`, `.py`, `.cpp`, `.xlsx`, `.docx`, `.html`, `.css`, `.js`, `.xml`, `.json`
- 上传后立即返回fileId，后台异步解析
- 前端需要根据fileId轮询查询解析状态

---

### 2. 查询文件解析状态

**接口**: `GET /v1/file/parse-status/{fileId}`

**认证**: 需要 `X-User-Token`

**请求**:
```http
GET /v1/file/parse-status/abc123def456...
X-User-Token: {user_token}
```

**响应（解析中）**:
```json
{
  "code": 200,
  "data": {
    "fileId": "abc123def456...",
    "fileName": "report.pdf",
    "status": "PARSING",
    "uploadTime": 1715960400000,
    "parseStartTime": 1715960401000
  }
}
```

**响应（解析成功）**:
```json
{
  "code": 200,
  "data": {
    "fileId": "abc123def456...",
    "fileName": "report.pdf",
    "status": "SUCCESS",
    "parsedTextLength": 15234,
    "uploadTime": 1715960400000,
    "parseStartTime": 1715960401000,
    "parseEndTime": 1715960405000
  }
}
```

**响应（解析失败）**:
```json
{
  "code": 200,
  "data": {
    "fileId": "abc123def456...",
    "fileName": "corrupted.pdf",
    "status": "FAILED",
    "errorMessage": "文件格式损坏，无法解析",
    "uploadTime": 1715960400000,
    "parseEndTime": 1715960403000
  }
}
```

**状态说明**:
- `PENDING`: 等待解析
- `PARSING`: 解析中
- `SUCCESS`: 解析成功
- `FAILED`: 解析失败

**前端轮询示例**:
```javascript
const checkStatus = async (fileId) => {
  const res = await fetch(`/v1/file/parse-status/${fileId}`, {
    headers: { 'X-User-Token': token }
  });
  const { status } = (await res.json()).data;
  
  if (status === 'SUCCESS') {
    unlockSendButton();  // 解锁发送按钮
  } else if (status === 'FAILED') {
    showError('文件解析失败');
  } else {
    setTimeout(() => checkStatus(fileId), 1500);  // 1.5秒后重试
  }
};
```

---

### 3. 删除未使用的文件

**接口**: `DELETE /v1/file/{fileId}`

**认证**: 需要 `X-User-Token`

**请求**:
```http
DELETE /v1/file/abc123def456...
X-User-Token: {user_token}
```

**响应**:
```json
{
  "code": 200,
  "data": true
}
```

**说明**:
- 只能删除未使用的文件（未在对话中使用）
- 如果文件已在对话中使用，会返回错误
- 删除后会同时清理Redis缓存

---

### 4. 带文件的流式对话

**接口**: `POST /chat/stream-with-files`

**认证**: 需要 `X-User-Token`

**请求**:
```http
POST /chat/stream-with-files
Content-Type: application/json
X-User-Token: {user_token}

{
  "conversationId": 101,
  "model": "deepseek-v4-pro",
  "question": "请总结这份报告的主要内容",
  "fileIds": ["abc123", "def456"]
}
```

**响应**: SSE流式输出

```
event: status
data: {"stage": "reading_files", "message": "正在读取文件内容..."}

event: status
data: {"stage": "processing", "message": "正在与AI对话..."}

event: message
data: {"content": "这"}

event: message
data: {"content": "份"}

event: message
data: {"content": "报"}

event: message
data: {"content": "告"}

event: done
data: {"finished": true, "inputTokens": 1523, "outputTokens": 456, "cost": 3.500}
```

**SSE事件说明**:

| 事件名 | 触发时机 | 数据格式 | 前端处理 |
|--------|---------|----------|---------|
| `status` | 阶段状态变化 | `{"stage": "...", "message": "..."}` | 显示进度提示 |
| `message` | AI逐字输出 | `{"content": "单个字符或词"}` | 实时追加到显示区 |
| `done` | 对话完成 | `{"finished": true, "inputTokens": x, "outputTokens": y, "cost": z}` | 显示统计信息，关闭连接 |
| `error` | 发生错误 | `{"message": "错误描述"}` | 显示错误提示，关闭连接 |

**Stage类型**:
- `reading_files`: 正在读取文件内容
- `processing`: 正在与AI对话

**前端对接示例**:
```javascript
const response = await fetch('/chat/stream-with-files', {
  method: 'POST',
  headers: {
    'X-User-Token': token,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    conversationId: currentConvId,
    model: selectedModel,
    question: userInput,
    fileIds: uploadedFiles.map(f => f.fileId)
  })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  
  const chunk = decoder.decode(value);
  const lines = chunk.split('\n');
  
  for (const line of lines) {
    if (line.startsWith('event: ')) {
      const event = line.substring(7);
    } else if (line.startsWith('data: ')) {
      const data = JSON.parse(line.substring(6));
      
      if (event === 'message') {
        appendToDisplay(data.content);
      } else if (event === 'done') {
        showStats(data);
      } else if (event === 'error') {
        showError(data.message);
      }
    }
  }
}
```

---

## 📊 完整流程示例

### JavaScript前端实现

```javascript
class FileChatClient {
  constructor(token) {
    this.token = token;
    this.baseUrl = '/api';
  }

  // 1. 批量上传文件
  async uploadFiles(files) {
    const formData = new FormData();
    for (let file of files) {
      formData.append('files', file);
    }

    const res = await fetch(`${this.baseUrl}/v1/file/upload`, {
      method: 'POST',
      headers: { 'X-User-Token': this.token },
      body: formData
    });

    const result = await res.json();
    return result.data.files;
  }

  // 2. 轮询查询解析状态
  async waitForParseComplete(fileId, timeout = 60000) {
    const startTime = Date.now();
    
    while (Date.now() - startTime < timeout) {
      const res = await fetch(`${this.baseUrl}/v1/file/parse-status/${fileId}`, {
        headers: { 'X-User-Token': this.token }
      });
      
      const { status } = (await res.json()).data;
      
      if (status === 'SUCCESS') {
        return true;
      } else if (status === 'FAILED') {
        throw new Error('文件解析失败');
      }
      
      // 等待1.5秒后重试
      await new Promise(resolve => setTimeout(resolve, 1500));
    }
    
    throw new Error('文件解析超时');
  }

  // 3. 带文件流式对话
  async chatWithFiles(question, fileIds, model = 'deepseek-v4-flash', callbacks = {}) {
    const response = await fetch(`${this.baseUrl}/chat/stream-with-files`, {
      method: 'POST',
      headers: {
        'X-User-Token': this.token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        question,
        fileIds,
        model
      })
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      const chunk = decoder.decode(value);
      const lines = chunk.split('\n');

      let currentEvent = '';
      for (const line of lines) {
        if (line.startsWith('event: ')) {
          currentEvent = line.substring(7);
        } else if (line.startsWith('data: ')) {
          const data = JSON.parse(line.substring(6));

          if (currentEvent === 'status') {
            callbacks.onStatus?.(data);
          } else if (currentEvent === 'message') {
            callbacks.onMessage?.(data.content);
          } else if (currentEvent === 'done') {
            callbacks.onDone?.(data);
          } else if (currentEvent === 'error') {
            callbacks.onError?.(data.message);
          }
        }
      }
    }
  }
}

// 使用示例
const client = new FileChatClient(userToken);

// 1. 上传文件
const files = fileInput.files;
const uploadedFiles = await client.uploadFiles(Array.from(files));

// 2. 等待所有文件解析完成
await Promise.all(
  uploadedFiles.map(file => client.waitForParseComplete(file.fileId))
);

// 3. 发送问题
await client.chatWithFiles(
  '请总结这些文档的主要内容',
  uploadedFiles.map(f => f.fileId),
  'deepseek-v4-pro',
  {
    onStatus: (data) => console.log('状态:', data.message),
    onMessage: (content) => displayArea.append(content),
    onDone: (stats) => console.log('统计:', stats),
    onError: (error) => alert('错误: ' + error)
  }
);
```

---

## ⚠️ 注意事项

1. **文件数量限制**: 最多一次上传8个文件
2. **文件大小限制**: 单个文件不超过10MB
3. **解析超时**: 大文件可能需要较长时间解析，建议设置60秒超时
4. **缓存过期**: 解析后的内容在Redis中保存30分钟，过期后需重新上传
5. **点数计费**: 带文件对话会扣除相应点数（根据模型和token数量）
6. **并发控制**: 单用户同时解析文件数限制为5个

---

## 🔐 安全说明

1. **文件校验**: 后端会校验文件类型和大小
2. **用户隔离**: 文件按用户ID存储在OSS不同目录
3. **权限控制**: 所有接口都需要有效的 `X-User-Token`
4. **归属验证**: 查询文件状态时会验证文件归属权

---

**文档版本**: v1.0  
**最后更新**: 2026-05-17  
**维护者**: 后端开发团队
