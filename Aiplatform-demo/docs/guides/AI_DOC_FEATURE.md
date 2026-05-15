# AI 文档助手与云端同步功能指南

## 🚀 功能概述
该功能增强了现有的 AI 对话系统，支持在对话中直接处理多种格式的文件，并实现了基于阿里云 OSS 的云端数据同步与管理。

### 核心特性：
1. **多格式支持**：上传 `.txt`, `.md`, `.java`, `.py`, `.cpp`, `.xlsx`, `.docx`, `.html`, `.css` 等文件。
2. **Markdown 统一输出**：AI 回答统一采用 Markdown 格式，前端可自动渲染代码块、表格及 Mermaid 流程图。
3. **云端存储**：所有用户上传文件、AI 生成文件均存入阿里云 OSS。
4. **数据同步**：支持将对话历史打包备份至云端，并可随时下载恢复。

## ⚙️ 环境配置
在 `application.yml` 中完成以下配置：

```yaml
aliyun:
  oss:
    endpoint: oss-cn-wuhan-lr.aliyuncs.com
    access-key-id: 你的AccessKeyId
    access-key-secret: 你的AccessKeySecret
    bucket-name: my-ai-platform-docs
    domain: https://my-ai-platform-docs.oss-cn-wuhan-lr.aliyuncs.com
```

## 📡 核心接口说明

| 接口路径 | 方法 | 说明 |
| :--- | :--- | :--- |
| `/api/chat/with-file` | POST | 对话中发送文件，AI 解析后返回 Markdown 回答 |
| `/api/chat/generate-file` | POST | 让 AI 生成指定格式的代码或文档文件 |
| `/api/chat/sync` | POST | 将当前对话数据打包同步至阿里云 OSS |
| `/api/chat/download` | GET | 从云端下载已备份的对话数据包 (ZIP) |
| `/api/chat/load` | GET | 加载对话时获取关联文件的云端 URL |

## 🛠️ 技术实现细节
*   **文件解析**: 使用 Apache POI 处理 Office 文档，Commonmark 处理 Markdown。
*   **安全存储**: 文件按用户 ID 隔离存储在 OSS 的不同目录下。
*   **AI 交互**: 强制注入 System Prompt，确保 AI 输出符合前端渲染规范的 Markdown 内容。
