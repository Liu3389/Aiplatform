# AI 开发任务清单 - AI Gateway Platform

> 📋 本文档列出前后端需要完成的任务，AI 助手根据此文档实施

---

## 🎨 前端技术选型要求

### 核心要求：使用 Pretext 无 DOM 布局引擎

**为什么需要 Pretext？**

在 AI 流式输出场景下，传统 DOM 渲染存在严重性能问题：

| 问题        | 传统 DOM                    | Pretext 方案         |
|-----------|---------------------------|--------------------|
| **高频重排**  | 每接收一个字符就触发 DOM 更新和重排      | 虚拟渲染，批量更新          |
| **长文本卡顿** | 超过 1000 字明显卡顿，5000 字几乎不可用 | 10 万字流畅渲染          |
| **内存占用**  | 大量 DOM 节点占用数百 MB 内存       | 仅渲染可视区域，内存占用降低 90% |
| **帧率**    | 流式输出时帧率降至 10-20 FPS       | 稳定保持 60 FPS        |
| **首屏时间**  | 长对话加载缓慢                   | 即时显示，按需加载          |

**推荐技术栈：**

| 类别           | 推荐方案                      | 说明                 |
|--------------|---------------------------|--------------------|
| **框架**       | React 18+ / Vue 3         | 组件化开发              |
| **状态管理**     | Zustand / Pinia           | 轻量级状态管理            |
| **路由**       | React Router / Vue Router | 前端路由               |
| **UI 库**     | Ant Design / Element Plus | 企业级 UI 组件          |
| **HTTP 客户端** | Axios / Fetch API         | 请求封装               |
| **流式渲染**     | **Pretext**               | **无 DOM 布局引擎（必须）** |
| **代码高亮**     | Prism.js / Highlight.js   | 代码语法高亮             |
| **Markdown** | marked / markdown-it      | Markdown 解析        |
| **类型检查**     | TypeScript                | 类型安全               |

---

## 🚨 P0 紧急修复：后端密码加密问题

### ❌ 当前问题

项目目前使用的是 **MD5 加密**，不是 BCrypt！

### ✅ 需要做什么

#### 任务1：升级密码加密从 MD5 到 BCrypt

**工作内容：**

1. 确认 pom.xml 中有 spring-boot-starter-security 依赖
2. 修改 UserService.java：
    - 删除 SecureUtil 导入，添加 BCryptPasswordEncoder 导入
    - 添加 BCryptPasswordEncoder 字段
    - register() 方法：将 MD5 加密改为 BCrypt 加密
    - login() 方法：将 MD5 验证改为 BCrypt 验证
3. 更新数据库中的测试密码（非常重要！）：
    - 生成 BCrypt 加密的密码哈希值
    - 创建 SQL 脚本更新所有测试账号的密码
    - 执行 SQL 到数据库
4. 更新 testdata.sql 文件中的密码字段

**注意事项：**

- BCrypt 和 MD5 生成的哈希值完全不同，必须更新数据库
- BCrypt 每次生成的哈希值都不同（因为随机盐值），这是正常的
- 使用 passwordEncoder.matches() 验证，不要用 equals

---

## 📋 P1 高优先级任务

### 后端任务

#### 任务2：添加用户角色字段

**原因：** 目前没有角色字段，无法区分普通用户和管理员

**工作内容：**

1. 修改 User.java 实体类，添加 role 字段
2. 创建 SQL 脚本：
    - ALTER TABLE 添加 role 列
    - UPDATE 设置现有用户的角色（superadmin=SUPER_ADMIN, zhangsan/devteam=ADMIN, 其他=USER）
3. 执行 SQL 脚本到数据库
4. 更新 testdata.sql 文件，添加 role 字段

---

#### 任务3：实现 Token 认证机制

**当前问题：** AuthController 中 Token 只是简单拼接字符串 "token_" + userId，太简单了

**工作内容：**

1. 在 UserService 中注入 StringRedisTemplate
2. 修改 login() 方法：
    - 生成 UUID 作为 Token
    - 将 Token 存储到 Redis，设置过期时间（24小时）
    - 将用户角色缓存到 Redis
3. 在 Constants.java 中添加 Token 相关常量
4. 确保拦截器能从 Redis 验证 Token

---

#### 任务4：创建管理员后台

**原因：** 目前完全没有管理员功能，无法管理系统

**工作内容：**

