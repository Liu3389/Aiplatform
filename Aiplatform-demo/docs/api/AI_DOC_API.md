# AI 文档助手 API 文档

## ⚠️ 重要通知（2026-05-16）

### 接口废弃说明

以下接口已标记为 `@Deprecated`，**请尽快迁移到新接口**：

| 旧接口                                    | 新接口                                  | 状态             |
|----------------------------------------|--------------------------------------|----------------|
| `POST /v1/ai-doc/chat`                 | `POST /chat/stream-with-files`       | ❌ 已废弃，返回错误提示   |
| `POST /v1/ai-doc/chat/stream`          | `POST /chat/stream-with-files`       | ❌ 已废弃，返回错误提示   |
| `POST /v1/ai-doc/upload`               | `POST /v1/file/upload`               | ⚠️ 已废弃，建议使用新接口 |
| `GET /v1/ai-doc/parse-status/{fileId}` | `GET /v1/file/parse-status/{fileId}` | ⚠️ 已废弃，建议使用新接口 |
| `DELETE /v1/ai-doc/upload/{fileId}`    | `DELETE /v1/file/{fileId}`           | ⚠️ 已废弃，建议使用新接口 |

**迁移指南**:

- 文件上传请使用 `/v1/file/upload` 批量上传接口
- 带文件对话请使用 `/chat/stream-with-files` 标准流程
- 详见 [FILE_UPLOAD_API.md](./FILE_UPLOAD_API.md) 和 [CONVERSATION_FILE_API.md](./CONVERSATION_FILE_API.md)

---

## 📋 接口概览

AI 文档助手提供文件上传、解析、与 AI 对话的完整功能，支持**非流式**和**流式（SSE）**两种模式。

---

## 🔧 基础信息

**Base URL**: `http://localhost:8080/v1/ai-doc`

**认证方式**: Header `X-User-Token`

**支持的文件格式**:

- 文本类：`.txt`, `.md`, `.java`, `.py`, `.cpp`, `.html`, `.css`, `.js`, `.xml`, `.json`
- Office 文档：`.docx`, `.xlsx`

**文件大小限制**: 最大 10MB

---

## 📡 接口列表

### 1. 文件解析与 AI 对话（非流式） ❌ 已废弃

**⚠️ 该接口已废弃，请使用 `/chat/stream-with-files`**

**接口**: `POST /chat`

**状态**: @Deprecated - 调用将返回错误提示："该接口已废弃，请使用 /chat/stream-with-files 接口"

**Content-Type**: `multipart/form-data`

**请求参数**:

| 参数名      | 类型     | 必填 | 说明    |
|----------|--------|----|-------|
| file     | File   | ✅  | 上传的文件 |
| question | String | ✅  | 用户问题  |

**请求示例**:

```bash
curl -X POST http://localhost:8080/v1/ai-doc/chat \
  -H "X-User-Token: YOUR_TOKEN" \
  -F "file=@document.pdf" \
  -F "question=请总结这个文档的主要内容"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": "根据文档内容，主要讨论了以下几个方面：\n\n1. AI技术的发展趋势...\n2. 应用场景分析...\n3. 未来展望..."
}
```

**适用场景**:

- 小文件快速处理
- 不需要实时进度反馈
- 简单问答场景

---

### 2. 文件解析与 AI 对话（流式 - SSE） ❌ 已废弃

**⚠️ 该接口已废弃，请使用 `/chat/stream-with-files`**

**接口**: `POST /chat/stream`

**状态**: @Deprecated - 调用将返回SSE错误事件："该接口已废弃，请使用 /chat/stream-with-files 接口"

**Content-Type**: `multipart/form-data`

**Produces**: `text/event-stream`

**请求参数**:

| 参数名      | 类型     | 必填 | 说明    |
|----------|--------|----|-------|
| file     | File   | ✅  | 上传的文件 |
| question | String | ✅  | 用户问题  |

**请求示例**:

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('question', '请总结这个文档的主要内容');

