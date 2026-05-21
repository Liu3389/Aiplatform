# AI对话文件处理重构方案

## 📋 现状分析

### 当前问题

1. **接口混乱**：存在多个文件上传和对话接口，职责不清晰
    - `/api/chat/with-file` - ChatController中的传统方式
    - `/v1/ai-doc/upload` - AiDocController的两步流程
    - `/v1/ai-doc/chat` - 非流式旧接口
    - `/v1/ai-doc/chat/stream` - 流式新接口

2. **缺少异步解析**：目前文件解析是同步的，大文件会阻塞请求

3. **缺少批量上传支持**：不支持一次上传多个文件（最多8个）

4. **状态管理不完善**：没有明确的"解析中/成功/失败"状态流转

---

## 🎯 目标架构

### 标准流程（用户视角）

```
1. 用户选择文件（最多8个） → 前端立即上传
2. 后端接收 → 保存到OSS → 返回fileId
3. 后端异步解析 → 更新解析状态
4. 前端轮询查询状态 → 显示"解析中/成功/失败"
5. 解析成功后 → 用户输入问题并发送
6. 后端读取已解析内容 → 拼接prompt → 扣费 → 调用AI → 流式返回
```

---

## 🔧 改造方案

### 一、数据库设计（新增表）

#### 1. 文件解析任务表 `file_parse_task`