1. 创建 AdminController.java，包含以下接口：
    - GET /admin/stats - 获取系统统计信息（用户数、调用次数、收入等）
    - GET /admin/users - 查询用户列表（分页）
    - PUT /admin/users/{id}/status - 禁用/启用用户
    - POST /admin/users/{id}/recharge - 管理员为用户充值
    - POST /admin/users/{id}/free-strategy - 设置用户免费策略
    - GET /admin/call-logs - 查询调用日志（可筛选）
    - GET /admin/billing-records - 查询计费记录

2. 创建 AdminService.java，实现业务逻辑：
    - 验证管理员权限（检查角色是否为 ADMIN 或 SUPER_ADMIN）
    - 实现各个接口的业务逻辑
    - 记录操作日志

3. 创建 SystemStatsVO.java，用于返回系统统计信息

**关键点：**

- 所有接口都需要验证 X-User-Id Header
- 验证用户角色是否为管理员
- 涉及余额变更的操作必须加 @Transactional
- 记录所有管理员操作日志

---

### 前端任务

#### 任务5：搭建前端项目基础架构

**工作内容：**

1. 初始化 React/Vue 项目（使用 Vite）
2. 安装必要依赖：
    - 路由库（React Router / Vue Router）
    - 状态管理（Zustand / Pinia）
    - UI 组件库（Ant Design / Element Plus）
    - HTTP 客户端（Axios）
    - **Pretext 流式渲染库**
    - Markdown 解析器
    - 代码高亮库
3. 配置项目结构：
    - src/components（公共组件）
    - src/pages（页面组件）
    - src/services（API 调用）
    - src/store（状态管理）
    - src/utils（工具函数）
4. 配置路由和布局

---

#### 任务6：实现用户认证模块

**工作内容：**

1. 创建登录页面：
    - 用户名/密码输入框
    - 登录按钮
    - 错误提示
2. 创建注册页面：
    - 用户名/密码/邮箱输入框
    - 注册按钮
    - 表单验证
3. 实现认证服务：
    - 登录 API 调用
    - 注册 API 调用
    - Token 存储（localStorage/sessionStorage）
    - Token 过期处理
4. 实现路由守卫：
    - 未登录用户跳转到登录页
    - 已登录用户访问登录页跳转到首页

---

#### 任务7：实现 AI 对话界面（核心功能）

**工作内容：**

1. 创建聊天页面布局：
    - 左侧：会话列表
    - 中间：对话区域
    - 右侧：模型选择/设置（可选）
2. 实现消息列表组件：
    - 用户消息（右对齐）
    - AI 消息（左对齐，使用 Pretext 渲染）
    - 支持 Markdown 格式
    - 支持代码高亮
3. 实现输入框组件：
    - 多行文本输入
    - 发送按钮
    - 停止生成按钮
    - 字数统计
4. 实现流式对话逻辑：
    - 使用 Fetch API 或 EventSource 接收 SSE 流
    - 实时追加内容到 Pretext 渲染器
    - 处理连接中断和错误
    - 显示加载状态
5. 实现会话管理：
    - 新建会话
    - 切换会话
    - 删除会话
    - 会话历史持久化

**关键技术要求：**

- **必须使用 Pretext 渲染 AI 回复内容**
- 流式数据解析（SSE 格式）
- 自动滚动到底部
- 支持中断生成

---

#### 任务8：实现用户个人中心

**工作内容：**

1. 创建个人中心页面：
    - 用户信息显示
    - 余额显示
    - 充值入口
2. 实现充值功能：
    - 充值金额输入
    - 支付流程（可先模拟）
    - 充值成功提示
3. 实现 API Key 管理：
    - API Key 列表展示
    - 生成新 API Key
    - 复制 API Key
    - 禁用/启用 API Key
    - 删除 API Key
    - 查看 API Key 使用统计
4. 实现调用历史查询：
    - 调用记录列表（分页）
    - 筛选功能（按日期、模型等）
    - 查看详情（请求参数、响应、费用等）

---

### 前后端对接任务

#### 任务9：定义 API 接口规范

**工作内容：**