fetch('/v1/ai-doc/chat/stream', {
  method: 'POST',
  headers: {
    'X-User-Token': userToken
  },
  body: formData
})
.then(response => {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  
  function readStream() {
    reader.read().then(({ done, value }) => {
      if (done) return;
      
      const chunk = decoder.decode(value);
      const lines = chunk.split('\n');
      
      lines.forEach(line => {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim();
        } else if (line.startsWith('data:') && currentEvent) {
          const data = JSON.parse(line.slice(5).trim());
          handleEvent(currentEvent, data);
        }
      });
      
      readStream();
    });
  }
  
  readStream();
});

function handleEvent(event, data) {
  switch(event) {
    case 'status':
      if (data.stage === 'parsing') {
        showLoading('正在解析文件...');
      } else if (data.stage === 'parsed') {
        hideLoading();
        showThinking('AI正在思考...');
      }
      break;
      
    case 'content':
      renderMarkdown(data.text);
      break;
      
    case 'done':
      hideThinking();
      showMessage('对话完成');
      break;
      
    case 'error':
      showError(data.message);
      break;
  }
}
```

**SSE 事件说明**:

| 事件名       | 触发时机    | 数据格式                                                                 | 前端处理        |
|-----------|---------|----------------------------------------------------------------------|-------------|
| `message` | AI 逐字输出 | `{"content": "单个字符或词"}`                                              | 实时追加到显示区    |
| `done`    | 对话完成    | `{"finished": true, "inputTokens": x, "outputTokens": y, "cost": z}` | 显示统计信息，关闭连接 |
| `error`   | 发生错误    | `{"message": "错误描述"}`                                                | 显示错误提示，关闭连接 |

**重要说明**：

流式接口采用**两步流程**：

1. **第一步**：调用 `/v1/ai-doc/upload` 上传文件
    - 后端立即解析文件并缓存到 Redis
    - 返回 `fileId`

2. **第二步**：调用 `/v1/ai-doc/chat/stream` 进行对话
    - 传入 `fileId` + `question`
    - 后端从 Redis 读取已解析的内容
    - **调用 OpenAiChatService.streamChat 进行真正的流式对话**
    - SSE 逐字推送 AI 回复内容

**优势**：

- ✅ 文件只解析一次，可多次对话
- ✅ **真正的流式输出**，用户体验极佳
- ✅ 解析和对话分离，更灵活
- ✅ 支持大文件（异步处理）
- ✅ 用户可以取消未使用的文件（调用 DELETE /upload/{fileId}）

**事件流程图**:

```
第一步：上传文件
POST /v1/ai-doc/upload
    ↓
后端解析文件并缓存到 Redis (TTL: 30分钟)
    ↓
返回 fileId
    ↓
第二步：流式对话
POST /v1/ai-doc/chat/stream (fileId + question)
    ↓
后端从 Redis 读取已解析的文件内容
    ↓
调用 OpenAiChatService.streamChat
    ↓
[发送 message] → 前端实时追加显示（逐字）
    ↓
[发送 message] → 继续推送...
    ↓
[发送 done] → 前端显示统计信息，关闭连接
```

**响应示例**（SSE 流）:

```
event: message
data: {"content":"根"}

event: message
data: {"content":"据"}

event: message
data: {"content":"文"}

event: message
data: {"content":"档"}

event: message
data: {"content":"内"}

event: message
data: {"content":"容"}

...

event: done
data: {"finished":true,"inputTokens":150,"outputTokens":320,"cost":1.0}
```

**适用场景**:

- ✅ 大文件处理（需要进度反馈）
- ✅ 提升用户体验（实时显示状态）
- ✅ 避免超时等待（异步处理）
- ✅ 复杂文档分析

**优势对比**:

| 特性    | 非流式        | 流式（SSE）    |
|-------|------------|------------|
| 用户体验  | ❌ 长时间等待无反馈 | ✅ 实时显示进度   |
| 大文件支持 | ⚠️ 可能超时    | ✅ 异步处理，稳定  |
| 前端控制  | ❌ 黑盒等待     | ✅ 精确控制每个阶段 |
| 错误提示  | ❌ 统一错误消息   | ✅ 分阶段错误定位  |
| 实现复杂度 | 简单         | 中等         |

---

### 3. 导出 AI 结果

**接口**: `POST /export`

**Content-Type**: `application/json`

**请求参数**:

```json
{
  "content": "AI 回复的 Markdown 内容",
  "format": "md"
}
```

**支持的格式**:

- `md` - Markdown 文件
- `txt` - 纯文本文件
- `pdf` - PDF 文档（待实现）
- `docx` - Word 文档（待实现）

**响应**: 文件下载（`application/octet-stream`）

**请求示例**:

```bash
curl -X POST http://localhost:8080/v1/ai-doc/export \
  -H "X-User-Token: YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "# AI 分析报告\n\n这是AI生成的内容...",
    "format": "md"
  }' \
  --output ai_result.md