```sql
CREATE TABLE `file_parse_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件唯一ID（UUID）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `oss_url` VARCHAR(512) NOT NULL COMMENT 'OSS存储地址',
  `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
  `file_type` VARCHAR(32) NOT NULL COMMENT '文件类型（txt/md/docx/xlsx等）',
  
  -- 解析状态
  `parse_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING/PARSING/SUCCESS/FAILED',
  `parsed_text_length` INT DEFAULT 0 COMMENT '解析后文本长度',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息（解析失败时）',
  
  -- 时间戳
  `upload_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `parse_start_time` DATETIME DEFAULT NULL COMMENT '开始解析时间',
  `parse_end_time` DATETIME DEFAULT NULL COMMENT '解析完成时间',
  
  -- 关联信息
  `conversation_id` BIGINT DEFAULT NULL COMMENT '关联会话ID（对话时使用）',
  `is_used` TINYINT(1) DEFAULT 0 COMMENT '是否已在对话中使用',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_id` (`file_id`),
  KEY `idx_user_status` (`user_id`, `parse_status`),
  KEY `idx_upload_time` (`upload_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件解析任务表';
```

---

### 二、后端接口设计（3个核心接口）

#### 接口1：上传文件（支持批量，最多8个）

**路径**: `POST /v1/file/upload`

**请求**:

```http
Content-Type: multipart/form-data
X-User-Token: {token}

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
        "uploadTime": "2026-05-17T23:30:00"
      },
      ...
    ]
  }
}
```

**逻辑**:

1. 校验文件数量（≤8个）
2. 逐个校验文件大小（≤10MB）和格式
3. 上传到OSS，生成fileId
4. 创建解析任务记录（状态=PENDING）
5. 提交异步解析任务到线程池
6. 立即返回fileId列表

---

#### 接口2：查询文件解析状态

**路径**: `GET /v1/file/parse-status/{fileId}`

**请求**:

```http
GET /v1/file/parse-status/abc123def456...
X-User-Token: {token}
```

**响应（解析中）**:

```json
{
  "code": 200,
  "data": {
    "fileId": "abc123def456...",
    "fileName": "report.pdf",
    "status": "PARSING",
    "progress": 50,  // 可选：解析进度百分比
    "uploadTime": "2026-05-17T23:30:00",
    "parseStartTime": "2026-05-17T23:30:01"
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
    "uploadTime": "2026-05-17T23:30:00",
    "parseEndTime": "2026-05-17T23:30:05"
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
    "uploadTime": "2026-05-17T23:30:00",
    "parseEndTime": "2026-05-17T23:30:03"
  }
}
```

---

#### 接口3：带文件的流式对话

**路径**: `POST /v1/chat/stream-with-files`

**请求**:

```http
POST /v1/chat/stream-with-files
Content-Type: application/json
X-User-Token: {token}

{
  "conversationId": 101,
  "model": "deepseek-v4-pro",
  "question": "请总结这份报告的主要内容",
  "fileIds": ["abc123", "def456"]  // 可选：关联的文件ID列表
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

event: done
data: {"finished": true, "inputTokens": 1523, "outputTokens": 456, "cost": 3.500}
```

**逻辑**:

1. 根据fileIds查询已解析的文本内容
2. 校验所有文件状态必须为SUCCESS
3. 拼接prompt：`问题 + 文件1内容 + 文件2内容 + ...`
4. 预扣点数
5. 调用OpenAiChatService.streamChat
6. 流式返回AI回答
7. 标记文件为"已使用"

---

### 三、后端服务层改造

#### 1. 新增 FileParseService

```java
@Service
@RequiredArgsConstructor
public class FileParseService {
    
    private final OssService ossService;
    private final FileParseTaskMapper taskMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Executor asyncExecutor;  // 异步线程池
    
    /**
     * 批量上传文件（最多8个）
     */
    public List<FileUploadResult> uploadFiles(List<MultipartFile> files, Long userId) {
        // 1. 校验文件数量和大小
        if (files.size() > 8) {
            throw new BusinessException("最多支持上传8个文件");
        }
        
        List<FileUploadResult> results = new ArrayList<>();
        
        // 2. 逐个处理文件
        for (MultipartFile file : files) {
            // 上传OSS
            String ossUrl = ossService.uploadFile(file, "documents/" + userId + "/");
            
            // 生成fileId
            String fileId = UUID.randomUUID().toString().replace("-", "");
            
            // 创建解析任务记录
            FileParseTask task = new FileParseTask();
            task.setFileId(fileId);
            task.setUserId(userId);
            task.setOriginalName(file.getOriginalFilename());
            task.setOssUrl(ossUrl);
            task.setFileSize(file.getSize());
            task.setFileType(getFileType(file.getOriginalFilename()));
            task.setParseStatus("PENDING");
            task.setUploadTime(new Date());
            taskMapper.insert(task);
            
            // 提交异步解析任务
            asyncExecutor.execute(() -> parseFileAsync(task.getId(), fileId, ossUrl));
            
            // 构建返回结果
            results.add(new FileUploadResult(fileId, file.getOriginalFilename(), ...));
        }
        
        return results;
    }
    
    /**
     * 异步解析文件
     */
    @Async
    public void parseFileAsync(Long taskId, String fileId, String ossUrl) {
        // 1. 更新状态为PARSING
        taskMapper.updateStatus(taskId, "PARSING", new Date());
        
        try {
            // 2. 从OSS下载文件
            byte[] fileBytes = ossService.downloadFile(ossUrl);
            
            // 3. 解析文件内容为文本
            String content = parseFileContent(fileBytes, getFileTypeFromUrl(ossUrl));
            
            // 4. 缓存到Redis（TTL 30分钟）
            String cacheKey = "file:parsed:" + fileId;
            redisTemplate.opsForValue().set(cacheKey, content, 30, TimeUnit.MINUTES);
            
            // 5. 更新状态为SUCCESS
            taskMapper.updateSuccess(taskId, content.length(), new Date());
            
        } catch (Exception e) {
            // 6. 更新状态为FAILED
            taskMapper.updateFailed(taskId, e.getMessage(), new Date());
            log.error("文件解析失败: fileId={}", fileId, e);
        }
    }
    
    /**
     * 查询解析状态
     */
    public ParseStatusDTO getParseStatus(String fileId, Long userId) {
        FileParseTask task = taskMapper.selectByFileIdAndUserId(fileId, userId);
        if (task == null) {
            throw new BusinessException("文件不存在");
        }
        
        return convertToDTO(task);
    }
    
    /**
     * 获取已解析的文件内容
     */
    public String getParsedContent(String fileId, Long userId) {
        // 1. 校验文件归属
        FileParseTask task = taskMapper.selectByFileIdAndUserId(fileId, userId);
        if (task == null) {
            throw new BusinessException("文件不存在");
        }
        
        // 2. 校验解析状态
        if (!"SUCCESS".equals(task.getParseStatus())) {
            throw new BusinessException("文件尚未解析成功，当前状态：" + task.getParseStatus());
        }
        
        // 3. 从Redis获取内容
        String cacheKey = "file:parsed:" + fileId;
        String content = (String) redisTemplate.opsForValue().get(cacheKey);
        
        if (content == null) {
            throw new BusinessException("文件内容已过期，请重新上传");
        }
        
        return content;
    }
}
```

---

### 四、前端对接流程

```javascript
// 1. 用户上传文件（最多8个）
const formData = new FormData();
for (let file of selectedFiles) {
  formData.append('files', file);
}

const uploadRes = await fetch('/v1/file/upload', {
  method: 'POST',
  headers: { 'X-User-Token': token },
  body: formData
});

const { files } = (await uploadRes.json()).data;
// files = [{ fileId: 'abc123', fileName: 'report.pdf', status: 'PENDING' }, ...]

// 2. 轮询查询解析状态
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

files.forEach(file => checkStatus(file.fileId));

// 3. 用户输入问题并发送
const sendQuestion = async () => {
  const response = await fetch('/v1/chat/stream-with-files', {
    method: 'POST',
    headers: {
      'X-User-Token': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      conversationId: currentConvId,
      model: selectedModel,
      question: userInput,
      fileIds: files.map(f => f.fileId)
    })
  });
  
  // 4. 处理SSE流
  const reader = response.body.getReader();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    // 解析并渲染SSE事件
  }
};
```

---

## 📊 改造优势

| 特性       | 改造前      | 改造后                            |
|----------|----------|--------------------------------|
| **文件上传** | 单文件，同步解析 | 批量（最多8个），异步解析                  |
| **用户体验** | 大文件等待时间长 | 立即返回fileId，后台解析                |
| **状态管理** | 无明确状态    | PENDING→PARSING→SUCCESS/FAILED |
| **可追溯性** | 无记录      | 数据库完整记录                        |
| **灵活性**  | 解析和对话耦合  | 解耦，可单独取消/重试                    |
| **性能**   | 阻塞主线程    | 异步线程池处理                        |

---

## 🚀 实施步骤

### Phase 1: 数据库和基础服务（✅ 已完成）

1. ✅ 创建`file_parse_task`表 - rebuild.sql已更新
2. ✅ 实现`FileParseTaskMapper` - 已创建
3. ✅ 配置异步线程池 - AsyncConfig已添加fileParseExecutor

### Phase 2: 核心服务层（✅ 已完成）

1. ✅ 实现`FileParseService` - 已创建
2. ✅ 实现文件解析逻辑（复用AiDocService的parseFileToText）
3. ✅ 实现Redis缓存策略

### Phase 3: Controller接口（✅ 已完成）

1. ✅ 实现`POST /v1/file/upload` - FileUploadController已创建
2. ✅ 实现`GET /v1/file/parse-status/{fileId}`
3. ✅ 实现`DELETE /v1/file/{fileId}`
4. ✅ 实现`POST /chat/stream-with-files` - ChatController已添加

### Phase 4: 清理旧接口（⏳ 待完成）

1. ⏳ 标记`/api/chat/with-file`为@Deprecated
2. ⏳ 标记`/v1/ai-doc/upload`为@Deprecated
3. ⏳ 保留`/v1/ai-doc/chat/stream`作为兼容接口

### Phase 5: 前端对接（⏳ 待完成）

1. ⏳ 实现批量上传组件
2. ⏳ 实现状态轮询逻辑
3. ⏳ 集成流式对话

---

## ⚠️ 注意事项

1. **异步线程池配置**：
   ```yaml
   spring:
     task:
       execution:
         pool:
           core-size: 5
           max-size: 20
           queue-capacity: 100
   ```

2. **文件清理策略**：
    - 定时任务清理7天前的未使用文件
    - OSS设置生命周期规则自动删除

3. **并发控制**：
    - 单用户同时解析文件数限制（最多5个）
    - 全局解析队列限流

4. **错误重试**：
    - 解析失败允许用户重新触发
    - 最多重试3次

---

**文档版本**: v1.0  
**创建时间**: 2026-05-17  
**维护者**: 后端开发团队