1. 统一响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1234567890
}
```

2. 统一错误处理：
    - 前端拦截器处理常见错误码
    - 友好的错误提示
    - Token 过期自动跳转登录

3. 请求拦截器：
    - 自动添加 Token 到请求头
    - 处理请求loading状态

4. 响应拦截器：
    - 统一处理错误码
    - Token 过期处理
    - 网络错误处理

---

## 📊 P2 中优先级任务

### 后端任务

#### 任务10：添加分页功能

**工作内容：**

1. ApiKeyController 的列表接口添加分页参数
2. ApiKeyService 实现分页查询
3. 返回 Page 对象而不是 List

---

#### 任务11：添加密码修改功能

**工作内容：**

1. 在 AuthController 中添加接口：
    - POST /auth/change-password - 修改密码（需要旧密码）
    - POST /auth/reset-password - 重置密码（管理员操作）
2. 创建 ChangePasswordRequest DTO
3. 在 UserService 中实现密码修改逻辑

---

### 前端任务

#### 任务12：实现管理员后台界面

**工作内容：**

1. 创建管理员专属布局：
    - 侧边栏导航
    - 顶部标题栏
    - 主内容区
2. 实现系统概览页面：
    - 统计数据卡片（用户数、调用次数、收入等）
    - 图表展示（用户增长趋势、调用量趋势等）
3. 实现用户管理页面：
    - 用户列表表格（分页）
    - 搜索/筛选功能
    - 禁用/启用用户
    - 管理员充值
    - 设置免费策略
4. 实现调用日志页面：
    - 日志列表（分页）
    - 筛选功能（用户、日期、模型等）
    - 查看详情
5. 实现计费记录页面：
    - 计费记录列表
    - 统计报表
    - 导出功能

---

#### 任务13：优化用户体验

**工作内容：**

1. 添加加载状态：
    - 页面加载骨架屏
    - 按钮 loading 状态
    - 列表加载动画
2. 添加空状态提示
3. 添加错误边界处理
4. 实现响应式设计（适配移动端）
5. 添加主题切换（明暗模式）
6. 优化性能：
    - 路由懒加载
    - 组件懒加载
    - 图片懒加载
    - 虚拟滚动（长列表）

---

## 🐳 Docker 部署任务

### 任务14：后端 Docker 化

**工作内容：**

1. 创建 Dockerfile：
    - 使用多阶段构建
    - 第一阶段：Maven 编译
    - 第二阶段：JRE 运行
2. 创建 docker-compose.yml：
    - MySQL 服务配置
    - Redis 服务配置
    - Application 服务配置
    - 网络和数据卷配置
3. 创建 .dockerignore 文件
4. 创建一键部署脚本 deploy.sh
5. 创建停止脚本 docker-stop.sh

---

### 任务15：前端 Docker 化（可选）

**工作内容：**

1. 创建前端 Dockerfile：
    - 构建阶段：Node.js 环境编译
    - 运行阶段：Nginx 静态服务
2. 配置 Nginx：
    - 静态文件服务
    - 反向代理到后端 API
    - 支持 SPA 路由
3. 集成到 docker-compose.yml

---

## 🎯 实施顺序建议

### 第一阶段：后端基础修复（1-2天）

1. ✅ 升级 MD5 → BCrypt
2. ✅ 更新数据库测试密码
3. ✅ 测试登录注册功能

### 第二阶段：后端功能完善（2-3天）

4. ✅ 添加用户角色字段
5. ✅ 实现 UUID + Redis Token 认证
6. ✅ 创建管理员后台

### 第三阶段：前端基础搭建（2-3天）

7. ✅ 搭建前端项目架构
8. ✅ 实现用户认证模块
9. ✅ 实现 API 接口封装

### 第四阶段：前端核心功能（3-5天）

10. ✅ 实现 AI 对话界面（重点：Pretext 集成）
11. ✅ 实现用户个人中心
12. ✅ 实现会话管理

### 第五阶段：前后端联调（1-2天）

13. ✅ 接口对接
14. ✅ 错误处理优化
15. ✅ 用户体验优化

### 第六阶段：高级功能（2-3天）

16. 实现管理员后台前端
17. 添加分页功能
18. 添加更多优化功能

### 第七阶段：部署上线（1天）

19. Docker 化打包
20. 部署到测试环境
21. 压力测试和优化

---

## ⚠️ 注意事项

### 后端注意事项

1. **数据库变更顺序**
   ```
   1. 先修改 Entity 类
   2. 再执行 ALTER TABLE SQL
   3. 更新 testdata.sql
   4. 重启应用
   ```

2. **BCrypt 重要提醒**
    - BCrypt 每次生成的哈希值都不同（因为随机盐值）
    - 必须先更新数据库密码，否则现有用户无法登录
    - 使用 passwordEncoder.matches() 验证，不要用 equals

3. **权限验证**
   所有管理员接口必须：
    - 验证 X-User-Id Header 存在
    - 验证用户是 ADMIN 或 SUPER_ADMIN
    - 验证用户状态正常
    - 记录操作日志

4. **事务管理**
   涉及余额变更的操作必须加 @Transactional

5. **代码规范**
    - 保持代码风格一致，参考现有代码
    - 遵循分层架构：Controller → Service → Mapper
    - 使用 Lombok 注解
    - 统一响应格式：Result.success() 和 Result.error()
    - 抛出自定义 BusinessException
    - 每个类、方法都要有 JavaDoc 注释

---

### 前端注意事项

1. **Pretext 集成要点**
    - 必须使用 Pretext 渲染 AI 流式输出
    - 配置合适的渲染策略（incremental/virtual-scroll）
    - 启用代码高亮和 Markdown 支持
    - 处理长文本的性能优化

2. **SSE 流式处理**
    - 正确处理 SSE 数据格式（data: 开头）
    - 处理连接中断和重连
    - 实现取消/中断生成
    - 显示实时加载状态

3. **状态管理**
    - 合理划分 Store（用户状态、会话状态、聊天状态等）
    - 避免不必要的重渲染
    - 持久化重要状态（Token、会话列表等）

4. **错误处理**
    - 统一的错误提示机制
    - Token 过期自动跳转登录
    - 网络错误重试机制
    - 友好的错误提示文案

5. **性能优化**
    - 路由和组件懒加载
    - 虚拟滚动（长列表）
    - 防抖节流（搜索、输入等）
    - 图片懒加载
    - 合理使用 useMemo/useCallback

6. **响应式设计**
    - 适配桌面端和移动端
    - 触摸友好的交互
    - 合理的断点设置

---

## 🧪 测试清单

### 后端测试

#### BCrypt 升级测试

- [ ] 新用户注册（密码会被 BCrypt 加密）
- [ ] 新用户登录（验证 BCrypt 密码）
- [ ] 旧用户登录（如果数据库未更新，会失败）

#### Token 认证测试

- [ ] 登录获取 Token
- [ ] 使用 Token 访问受保护接口
- [ ] Token 过期后访问（应该失败）

#### 管理员功能测试

- [ ] 管理员登录
- [ ] 获取系统统计
- [ ] 查询用户列表
- [ ] 禁用/启用用户
- [ ] 管理员充值
- [ ] 设置免费策略

---

### 前端测试

#### 认证模块测试

- [ ] 注册功能
- [ ] 登录功能
- [ ] Token 持久化
- [ ] Token 过期跳转
- [ ] 路由守卫

#### 对话功能测试

- [ ] 发送消息
- [ ] 接收流式响应
- [ ] Pretext 渲染效果
- [ ] 代码高亮
- [ ] Markdown 渲染
- [ ] 中断生成
- [ ] 会话切换
- [ ] 新建会话
- [ ] 删除会话

#### 个人中心测试

- [ ] 查看余额
- [ ] 充值功能
- [ ] API Key 管理
- [ ] 调用历史查询

#### 管理员后台测试

- [ ] 系统统计展示
- [ ] 用户管理
- [ ] 调用日志查询
- [ ] 计费记录查询

#### 兼容性测试

- [ ] Chrome
- [ ] Firefox
- [ ] Safari
- [ ] Edge
- [ ] 移动端浏览器

---

## 🚀 快速开始

想让 AI 开始工作，这样说：

**后端任务：**

```
请按照 AI_DEVELOPMENT_TASKS.md 完成 P0 紧急修复任务
```

**前端任务：**

```
请按照 AI_DEVELOPMENT_TASKS.md 完成前端任务5：搭建前端项目基础架构
```

**特定任务：**

```
请按照 AI_DEVELOPMENT_TASKS.md 完成任务7：实现 AI 对话界面
```

---

## 📚 参考资料

### 后端

- Spring Boot 官方文档
- MyBatis-Plus 官方文档
- Redis 官方文档
- BCrypt 加密原理

### 前端

- React/Vue 官方文档
- Pretext 官方文档和示例
- SSE (Server-Sent Events) 规范
- Ant Design / Element Plus 文档

### DevOps

- Docker 官方文档
- Docker Compose 使用指南
- Nginx 配置指南

---

**最后提醒：**

- ⚠️ BCrypt 升级是最紧急的，关系到系统安全
- ⚠️ Pretext 是前端核心要求，必须正确使用
- ⚠️ 每完成一个任务，记得编译检查、Apifox 测试、查看日志
- ✅ 完成后提交 Git

祝开发顺利！🚀