```

---

## 💡 最佳实践

### 1. 选择合适的接口

**使用非流式接口** (`/chat`) 当：

- 文件较小（< 1MB）
- 网络环境良好
- 不需要实时进度反馈

**使用流式接口** (`/chat/stream`) 当：

- 文件较大（> 1MB）
- 需要提升用户体验
- 网络不稳定（可显示进度）
- 需要精确的错误定位

### 2. 前端实现建议

```javascript
// 推荐的封装方式
class AiDocClient {
  constructor(token) {
    this.token = token;
    this.baseUrl = '/v1/ai-doc';
  }
  
  // 流式对话（基于 fileId）
  async chatWithFileStream(fileId, question, callbacks) {
    const response = await fetch(`${this.baseUrl}/chat/stream`, {
      method: 'POST',
      headers: { 
        'X-User-Token': this.token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ fileId, question })
    });
    
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let currentEvent = null;
    
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      
      const chunk = decoder.decode(value);
      const lines = chunk.split('\n');
      
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim();
        } else if (line.startsWith('data:') && currentEvent) {
          const data = JSON.parse(line.slice(5).trim());
          
          // 调用对应的回调
          if (callbacks[currentEvent]) {
            callbacks[currentEvent](data);
          }
        }
      }
    }
  }
}

// 使用示例
const client = new AiDocClient(userToken);

// 第一步：上传文件
const uploadResponse = await fetch('/v1/ai-doc/upload', {
  method: 'POST',
  headers: { 'X-User-Token': userToken },
  body: formData // FormData 包含 file
});

const uploadResult = await uploadResponse.json();
const fileId = uploadResult.data.fileId;

// 第二步：流式对话（真正的逐字输出）
await client.chatWithFileStream(
  fileId,
  '请总结这个文档',
  {
    message: (data) => {
      // 实时追加每个字符
      appendToDisplay(data.content);
    },
    done: (data) => {
      console.log('对话完成');
      console.log('输入 Token:', data.inputTokens);
      console.log('输出 Token:', data.outputTokens);
      console.log('消耗点数:', data.cost);
    },
    error: (data) => {
      showError(data.message);
    }
  }
);
```

### 3. 错误处理

```javascript
try {
  await client.chatWithFileStream(file, question, callbacks);
} catch (error) {
  console.error('文件解析失败:', error);
  showError('文件处理失败，请重试');
}
```

---

## 🔐 安全说明

1. **文件校验**: 后端会校验文件类型和大小
2. **用户隔离**: 文件按用户 ID 存储在 OSS 不同目录
3. **权限控制**: 所有接口都需要有效的 `X-User-Token`
4. **点数计费**: 每次对话会扣除相应点数

---

## 📊 性能指标

| 指标       | 数值       |
|----------|----------|
| 最大文件大小   | 10 MB    |
| 支持文件格式   | 12 种     |
| SSE 超时时间 | 120 秒    |
| 平均解析速度   | ~500KB/s |
| 并发限制     | 取决于服务器配置 |

---

**文档版本**: v1.1  
**最后更新**: 2026-05-16  
**维护者**: 后端开发团队  
**更新记录**:

- v1.1 (2026-05-16): 标记旧接口为@Deprecated，引导迁移到 `/chat/stream-with-files`
- v1.0 (2026-05-17): 初始版本
