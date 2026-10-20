# AI 平台问题整改计划

本计划把现有代码审计发现的 19 项问题转成可逐项验收的开发清单，并纳入本地验收额外发现的 SSE、聊天页包体、本地运行器重启和模型接入状态问题。整改以 AI 对话安全、计费正确性和请求可靠性为主；每项先在隔离本地环境实现并完成对应测试，验收记录齐全后才进入线上发布流程。当前文档记录的是本地整改状态，不代表这些变更已经发布线上。

## 开发与发布门禁

1. 每个问题单独实现，补充可复现的回归测试；涉及数据库时使用本地迁移和独立本地数据，不在测试中连接线上数据库。
2. 本地门禁按改动选择：单元测试、集成测试、HTTP 接口测试、浏览器功能验证、模拟上游模型测试、故障/重放测试。只编译成功不能算接口或功能验收通过。
3. AI 上游调用先用本地 OpenAI 兼容模拟服务验证请求、流式/非流式响应、失败回滚和计费；真实模型凭据、RabbitMQ、OSS 等外部依赖另列联调门禁，未验证的能力不得标记为已验收。
4. 一项功能的实现、回归测试和验收记录全部通过后，才允许将该项提交到线上发布候选。线上发布仍需逐项核对迁移、配置、回滚方案与监控；本计划不授权自动部署。

## 需要凭据所有者配置或人工核验的事项

以下列出仍需完成的真实依赖或发布前人工核验；具体完成状态以各整改批次的证据为准。未完成相应门禁前不得开放线上功能或发布。凭据放入权限受限的本地/服务器环境文件或密钥管理器，不要提交 Git，也不要粘贴到聊天。

- **Google OIDC 沙箱与生产门禁**：本地开发项目已创建 Web OAuth 客户端并完成真实登录验收；本地凭据仅保存在被 Git 忽略且权限为 `600` 的 `.runtime/local.env`。独立生产项目 `lingdian-ai-production` 已创建；2026-10-19 经用户授权后，品牌显示名为“凌点 AI”，受众为“外部/测试”，用户指定的 Google 账号已登记为唯一测试用户，授权域名为 `lingdian-ai.top`，首页为 `https://lingdian-ai.top/welcome`。用户已创建并附上生产 Web 客户端 JSON；脱敏核验确认项目正确，但当前授权来源额外包含 HTTP 域名，回调登记为站点根路径而非应用的 `/oauth/callback`，因此不得写入服务器。用户本机附件副本权限为 `600`，密钥没有输出到聊天。修正来源/回调并重新导出后，才可写入服务器 `.env.prod` 的 `GOOGLE_OAUTH_CLIENT_ID`、`GOOGLE_OAUTH_CLIENT_SECRET`、`GOOGLE_OAUTH_REDIRECT_URI`；线上授权入口仍返回业务码 503。安全配置加载路径已加入 `application.yml`（可从容器内 `/app/google-oauth.properties` 读取）并推送到 `main` 提交 `f028fe7`，但尚未部署。隐私/服务条款页面与运营主体信息仍未完成正式发布，因此目前仅限测试用户登录，不代表可向公众发布或通过 OAuth 审核。
- **DeepSeek 上游**：现有配置凭据已通过官方 `GET /models` 鉴权，并完成本地非流式与 SSE 真实聊天验收；无需为本地联调申请新 Key。凭据仅存于权限 `600` 的 Git 忽略文件 `.runtime/local.env`。用户已授权清理本地 `main` 历史；当前 `main` 与 `origin/main` 同步，推送扫描通过。本地回滚引用仍保留重写前对象；没有撤销或轮换现有 Key。线上数据库中的模型 Key 未在本轮读取；生产上游仍需单独进行脱敏联调验收。
- **邮件与短信**：真实邮件需配置 SMTP `SPRING_MAIL_*`；真实短信需配置 `VERIFICATION_DELIVERY_MODE=external`、`VERIFICATION_SMS_URL`、`VERIFICATION_SMS_TOKEN`、`VERIFICATION_SMS_TEMPLATE_ID`，之后分别验收投递与验证绑定。
- **真实 RabbitMQ 与 OSS**：部署环境需配置 `RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`、`RABBITMQ_VHOST`，并联调队列、监控和重启恢复；OSS 使用 `ALIYUN_OSS_ENDPOINT`、`ALIYUN_OSS_ACCESS_KEY_ID`、`ALIYUN_OSS_ACCESS_KEY_SECRET`、`ALIYUN_OSS_BUCKET`、`ALIYUN_OSS_DOMAIN`。若要实际使用 OSS，还需将 `APP_STORAGE_PREFER_LOCAL=false`；模板默认 true 会继续优先本地存储。本地 RabbitMQ 与本地文件存储已各自验证，不能替代生产联调。
- **线上发布配置**：最新只读核验发现 `MYSQL_PASSWORD`、`SECONDARY_PASSWORD` 已设置而 `REDIS_PASSWORD` 为空。用户明确选择保持现有 Redis/backend 配置，暂不启用 Redis 认证；因此不要求现在设置 Redis 密码，也不为此重启 Redis。使用旧 `server-deploy.sh`/Compose 的镜像发布仍会因严格 Redis 密码门禁拒绝；当前新增的原容器内 JAR/静态文件发布流程不使用 Compose，不新建镜像/容器。其他服务器必需密码不需要复制到开发机或聊天中。以后执行受控发布需设置 `AI_PLATFORM_DEPLOY_HOST` 和 `AI_PLATFORM_DEPLOY_SSH_KEY`。
- **历史数据/凭据人工核验**：上线前需按 B3、B5、C1-C4、E1 各节盘点旧 Redis 结算快照、临时赠点、伪 TOTP/OAuth 数据、旧 Token、历史联系方式与已暴露密钥；涉及用户余额、身份或联系方式的迁移/冲销不能自动猜测。
- **支付**：按用户决定暂不接支付接口；直充路径继续关闭，待以后另选渠道并单独验收。

## 整改批次

### 用户要求优先项

- [x] **登录图形验证码（优先实现）**：登录页加载可刷新、5分钟有效、一次消费的图形验证码；登录 API 强制校验，并与注册验证码分用途隔离。验收：错误/过期/重放验证码拒绝且不进入账号密码校验；有效验证码可正常登录；本地浏览器与接口冒烟通过。2026-10-19 复验：登录验证码接口返回图片 data URI、有效期 300 秒；错误验证码返回业务码 400；验证码失败时不调用账号或 Token 服务。追加防刷门禁：登录/注册验证码共用每 IP 每分钟 10 次的 Redis 原子计数，超限返回 HTTP 429；限流身份使用 Nginx 覆写的 `X-Real-IP`，忽略客户端可控的 `X-Forwarded-For`。前端保留后端错误正文，登录页可显示限流提示。`IpRateLimitInterceptorTest` 覆盖超限拒绝、转发头伪造和其他认证路由；与登录/验证码服务、用户限流共 13 项定向测试通过。全量本地预检 108 项 Surefire 测试、0 失败/错误、2 项 RabbitMQ opt-in 跳过，前端生产构建和模拟聊天/SSE/结算回归通过；错误提示补充 `npm run test:api-errors`（2 项通过）后再次 `npm run build`、本地 `verify` 均通过。

### 本地运行复核新增项

- [x] **模型接入状态与聊天可用性（本地审计新增）**：`/platform-model/list` 与 `/platform-model/available` 用不含凭据的 `chatEnabled` 布尔字段反映 provider、实际模型、上游地址和 API Key 是否配置齐全；聊天页、设置面板与模型商城禁止选择未接入模型并说明原因；首页只展示已接入模型且使用真实 `displayName/provider` 契约。后端对缺少上游配置的请求在预扣和调用上游前拒绝。验收：接口不泄露密钥；用户模型列表正确返回接入状态；本地 mock 可正常聊天，未配置模型不会误选、不会扣点或调用上游。
- [x] **Google 法律页路由缺失（本地运行复核新增）**：此前 `/privacy`、`/terms` 未注册，未知路径被重定向到 `/welcome`。现已新增两条公开路由、基于项目实际能力撰写的本地政策草案及欢迎页真实链接；前端构建与本地浏览器可见文本验收通过。页面明确显示草案提示，尚未部署。
- [x] **账号停用语义与行为统一（隐私审计新增）**：账号设置已明确执行“停用”，停用后禁止继续登录/使用 API 并撤销所有会话，同时保留账号资料、联系方式、第三方身份、对话、文件与账务数据；前后端文案及本地法律草案与行为一致。服务回归测试验证资料未变且会话失效；删除数据的范围仍单独待确认。
- [ ] **账号数据删除范围与流程待定（隐私审计新增）**：用户已决定先停用账号，账号数据删除范围稍后确认。在范围、法定账务留存期限与申请处理流程经运营方确认前，不执行资料匿名化、身份解绑或内容/账务清除，也不将账号停用描述为注销或删除。
- [x] **可选 RabbitMQ 令 Actuator 健康误报（运行复核新增）**：`RABBITMQ_ENABLED=false` 时 Spring Boot 仍探测默认 RabbitMQ 并使 `/api/actuator/health` 返回 503；已将 Rabbit 健康指示器绑定到 `app.messaging.rabbit-enabled`，并让本地代理/后端验收断言 `status=UP`。2026-10-19 `./scripts/local-release-preflight.sh --local-only` 通过：34 个 Surefire suite、101 项测试、0 失败/错误、2 项 Rabbit opt-in 跳过；前端生产构建、验证码与模拟聊天/SSE/结算回归通过；结束后 `./scripts/ai-local-env.sh verify` 再次确认后端与前端代理健康为 UP。Rabbit 显式启用时仍须检查真实 Broker。
- [x] **生产 CORS 通配符风险（运行复核新增）**：线上 backend 原容器环境注入 `ALLOWED_ORIGINS=*` 且凭据跨域已启用；后端现拒绝通配符并降级到 `https://lingdian-ai.top`、`https://www.lingdian-ai.top`，生产环境校验器拒绝任意含 `*` 的来源。`CorsConfigTest` 3 项、生产配置正反向门禁、本地全量预检和本地/公网 OPTIONS 验收均通过；陌生来源均无 ACAO 并返回 403。已备份权限 `600` 的旧 JAR 与 `.env.prod`，将服务器 `.env.prod` 收紧为两个正式 HTTPS 来源；新 JAR 已在原 `lingdian-backend` 容器内替换并通过 SHA-256、健康、登录验证码和 CORS 验收。现有容器 ID 未变、没有创建镜像/容器；因容器环境仍保留旧 `*`，新代码运行时会安全降级并记录告警。

### 批次 A：AI 请求边界与模型访问安全（最高优先级）

- [x] **A1 文件路径越权读取（P1-01）**：在 `OssService.resolveLocalPath` 对规范化后的真实路径做上传根目录边界校验；拒绝 `..`、编码绕过、符号链接越界和绝对路径。验收：合法文件可读；上传根目录外的哨兵文件无法通过聊天图片或文件接口读取；覆盖 Unix/Windows 分隔符与符号链接用例。
- [x] **A2 会话归属校验（P1-04）**：复用 `conversationId` 前验证会话存在且归属当前用户；不存在或属于他人的会话必须拒绝，且不得写入消息、触发模型调用或扣点。验收：本人续聊成功；跨用户 IDOR 回归用例失败关闭；数据库、点数和上游 mock 请求数均不发生副作用。
- [x] **A3 模型权限失败关闭（P1-10）**：订阅/权限服务异常时拒绝需要会员等级的模型调用，不允许 catch 后放行。验收：免费用户、高等级用户、权限库超时/异常分别覆盖；异常场景没有模型请求和预扣点。
- [x] **A4 用户聊天限流路径（P2-18）**：让 `/chat/**` 的用户令牌请求经过预期的用户级限流器，明确区分平台 API Key 与用户 Token。验收：同一用户超过阈值返回约定限流响应；不同用户互不串限；API Key 限流回归不变。已修复 context-path 导致 `/api/**` 不匹配的配置错误；用户限额可配置，API Key 仍只走按 key 限流。
- [x] **A5 测试数据管理端点授权（P1-03）**：为 `/admin/test-data/**` 增加管理员授权，并检查实际生效的拦截器/注解边界。验收：未登录和普通用户均不能读写缓存；管理员可按授权操作；审计日志记录操作者。四个端点逐个标注 `@RequireAdmin`；缓存 JSON 使用注入的 JavaTime `ObjectMapper`，修复管理员初始化接口的 `LocalDateTime` 序列化失败。

### 批次 B：点数账本、预扣与结算一致性

- [ ] **B1 充值只能由可信支付确认入账（P1-02）**：完整支付订单、验签回调与对账尚未实现，完成前不得开放充值。普通用户直充及优惠券直充已按用户决定关闭并通过本地回归（见下方“B1 直接充值风险止血记录”）；用户决定暂缓支付渠道接入，保留后续扩展边界，不创建自定义验签协议。
- [x] **B2 Redis 余额更新不覆盖预扣（P1-06）**：修复 `addPoints` 中数据库余额覆盖 Redis 预扣余额的问题，统一余额变更的原子顺序和账本来源。验收：并发充值与预扣、已有未结算预扣时余额与流水一致；并发压力回归无丢失更新。
- [x] **B3 结算重放与旧快照保护（P1-07）**：结算快照记录数据库点数版本与原结算时间；重放时只有余额版本未前进才补写该快照，版本已前进则只补账单、不覆盖余额。结算流水 before/after 与实际扣点一致。验收：重复投递、乱序投递、进程重启恢复后余额与账本一致，旧状态不能回滚新状态。
- [x] **B4 预扣崩溃恢复（P1-08）**：在预扣时持久化可恢复的请求状态，覆盖调用上游前、收到完整响应后与结算前的进程崩溃；恢复扫描器按状态幂等退款或恢复 outbox 结算。新预扣记录不设 Redis TTL，旧预扣 key 失效时使用持久化预扣金额恢复。无法确认上游结果的过期请求进入 `MANUAL_REVIEW` 并保留预扣，等待供应商日志对账。
- [x] **B5 每日赠点生命周期（P2-11）**：每日赠点使用稳定业务 ID 幂等发放；逐笔记录临时点数剩余额，按最早过期顺序消费；调度扫描只清理到期临时批次并写 `EXPIRE` 账本，永久赠点与充值不参与过期扣减；失败用户由后续扫描重试。

### 批次 C：认证、身份与用户资料

- [x] **C1 登录二次验证（P1-09）**：使用 RFC 6238 TOTP 接入登录状态机；启用前必须完成验证码确认；备份码仅在确认时生成一次、哈希存储并单次消费；共享密钥使用 `API_ENCRYPTION_KEY` 派生密钥加密保存，并绑定用户身份。验收：未完成 2FA 不能取得完整会话；时间窗口、HTTP 错误码、备份码重放、密钥用户绑定均有测试；密钥不写明文日志。
- [x] **C2 OAuth 真正换取身份（P2-13）**：移除伪造 openId 与占位 client id，先实现 Google OIDC 授权码流程；state 一次消费并绑定 HttpOnly 浏览器 cookie，增加 nonce、PKCE、授权码交换、RS256/JWKS 签名及 issuer/audience/azp/exp/iat 校验；账号身份落库并对提供方 subject 与用户/提供方加唯一约束。OAuth 绑定须经当前用户授权，禁止按邮箱自动合并账号；已有 2FA 账号必须继续完成二次验证。未配置时 Google 返回明确不可用，GitHub/微信占位入口关闭。
- [x] **C3 踢下线撤销 API Token（P2-14）**：将 Token 绑定到 Redis 中的唯一设备会话；替换登录、设备移除、全端退出、账户注销、用户封禁和管理员封禁会撤销对应 Token；普通控制器与 AI 认证过滤器均检查当前绑定会话。验收：踢下线后旧 Token 对普通 API、聊天、流式聊天和 OpenAI 兼容 API 全部失效。
- [x] **C4 邮箱/手机号绑定可验证并持久化（P2-15）**：验证码走 SMTP 邮件或配置的 HTTPS 短信网关；绑定码按用户与目标隔离、限时、哈希存储、限次并原子单次消费；确认后在事务内写入 `user` 资料和唯一的已验证联系方式表。注册时未验证邮箱不再写入资料。
- [x] **C4 前端契约与安全中心可达性（本地审计新增）**：接入安全中心路由和个人中心入口；资料接口明确返回邮箱/手机号是否验证，设备列表按真实 `deviceId/info/lastActive/isCurrent` 字段显示；OAuth、2FA、设备请求失败时显示可重试错误，不伪装成“未绑定/未启用/空列表”；绑定成功刷新资料，验证码冷却按通道区分，充值与余额扣款文案一致。

### 批次 D：AI 对话与异步任务可靠性

- [x] **D1 文件解析任务重启恢复（P2-12）**：恢复超时 `PARSING` 任务；Rabbit 未启用时注册本地扫描器/执行器；任务状态迁移使用租约与 fencing token。worker 异常退出最多重新领取 5 次，之后写入明确 `FAILED` 原因。验收覆盖模拟 worker 退出后的成功恢复、耗尽失败、旧 worker 令牌拒绝，以及 Rabbit 开关两种配置装配。
- [x] **D2 对话持久化失败不能伪装成功（P2-16）**：用户消息、AI 回复、会话计数和 `MESSAGES_PERSISTED` 状态在同一事务提交；写库失败回滚整轮消息并退回预扣；客户端幂等键按用户作用域摘要后存储，同键同请求重放已保存回复，同键不同请求拒绝。恢复扫描只有发现持久化 assistant 回复才结算，无回复则退款。验证：MySQL assistant 插入触发器故障回滚、同键非流式和 SSE 重试各只调用一次模型/写一轮消息/扣一次费、恢复扫描无消息退款和有消息结算；全量 Maven 75 tests 通过，前端 `npm run build` 通过，`verify` 与 `verify-chat` 通过。真实第三方模型线路不在本地模拟验收范围。
- [x] **D3 默认输出 Token 上限（P2-17）**：省略或非正数 `maxTokens` 时使用有界默认值；用户值按套餐上限、模型配置上限与平台硬上限逐层裁剪。模型能力上限通过 `platform_model_config.max_output_tokens` 配置，默认 4096，平台硬上限 32768。验收：省略、负数、超上限和合法值均有序列化/接口测试；OpenAI 兼容及 Anthropic 使用 `max_tokens`，DeepSeek Responses 使用 `max_output_tokens`，最终上游 payload 一定有正整数上限。
- [x] **D4 SSE 异步分派认证上下文（本地验收新发现，P1-20；已本地修复）**：异步响应续派时恢复原请求已认证主体，避免流内容已写出后被 Spring Security 拒绝；续派不得重复消耗 API Key 限流额度。验收：有效 Token/API Key 的流式聊天收到 `message` 与 `done`；无认证请求仍被拒绝；限流器对一次 SSE 请求只计一次。已本地覆盖 Token 与限额为 1 的 API Key 流式用例。

### 批次 E：配置、凭据与部署边界

- [ ] **E1 生产凭据脱离 Git（P1-05）**：本地环境文件未跟踪、被忽略且权限为 `600`；`.env.example` 已从本地 `main` 删除，`.env.prod.example` 通过模板占位符检查；本机暂存/工作区凭据扫描通过。用户已授权并完成本地 `main` 历史脱敏；当前 `main` 与 `origin/main` 同步，推送扫描通过。本地回滚引用仍保留重写前对象；不因格式命中推断密钥当前有效，也未撤销或轮换凭据。服务器 `.env.prod` 为 `0600`；`MYSQL_PASSWORD`、`SECONDARY_PASSWORD` 非空，`REDIS_PASSWORD` 为空，且用户明确选择暂不启用 Redis 认证。Google 测试模式品牌配置已完成，但生产客户端凭据未创建；RabbitMQ、OSS、SMTP/短信生产配置仍为空/未完成联调；服务器凭据不应复制到开发机或聊天中。
- [x] **E2 前端 API 路由不被静态扩展规则截获（P2-19）**：`/api/`、`/chat/`、`/v1/` 已使用 `^~` 前缀优先匹配。临时 Nginx 与 mock 后端验证了带 `.png`、`.pdf`、`.svg`、`.json` 后缀的 API/SSE 请求均转发到后端，SSE 未缓冲，静态资源缓存保持正常；`nginx -t` 通过。
- [x] **E3 聊天页首屏包体过大（本地构建新发现，P3-21）**：聊天页 Markdown、Mermaid、代码高亮、公式渲染已按需加载。构建后 ChatPage 入口为 94.97 KB（gzip 25.97 KB），较整改前约减少 93%；浏览器回归确认 Markdown、代码高亮、KaTeX 和 Mermaid 均正常。ECharts 与其他页面共享 chunk 仍超过 500 KB，但不在 ChatPage 静态依赖图中，作为独立页面优化跟踪。

### 批次 F：本地开发环境可靠性

- [x] **F1 更新运行中 JAR 导致重启异常（本地验收新发现，P2-22）**：本地 runner 必须先停止正在运行的 JVM，再构建并启动新 JAR，避免打包覆盖运行中归档后旧进程在关闭阶段加载类失败。验收：源码变更后执行 `switch local` 可完成停机、构建、启动；日志无 `NoClassDefFoundError`；后端与前端代理健康检查通过。

## 当前环境与验收记录

- 本地开发/冒烟环境由 `docker-compose.local.yml` 和 `scripts/ai-local-env.sh` 管理，使用独立容器、独立数据卷及仅绑定 `127.0.0.1` 的端口。
- 前端代理目标可通过 `./scripts/ai-local-env.sh switch local|online` 切换。当前只验收本地模式；线上模式需由开发者在完成本地工作后主动选择。
- 常用命令：`./scripts/ai-local-env.sh up`、`verify`、`verify-chat`、`switch local|online`、`status`、`down`。`verify-chat` 会在本地数据库创建 `Local Mock (Codex)` 测试模型和临时测试账号；该模型只访问本机模拟服务，存在此模型时 `up` 会同步启动模拟服务。
- 每个整改项合并前在本节补充提交/变更标识、测试命令、结果、外部依赖缺口和回滚方案；没有记录不得进入线上发布候选。

### 本地环境验收记录（2026-10-18）

- [x] MySQL 与 Redis 容器健康；前端 `http://127.0.0.1:13000` 和后端 `http://127.0.0.1:18080/api` 持续运行，工作区原有 3306/6379/8080 服务未被占用或停止。
- [x] `/api/platform-model/list` 后端接口和前端 Vite 代理均返回成功模型列表；响应不含 API Key 字段。未认证聊天请求按现有统一响应协议返回业务码 `401`。
- [x] 用本地 OpenAI 兼容模拟服务完成注册、登录、非流式聊天、本人会话续聊、跨用户拒绝、Token/API Key 两种 SSE 聊天、消息落库和点数结算；最近一次测试账号落库 8 条聊天消息并完成 4 笔结算。
- [x] SSE 异步分派认证缺陷已在本地修复，流式回归收到 `message` 与 `done` 事件；有效请求只在初始分派执行一次 API Key 限流检查。
- [x] `npm run build` 通过；`mvn -q -Dtest=SearchIntentServiceTest,TikaParseTest test` 通过。
- [ ] 生产 RabbitMQ、OSS 与支付渠道仍是独立外部验收门禁。本地 RabbitMQ 4.2.9 已于 2026-10-19 完成本地 broker/恢复验收，不代表生产 broker 已联通；支付接口按用户决定暂缓。Google OIDC 开发沙箱和真实浏览器登录已完成，生产 Google 客户端仍需独立配置、页面审核和验收。
- [x] ChatPage 首屏入口已从约 1.4 MB 降至 94.97 KB；当前构建剩余的大 chunk 属于 ECharts 和其他页面共享依赖，不在聊天页静态依赖图中，按独立页面优化跟踪。
- [x] 2026-10-19 账号停用行为本地验收：提交 `c41d935` 将账号操作统一为停用，保留用户资料与全部历史数据、撤销会话并禁用后续访问；旧 `/user/delete-account` 与新 `/user/deactivate-account` 均保留。`UserServiceTest` 3 项通过，覆盖密码错误不改变状态、正确密码停用、用户名/邮箱/手机号保留和会话撤销。随后 `./scripts/local-release-preflight.sh --local-only` 通过：35 个 Surefire suite 共 105 项，0 失败、0 错误、2 项 Rabbit opt-in 跳过；前端生产构建、本地基础验证及验证码/模拟模型/SSE/结算回归通过。浏览器核对停用文案及空密码按钮禁用状态；接口无效 Token 探测在新旧路径均返回业务码 401。模拟上游验收不代表真实供应商请求；本功能未部署。
- [x] 2026-10-19 已验证的本地提交以普通快进方式推送：`origin/main` 与本地 `main` 均为 `541372b`，GitHub 端历史凭据扫描通过（58 个提交对象）；未强推或部署。

### 真实 DeepSeek 本地上游验收（2026-10-19）

- [x] 复用仓库历史 DeepSeek 配置指南中的现有凭据，官方 `GET /models` 鉴权成功并返回模型元数据；凭据只写入 Git 忽略的 `.runtime/local.env`（权限 `600`），没有回显或新增到代码/提交。后续用户授权重写本地 `main` 已移除其中的格式命中；当前 `origin/main` 与本地 `main` 同步至 `d31d134`，仅本地回滚引用仍保留旧历史；未撤销或轮换。
- [x] 后端 `DEEPSEEK_API_KEY` 环境变量回退使 3 个已有 DeepSeek 模型在脱敏模型列表中标记为可对话，接口未暴露凭据。真实 `DeepSeek-V4-Flash` 非流式与 SSE 请求均经本地前端代理到达供应商并成功；响应消息持久化为 4 条，本地完成 2 笔结算并扣除相应点数。短输出上限为 8 token。
- [x] 因真实 Key 会让 DeepSeek 模型正常可用，修正 `verify-chat` 夹具只从非 DeepSeek 模型中选取“未配置模型”；默认完整回归与 `AI_VERIFY_DEEPSEEK_FALLBACK=true` 合成 Responses mock 全量回归均通过。合成回退测试确认结束后已恢复原模型状态和本地凭据。
- [x] 复验后本地 `verify` 通过：17 个启用模型、4 个可对话、13 个未接入；MySQL、Redis、后端、前端和 mock 均运行，前端代理为 local。

### 登录图形验证码验收（2026-10-18）

- [x] 登录页自动加载登录专用验证码，可点击刷新并输入；注册与登录验证码使用不同 Redis 命名空间，消费由 Redis Lua 脚本原子完成。
- [x] `mvn -q -Dtest=AuthControllerCaptchaTest,CaptchaServiceTest test` 通过；错误验证码不进入账号锁定/密码校验，正确验证码可以建立登录会话，注册验证码不能作为登录验证码使用。
- [x] `npm run build` 通过；本地浏览器确认验证码图片与输入框显示正常。
- [x] `./scripts/ai-local-env.sh verify` 与 `./scripts/ai-local-env.sh verify-chat` 通过；脚本覆盖错误验证码拒绝、错误尝试后的答案重放拒绝、有效验证码登录，以及后续非流式聊天、Token/API Key SSE 和结算。
- [x] 2026-10-19 再次运行 `mvn -q -Dapp.messaging.rabbit-enabled=false -Dtest=CaptchaServiceTest,AuthControllerCaptchaTest test`；两个 Surefire 报告合计 5 项测试、0 失败、0 错误、0 跳过，覆盖验证码错误拒绝、一次消费/重放拒绝、注册与登录验证码隔离、有效验证码建立会话，以及 2FA 挑战不提前签发 Token。
- [x] 只在本地环境验证，未切换线上代理、未部署。

### A1 文件路径边界验收（2026-10-18）

- [x] `mvn -q -Dtest=AuthControllerCaptchaTest,CaptchaServiceTest,OssServicePathTraversalTest test` 通过；覆盖合法存储、Unix/Windows 路径、URL 编码 `..`、越界符号链接和上传写入。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；聊天图片越界引用被拒绝，模拟上游请求数、消息数、结算数和用户 Redis 点数均未增加/改变，随后正常非流式聊天、Token/API Key SSE 和点数结算仍通过。
- [x] 本地路径校验依据真实上传根目录与现存符号链接解析结果；写入在创建目录后再次校验，并禁止跟随目标文件符号链接。

### F1 本地运行器重启验收（2026-10-18）

- [x] 触发后端源码新于 JAR 的分支后执行 `./scripts/ai-local-env.sh switch local`；脚本先停旧 JVM，再构建并启动新进程。
- [x] 本次重启没有强制 KILL 或新的 `NoClassDefFoundError`；重启后 `./scripts/ai-local-env.sh verify` 的 MySQL、Redis、后端 API、未认证边界与前端代理检查全部通过。

### A2 会话归属校验验收（2026-10-18）

- [x] `mvn -q -Dtest=OpenAiChatServiceConversationOwnershipTest test` 通过；覆盖本人有效会话、跨用户、会话不存在、逻辑删除，以及拒绝请求不会访问模型、扣点、写消息或结算。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；本地两账号测试验证本人续聊成功，另一用户对同一 `conversationId` 的非流式请求返回 404、SSE 返回 `error` 并结束。
- [x] 两种跨用户请求均未增加本地模拟上游请求、会话消息、已结算账单或测试用户 Redis 点数；后续正常 Token/API Key SSE 与结算仍通过。

### A3 模型访问权限失败关闭验收（2026-10-18）

- [x] `mvn -q -Dtest=OpenAiChatServiceModelAccessTest test` 通过；免费用户访问受限模型被拒，高等级用户通过权限门后进入点数检查，订阅查询与套餐查询异常均返回 503 并失败关闭。
- [x] SSE 权限依赖异常发送 `error` 终态；权限异常分支没有执行点数预扣、消息写入、结算或模型调用。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；无会员要求的本地模拟模型仍可完成非流式、本人会话续聊、Token/API Key SSE 和结算。

### A4 用户聊天限流验收（2026-10-18）

- [x] `mvn -q -Dtest=UserRateLimitInterceptorTest test` 通过；覆盖超过阈值返回 429、不同用户窗口隔离、API Key 不叠加用户限流、SSE 异步续派不重复计数，以及同一毫秒请求成员唯一性。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；真实 `/api/chat/completions` 在用户窗口达到 60 次后返回 HTTP/业务码 429，拒绝请求未调用模型、写消息/结算或扣点；另一用户聊天成功。
- [x] Token SSE 只计入一次用户请求；在用户窗口已满时，限额为 1 的 API Key SSE 仍正常完成，且 API Key Redis 计数仍为 1。
- [x] 本地后端已按源码更新重启；未切换线上代理、未部署。

### A5 测试数据管理授权验收（2026-10-18）

- [x] `mvn -q -Dtest=AdminAuthAspectTest,UserRateLimitInterceptorTest test` 通过；覆盖端点注解边界、未登录 401、普通用户 403、启用管理员放行及禁用管理员拒绝。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；未登录不能读取/初始化，普通用户不能读取/初始化/清理，拒绝请求前后 Redis 哨兵内容不变。
- [x] 本地临时管理员成功执行单用户初始化、缓存查询、批量初始化和清理；每项操作均在 `admin_operation_log` 记录管理员 ID、用户名、动作和结果。
- [x] 修复测试数据缓存初始化裸 `ObjectMapper` 不支持 `LocalDateTime` 的本地功能缺陷，现使用统一配置后的 Spring mapper；四个管理员接口均通过 HTTP 回归。
- [x] 只在隔离本地数据库与 Redis 验证，未切换线上代理、未部署。

### B1 直接充值风险止血记录（2026-10-18，完整支付功能仍待实现）

- [x] `mvn -q -Dtest=UserRechargeSecurityTest,AdminAuthAspectTest test` 通过；大额直接充值与优惠券直充均返回 503，控制器不再调用余额入账服务。
- [x] `npm run build` 通过；两个充值入口明确显示自助充值暂停，前端不再提供伪成功的直接入账操作。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；伪造 1,000,000 元请求前后本地用户余额、充值流水和 Redis 余额一致。
- [x] 2026-10-19 本地审计补齐优惠券接口语义：`/user/coupons` 改为读取当前用户持久化优惠券，不再随机生成假券；未实现的单独使用与兑换码接口明确返回 503，不伪报成功；前端 API 类型不再暴露无法成功的使用/兑换方法。
- [x] `UserRechargeSecurityTest` 覆盖真实优惠券列表、独立使用/兑换拒绝及无服务副作用；全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 在加载隔离本地配置后通过（83 项，0 失败、0 错误、0 跳过），`npm run build` 通过。
- [x] 扩展 `./scripts/ai-local-env.sh verify-chat` 回归优惠券列表为空时不生成虚假券，独立使用/兑换返回 503，余额、充值流水、券记录与使用记录均无变化；整条本地模拟聊天、验证码、SSE 与计费验证通过。
- [x] 用户已决定暂不接入支付接口，后续另行选择可信支付渠道；当前继续关闭普通用户自助充值，不创建自定义验签协议。`UserRechargeSecurityTest` 5 项通过，并于 2026-10-19 再次独立复跑（0 失败/错误/跳过）；完整 `verify-chat` 确认 1,000,000 元伪造直充及优惠券直充均返回 503，余额、充值流水、优惠券与使用记录不变。可信支付订单/验签/对账仍作为 B1 未完成门禁，未开放充值。
- [x] 2026-10-19 前端文案对齐关闭状态：会员页明确现有余额仍可用于开通/续费，自助充值未开放；余额不足提示联系管理员，不再提示用户先充值；Billing FAQ 同步说明自助充值不可用。`npm run build` 通过；本轮浏览器控制连接中断，未声称视觉验收。

### B2 点数余额并发与预扣一致性验收（2026-10-18）

- [x] `mvn -q -Dtest=PointsServiceBalanceMutationTest,UserRechargeSecurityTest,AdminAuthAspectTest test` 通过；覆盖缓存已有活动预扣时增点和缓存缺失时初始化。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；先模拟 5 点未结算预扣，再经管理员接口增加 20 点，DB、Redis 与点数流水 before/after 一致。
- [x] 同一隔离测试用户并发执行 10 笔各 1.125 点增点，DB 与 Redis 最终均为基准值 + 20 + 11.25，且恰有 10 条新流水，没有丢失更新。
- [x] 只使用本地数据库、Redis 和模拟模型，未切换线上代理、未部署。

### B3 结算重放与旧快照保护验收（2026-10-18）

- [x] `mvn -q -Dtest=PointsServiceBalanceMutationTest test` 通过；覆盖数据库未前进时重放补写余额、版本已前进时保留新余额，以及旧 Redis 快照缺少版本元数据时失败关闭。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过；本地 outbox 扫描器处理“Redis 已结算、DB 已有后续余额更新、outbox 仍待处理”的旧快照，DB/Redis 保持最新余额，账本保留原结算时间与余额快照。
- [x] 将同一事件再次置为待处理后由扫描器重复投递；outbox 重新完成，扣费流水和点数流水均仍只有一条，余额不变。
- [x] 新增 `points_version` 与 `migration-v8-points-settlement-version.sql`；本地启动时幂等执行迁移，`rebuild.sql` 包含新列。只在本地栈验证，未切换线上代理、未部署。
- [x] 2026-10-19 复核失败关闭路径：Lua 对缺少 `databasePointsVersion` 或 `settledAt` 的旧快照返回 `legacy_settlement_metadata_missing`；`PointsService` 在写账单、点数流水或数据库余额前返回失败，outbox 保持待处理等待重试，不会应用旧余额快照。当前本地 MySQL/Redis 只读审计结果为 0 个待处理 outbox、0 个关联结算快照、0 个缺元数据或格式异常快照。
- [x] 2026-10-19 生产复核与历史账本修正：6 个 outbox 全部为 `SETTLED`，均能对应到 `billing_record` 与 `SETTLE` `points_bill`，用户、实际扣点及 after 余额一致；旧 Redis 快照缺少 `databasePointsVersion`/`settledAt`，但没有悬挂 outbox。修正前发现 6 条历史 `points_bill.balance_before` 均比对应 `billing_record.balance_before` 少 Redis 预扣额。先创建并通过 `gzip -t` 校验权限 `600` 的当前数据库备份 `/root/lingdian/backups/ai_gateway-pre-b3-ledger-fix-20261019T071404Z.sql.gz`，再以 `SERIALIZABLE` 事务和行锁将 6 条 before 快照对齐，影响行数恰为 6。修正后 before/after/扣点金额差异均为 0；10 个用户的余额合计 `529000`、点数合计 `53934` 与修正前相同。

### B4 点数预扣崩溃恢复验收（2026-10-18）

- [x] 新增 `ai_charge_intent` 持久化请求状态和恢复租约；预扣 key 不再依赖 TTL，退款与结算 Lua 标记在 DB/outbox 确认完成后清理。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过本地故障注入：旧预扣 key 丢失后按持久化金额退款并写一条退款流水；模拟退款已完成但状态待确认的重放，余额和流水均不重复；完整响应待结算时恢复 outbox 并只扣实际点数一次；上游结果未知时转 `MANUAL_REVIEW`、保留预扣且记录对账原因。测试结束后清理隔离用例数据并恢复本地测试用户余额。
- [x] 全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：41 项测试，0 失败、0 错误、0 跳过；聊天接口、普通/SSE 调用链和本地 mock 上游回归通过。
- [x] 新增 `migration-v9-ai-charge-intent.sql` 并纳入本地启动迁移；只使用本地数据库、Redis 与模拟模型，未切换线上代理、未推送或部署。
- [x] `MANUAL_REVIEW` 表示服务无法仅凭本地记录判断上游是否已计费；本地已补管理员核验队列、一次性退款/结算决策和持久化证据审计。真实供应商日志核验仍须由管理员逐笔完成，不由系统猜测。

### B4 管理员人工对账闭环验收（2026-10-19）

- [x] 新增 `/admin/ai-charge-intents/manual-review` 待处理队列、`/reconciliations` 历史记录和受保护的处理接口；查询仅返回必要的请求、用户、模型、预扣金额和失败原因，不暴露幂等指纹。
- [x] 新增 v17 `ai_charge_review_audit` 持久审计表。每个 requestId 只允许登记一次处理方向；退款要求证据确认上游未计费，结算要求证据确认上游计费且实际点数大于 0、不超过预扣。仅超级管理员可执行；重复相同提交幂等，不允许覆盖既有决定。
- [x] 退款继续使用预扣退款幂等路径；结算继续使用结算 outbox 和账本路径。管理员在状态变更后崩溃时，恢复扫描器可以继续 `REFUND_PENDING` / `SETTLEMENT_PENDING`，历史页会同步终态。
- [x] 对账备注拒绝常见 API Key、Bearer Token、密码字段等密钥样式；备注只保存在受限的对账审计表，通用管理员操作日志不复制备注内容。
- [x] 管理后台“计费与订单”增加“AI 人工对账”页签，明确区分“确认未计费并退款”和“确认计费并结算”，要求填写证据；提交前二次确认，普通管理员仅可查看，超级管理员可处理。
- [x] 本地集成测试覆盖退款幂等、实际金额结算、崩溃恢复、重复/冲突决定拒绝、超额结算拒绝、证据密钥拦截和队列字段边界；HTTP 测试覆盖路由、管理员鉴权、超级管理员处理及 Bean Validation。全量 Maven 95 项测试通过，0 失败、0 错误、0 跳过；`npm run build` 通过。
- [x] 2026-10-19 保存点：`Aiplatform-demo/target/surefire-reports` 汇总 32 份报告、95 项测试全部通过；`/admin` 守卫修改后 `npm run build`、`git diff --check` 和本地脚本语法检查通过。浏览器确认管理后台的人工对账空队列可见；临时预览账号已恢复为 `USER`。本地 MySQL、Redis、backend、web 和 mock 均在运行。
- [x] 2026-10-19 补齐非空浏览器端到端流程：隔离测试预扣分别通过页面执行退款与结算，二次确认文案、队列移除、成功提示和历史记录均可见；结算用例预扣 2.500 点、实际结算 1.250 点，MySQL/Redis 均落到 118.000，且生成唯一账单、点数流水和 outbox。之后删除全部 UI fixture/关联账务记录，测试用户恢复为 `USER`、余额及 Redis 缓存均恢复至 119.250。随后复跑三个 B4 后端套件，共 11 项测试、0 失败/错误/跳过；当前凭据扫描覆盖 42 个暂存变更、349 个跟踪文件和 148 个未跟踪文件，通过且未输出值。
- [ ] 真实供应商账单或调用日志尚未接入，人工操作是否正确仍以管理员核验证据为准；生产需先应用 v17 迁移并按受控发布流程验收。

### B5 每日赠点生命周期验收（2026-10-18）

- [x] `mvn -q -Dtest=PointsTemporaryPointsLifecycleTest,PointsDistributionServiceTest,PointsExpirationServiceTest test` 通过；本地 MySQL/Redis 集成用例验证稳定业务 ID 重复发放只入账一次、扣点后只过期未消费的临时赠点、流水 before/after 与余额一致，重复扫描不重复扣减。
- [x] `PointsExpirationServiceTest` 验证模拟过期处理失败后下一轮调度扫描会再次处理该用户；每日发放失败重试与连续失败报告由 `PointsDistributionServiceTest` 覆盖。
- [x] 全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：45 项测试，0 失败、0 错误、0 跳过。
- [x] `./scripts/ai-local-env.sh verify-chat` 与 `./scripts/ai-local-env.sh verify` 均通过；本地 mock 聊天、计费恢复、前端代理、未认证边界正常，代理模式仍为 `local`。
- [x] 2026-10-19 迁移路径只读审计：v10 只扩展账本类型注释并创建空的临时点数批次表，不回填余额；`scripts/server-deploy.sh` 不执行数据库迁移，只在门禁通过后更新 backend/frontend。常规应用部署不会自动运行 v10，但历史逐用户核账与迁移/冲销决策仍必须完成。
- [x] 2026-10-19 用户确认历史 6 笔、790 点旧限时赠点按永久点数保留。v10 迁移只创建空的临时批次表、不猜测性回填；迁移前后用户总点数与旧临时赠点账本均不变。新赠点由批次表逐笔追踪过期。

### C1 登录二次验证验收（2026-10-18）

- [x] 标准 TOTP、前后一个 30 秒时间步校验、无效输入拒绝、加密密钥解密和跨用户密文拒绝由 `TotpServiceTest` 覆盖；RFC 测试向量验证结果为 `287082`。
- [x] `UserTwoFactorServiceLifecycleTest` 使用本地 MySQL/Redis 验证启用确认、缺少二次验证码时 HTTP 428 且不创建会话、有效 TOTP 登录、时间步重放拒绝、备份码登录和重放拒绝、备份码哈希存储、状态接口不泄露备份码及安全关闭 2FA。
- [x] 全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：50 项测试，0 失败、0 错误、0 跳过；`npm run build` 通过。
- [x] `./scripts/ai-local-env.sh verify-chat` 与 `./scripts/ai-local-env.sh verify` 均通过；本地登录验证码、防重放、AI 聊天与 SSE、计费恢复、代理和未认证边界正常。`./scripts/ai-local-env.sh status` 确认本地模式，未切换线上代理、未推送或部署。
- [x] 2026-10-19 生产 v11 迁移后核验：旧 Redis `2fa:secret:*` 键为 0，`user_two_factor` 记录为 0；没有旧版伪 TOTP 密钥需要迁移或通知。没有导入旧密钥，也没有轮换 `API_ENCRYPTION_KEY`。

### C2 OAuth/OIDC 验收（2026-10-19）

- [x] `GoogleOidcClientTest` 使用本地 RSA/JWKS 模拟授权服务器，验证授权码表单携带 PKCE verifier、签名成功和无效签名拒绝，并覆盖错误 issuer、audience/azp、过期 ID Token、错误 nonce 与过期/重复授权码拒绝。
- [x] `OAuthServiceLifecycleTest` 连接隔离本地 MySQL/Redis，验证 state 绑定浏览器 cookie、错误浏览器拒绝且不消耗有效 state、state 重放拒绝、数据库身份映射、重复绑定冲突、OAuth 登录不能绕过 2FA 及二次验证挑战单次完成；Controller 用例确认 cookie 包含 `HttpOnly`、`SameSite=Lax` 和限定路径。
- [x] 全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：57 项测试，0 失败、0 错误、0 跳过；`npm run build` 通过。
- [x] 本地 API 回归确认 Google 凭据缺失返回 503、GitHub 占位提供方返回 400、缺失浏览器绑定的回调返回 400；`./scripts/ai-local-env.sh verify-chat`、`verify` 均通过，`status` 确认为 `local`。迁移 `migration-v12-oauth-identities.sql` 已应用于本地，未切换线上代理、未推送或部署。
- [x] 2026-10-19 本地复验：`GoogleOidcClientTest` 3 项、`OAuthServiceLifecycleTest` 4 项均通过；生命周期测试使用临时 MySQL schema，结束后已删除。Google 凭据配置前授权入口按设计返回 503，配置后返回指向 `accounts.google.com` 的授权 URL，范围为 `openid email profile`，包含 PKCE S256、state、nonce，并设置 HttpOnly/SameSite=Lax 浏览器绑定 Cookie。
- [x] Google Cloud `Gemini API` 开发项目已配置品牌“凌点 AI Gateway”，支持邮箱使用当前 Google 账号；受众为“外部/测试”，仅当前账号登记为测试用户。Web 客户端登记来源 `http://127.0.0.1:13000` 和回调 `http://127.0.0.1:13000/oauth/callback`。客户端凭据写入 `.runtime/local.env`（权限 `600`、Git 忽略），下载 JSON 副本已删除；未将密钥写入代码、计划文档或聊天。
- [x] 真实本地浏览器 OAuth 端到端验收：从登录页跳转 Google，用户同意仅提供姓名/头像及邮箱后，Google 返回 `/oauth/callback` 并进入本地 `/chat`。个人中心显示 Google 已验证邮箱和绑定状态；Google 登录创建的邮箱同步写入已验证联系方式，绑定到现有密码账号时不覆盖其原邮箱。前端将内部 `oauth_*` 映射名改为显示登录邮箱，并修复 React StrictMode 重复消费一次性 state 的回调竞争。`GoogleOidcClientTest` 3 项和 `OAuthServiceLifecycleTest` 4 项在临时数据库通过；`test:oauth-callback` 2 项、`test:auth-session` 7 项、`test:model-availability` 3 项、`npm run build`、本地 `verify` 与 `verify-chat` 均通过。
- [ ] 2026-10-19 生产 Google 门禁复核：Nginx 配置域名为 `lingdian-ai.top` / `www.lingdian-ai.top`，线上 HTTPS 首页、`/api/actuator/health`、`/api/auth/login/captcha` 返回 200。用户已提供正式域名 `https://lingdian-ai.top/`、首页 `/welcome`、隐私 `/privacy`、条款 `/terms`，并选择新建独立生产 Google Cloud 项目；项目“Lingdian AI Production”（ID `lingdian-ai-production`）已创建，未启用付费资源或 OAuth 凭据。2026-10-19 实测四个 URL 均返回 200，但四份 HTML 字节数和 SHA-256 完全相同，隐私/条款仍是 SPA 壳，没有可提交审核的政策正文。`WelcomePage.tsx` 的法律链接和本地草案已完成，草案明确提示待确认。运营主体全称和公开客服/隐私邮箱待用户补充；账号停用保留，删除范围待用户确认。`.env.prod` 中生产 OAuth client ID/secret/redirect URI 均缺失。正式配置前还需发布并核验真实法律页面、确认主体及公开邮箱；预计回调为 `https://lingdian-ai.top/oauth/callback`。不得将旧版 `oauth:bind:*` Redis 值或旧伪 OAuth 用户名自动迁入身份表；GitHub 和微信仍须各自完成真实提供方适配及验证后才能开放。
- [x] 2026-10-19 生产 Google Auth Platform 按用户授权完成基础配置：独立项目 `lingdian-ai-production` 的品牌名为“凌点 AI”，支持与开发者联系邮箱为用户指定邮箱，受众为“外部/测试”；已将用户指定的 Google 账号加入测试名单、将 `lingdian-ai.top` 加入已获授权网域，并保存首页 `https://lingdian-ai.top/welcome`。控制台验证为 1 个测试用户、尚无 OAuth 客户端。
- [ ] 生产 Google OAuth 仍待凭据所有者创建 Web OAuth 客户端并将 ID/secret/redirect URI 写入服务器受限 `.env.prod`；本轮只读复核确认三项均缺失，线上授权入口返回 HTTP 200/业务码 503“Google OAuth 尚未配置”。已新增 `spring.config.import` 可选加载 `/app/google-oauth.properties`，从提交 `f028fe7` 构建的 JAR 已本地构建并完成配置加载路径 smoke test，但线上 JAR SHA-256 仍为旧版、未部署。继续之前须由用户手动创建并提交客户端凭据；随后只更新现有 backend 容器内 JAR/受限配置文件并核验 OAuth URL 与登录回调。测试状态只允许登记用户；真实隐私/服务条款正文、运营主体全称与公开联系邮箱仍是公众发布/验证门禁。

### C3 会话撤销与 Token 失效验收（2026-10-18）

- [x] `TokenRevocationLifecycleTest` 使用本地 MySQL/Redis 覆盖设备替换、旧 Token 延迟退出不得误踢新会话、移除设备、保留当前设备、全端退出、当前 Token 退出、用户自助封禁与管理员封禁；普通用户接口和聊天认证过滤器均拒绝已撤销 Token。
- [x] 全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：60 项测试，0 失败、0 错误、0 跳过。
- [x] `./scripts/ai-local-env.sh verify` 与 `verify-chat` 通过；真实本地 HTTP 回归在全端退出后确认 `/api/user/info`、OpenAI 兼容 `/api/chat/completions` 和 SSE `/api/chat/stream` 均以业务码 401 拒绝旧 Token；本地模型流式聊天、结算与代理仍正常。
- [x] `./scripts/ai-local-env.sh status` 确认为本地模式，MySQL、Redis、后端、前端和模拟模型均运行；只在本地环境验证，未切换线上代理、未推送或部署。
- [x] 2026-10-19 前端失效会话提示与刷新恢复：Token 刷新成功后原请求只重试一次；仍收到 401 时清理旧会话，并在登录页一次性显示“登录状态已失效，请使用账号、密码和图形验证码重新登录”。Node 回归现为 7 项，覆盖首次 HTTP 401、刷新失败、刷新后重试仍 401、非认证错误保留会话、锁定账号和提示单次消费；`npm run build` 通过。
- [x] 旧 Redis Token 不含设备绑定映射时按失败关闭策略需用户重新登录；前端已在续期/重试仍为 401 时清除失效会话，并一次性显示重新登录提示。`npm run test:auth-session` 7 项全部通过。
- [ ] 生产发布前仍需安排重新登录公告并确认兼容窗口；部署后观察旧会话失效与重新登录反馈。

### C4 邮箱与手机号验证绑定验收（2026-10-18）

- [x] `UserContactVerificationLifecycleTest` 使用本地 MySQL/Redis 验证未验证注册邮箱不入库、邮件/手机验证码按用户隔离、错误码/错误用户/过期码/重放拒绝、已占用联系方式冲突，以及并发绑定同一邮箱最多一个成功；成功后 `user.email`、`user.phone` 与唯一验证记录一致。
- [x] `VerificationCodeDeliveryServiceTest` 验证未配置投递通道时返回 503，且没有把验证码写入 Redis；本地验证码收件箱必须显式开启，生产默认关闭。
- [x] 全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：65 项测试，0 失败、0 错误、0 跳过；`npm run build` 通过。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过真实本地 HTTP `/api/user/send-bind-code`、`/bind-email`、`/bind-phone`；仅从隔离 Redis 测试收件箱取码并核对 MySQL 持久化。原有聊天、流式聊天、计费恢复与 C3 旧 Token 回归也通过。
- [x] 新增并在本地重复应用 `migration-v13-user-verified-contacts.sql`；`verify` 和 `status` 均确认前端代理仍为 `local`，MySQL、Redis、后端、前端与 mock 正常。未切换线上、未推送或部署。
- [ ] 真实发信联调仍需配置 SMTP 沙箱与 HTTPS 短信网关（`VERIFICATION_DELIVERY_MODE=external`、SMTP `SPRING_MAIL_*`、`VERIFICATION_SMS_URL`、`VERIFICATION_SMS_TOKEN`、`VERIFICATION_SMS_TEMPLATE_ID`）。短信网关需接受 JSON `{phone, templateId, variables:{code, expiresInMinutes}}` 并以 2xx 表示提交成功；此处未声称真实邮件或短信已送达。
- [x] 2026-10-19 生产 v13 审计：10 个用户中重复邮箱组为 0，已验证联系方式表为空；旧邮箱未被自动标记为已验证，用户需要在邮件/短信投递配置完成后重新验证。没有发生邮箱冲突或账号合并。

### C4 前端契约与安全中心界面验收（2026-10-19）

- [x] `UserInfoVO` 增加 `emailVerified`、`phoneVerified`；仅当用户资料与 `user_contact_verification` 中的验证值一致时返回 `true`。前端类型、API 响应和 Zustand 用户资料同步增加邮箱、手机号及验证状态，个人中心明确区分已验证、未验证与未设置。
- [x] 安全中心已接入 `/security`、`/home/security`，并从个人中心“设置”提供入口。登录验证码页面缺少二步码时不会进入永久“登录中”；表单标签、自动填充和错误播报已补齐。
- [x] OAuth、2FA、设备接口加载失败会显示错误和重试入口；设备页展示后端真实会话结构及单设备策略，不再展示不存在的浏览器/IP 信息或误导性的“下线其他设备”。联系方式绑定后重新读取资料，邮件/手机冷却计时分别运行；2FA 密钥说明改为手动录入。
- [x] 修复独立 `/admin` 路由别名漏包 `AdminRoute` 的问题；普通登录用户访问管理台会跳回欢迎页，管理员仍可进入。通过本地浏览器对实际登录的普通测试账号验证重定向。
- [x] 安全中心使用主题变量、可滚动页面容器和手机底部留白。通过本地浏览器将真实 React 页面接入仅用于预览的合成 API 响应，检查桌面布局、窄屏绑定表单、2FA 状态和设备卡片；验证码页面正常显示。预览只使用合成账号数据，没有发起真实 OAuth、验证码投递或改绑。
- [x] `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：80 项，0 失败、0 错误、0 跳过；生命周期测试断言未验证资料为 `false`、完成绑定后对应字段为 `true`。`npm run build` 通过。
- [x] `./scripts/ai-local-env.sh switch local` 按新源码重建并重启后端/前端；`verify` 和 `verify-chat` 均通过，后者覆盖登录验证码、邮件/手机绑定、聊天、SSE、权限和计费恢复。环境仍指向本地代理。
- [ ] 生产 SMTP/短信投递、生产 Google OAuth 客户端和生产 DeepSeek 上游仍需独立配置并验收；本地 DeepSeek 真实调用已通过（见“真实 DeepSeek 本地上游验收”）；Google 本地开发沙箱已完成真实登录测试（见 C2）；旧历史邮箱不会被自动认定为已验证。

### D1 文件解析任务重启恢复验收（2026-10-18）

- [x] `FileParseTaskRecoveryLifecycleTest` 使用本地 MySQL/Redis 和本地文件存储模拟 worker 在 `PARSING` 后退出；本地扫描器重新领取并成功解析，原 `PENDING` 任务也能由扫描器执行；达到 5 次上限的过期任务转为带明确错误原因的 `FAILED`。另通过上传和状态查询 Controller 用例验证本地上传接口可正常返回并最终到达 `SUCCESS`。
- [x] Controller 验收发现本地 `MultipartFile` 落盘将不支持的 `NOFOLLOW_LINKS` 传给 `Files.copy`，导致上传失败；`OssService` 已改用不覆盖已有路径的 `CREATE_NEW` 写入，路径越界检查继续生效，相关存储回归也通过。
- [x] fencing 回归确认旧 worker 不能把已恢复任务写为失败，新 worker 持有有效租约时重复领取会被拒绝；活跃 worker 定时续租，成功/失败终态更新均要求匹配令牌。
- [x] `FileParseDispatchModeTest` 覆盖 `RABBITMQ_ENABLED=false/true`：关闭时仅注册本地持久化任务扫描器，开启时注册 Rabbit outbox dispatcher；真实 broker 投递另见 2026-10-19 RabbitMQ 集成验收。
- [x] 全量 `mvn -q test` 通过：72 项测试，0 失败、0 错误、0 跳过。新增幂等迁移 `migration-v14-file-parse-recovery.sql`，本地数据库已应用；`rebuild.sql` 与本地启动迁移列表同步。
- [x] 2026-10-19 用隔离 RabbitMQ 4.2.9 实例运行 `RabbitEventPublisherIntegrationTest`：真实 publisher confirm、持久化消息属性、mandatory unroutable return 拒绝均通过；真实 `FileParseMessageListener` 收到消息并调用解析服务，队列 ready/unacknowledged 均归零。opt-in 测试共 2 项通过。
- [x] 完整应用 Rabbit 模式重启恢复验收：先停止后端并将 `PENDING` 文件任务写入本地 MySQL，再以 `RABBITMQ_ENABLED=true` 启动应用；恢复扫描器发布消息，真实 `FileParseService` 将任务解析为 `SUCCESS`，`dispatch_attempts=1`、`parse_attempts=1`、解析文本长度 57；队列 ready/unacknowledged 为 0。测试文件、任务、Redis 缓存和临时 Rabbit 容器已清理，随后默认 `RABBITMQ_ENABLED=false` 本地环境重启成功。
- [x] 加入上述 opt-in 集成测试后的全量后端回归通过：97 项，0 失败、0 错误、2 项按条件跳过；命令 `mvn -f Aiplatform-demo/pom.xml -q -Dapp.messaging.rabbit-enabled=false test` 在加载 `.runtime/local.env` 后执行。
- [ ] 生产 RabbitMQ 的网络、凭据/vhost、队列策略与运行监控仍需在实际部署环境配置并验收；本地 broker 通过不代表生产 broker 已联通。未切换线上代理、未推送或部署。

### D3 默认输出 Token 上限验收（2026-10-18）

- [x] 新增 `ChatOutputTokenPolicy`：默认输出 2048；按模型上限、套餐上限与平台 32768 硬上限取最小值；缺失或无效的模型配置回退到 4096。新增 `platform_model_config.max_output_tokens` 和幂等迁移 `migration-v16-model-output-token-cap.sql`，本地模型配置已应用。
- [x] `ChatOutputTokenPolicyTest` 覆盖省略、负数、合法值、超模型/套餐/平台上限及无效模型配置；`OpenAiChatServiceProviderTokenPayloadTest` 将四种输入序列化后确认 OpenAI/Anthropic `max_tokens` 与 DeepSeek `max_output_tokens` 字段和值均有效。
- [x] `./scripts/ai-local-env.sh verify-chat` 通过真实本地 HTTP 与模拟上游验收：省略、负数、超限分别发送 `1536`，合法值发送 `768`；模拟服务捕获到的字段为 `max_tokens`。既有验证码、认证、权限、幂等、SSE、结算与故障恢复回归一并通过。
- [x] 全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：80 项测试，0 失败、0 错误、0 跳过；`npm run build`、`./scripts/ai-local-env.sh verify` 和 `git diff --check` 通过。后续 E3 已将 ChatPage 入口降至 94.97 KB，其他页面的大型 chunk 见 E3 记录。
- [x] `./scripts/ai-local-env.sh status` 确认 MySQL、Redis、后端、前端及模拟模型运行，代理仍为 `local`；仅应用本地迁移和本地配置，未切换线上、未推送或部署。
- [x] 2026-10-19 复跑 `verify-chat` 覆盖 4 种 Token 上限请求、验证码、联系方式绑定、B2/B3/B4 结算恢复、权限、限流、幂等与 SSE，全部通过；本地验收栈的每日赠点 cron 默认禁用，避免跨午夜时对余额断言产生并发干扰，需要验收定时发放时可在 `.runtime/local.env` 设置 `APP_POINTS_DAILY_GRANT_CRON`。

### E1 本地凭据治理验收（2026-10-19，发布门禁仍未完成）

- [x] `.env.prod` 已从 Git 索引移除并由 `.env.*` 忽略规则保护；本机文件权限为 `600`，`git log --all -- .env.prod` 未发现已提交版本。
- [x] 删除旧 `.env.example` 中的敏感默认值；`.env.prod.example` 改为占位符；当前测试 API Key 初始化器改为每次随机生成，不再保留静态测试 Token。
- [x] 新增 `scripts/check-staged-env-secrets.py` 和 `.githooks/pre-commit`，扫描暂存内容、当前跟踪文件及未跟踪/已跟踪环境模板；`git hook run pre-commit` 通过，本次检查覆盖 42 个暂存变更、349 个跟踪文件、134 个未跟踪文件和 2 个模板。
- [x] 本地 `origin` URL 曾内嵌访问凭据，现已改为不含凭据的 HTTPS 地址。提交检查增加 Git remote URL 凭据检测；正常配置通过，使用合成凭据的拒绝用例通过。Git Credential Manager 使用 macOS Keychain。
- [x] `ApiKeyTestDataInitializerTest` 验证生成值格式正确且 1000 次生成不重复；本次完整 Maven 套件通过 80 项测试，0 失败、0 错误、0 跳过。
- [x] 新增 `.githooks/pre-push` 与 `scripts/check-push-history-secrets.py`：推送前扫描目标引用的完整可达历史，包括文件对象、提交说明和标签；命中高置信度凭据格式、对象无法读取或输入无效时拒绝推送，诊断只显示规则和位置。`scripts/test-push-history-secrets.py` 在临时仓库验证干净历史通过、含模拟凭据及其后续历史被拒；对当前 `main` 的模拟推送也被拦截且没有回显匹配值。
- [x] 对全部 28 个本地可见提交进行不回显扫描：覆盖 1,369 个带路径对象（树和文件对象）及 28 个提交对象，在 25 个文件对象中命中供应商 API Key 格式；位置仅记录在旧 Postman 样例、部署/使用文档、测试脚本和 SQL 样例中，不输出匹配值。模式扫描不能证明密钥当前有效；未调用或撤销任何历史密钥，也未改写提交历史。扫描范围内没有命中 GitHub Token 格式；用户要求保留的 GitHub 凭据未触碰。
- [x] 当前暂存内容和工作区跟踪文件通过 `python3 scripts/check-staged-env-secrets.py`：覆盖 42 个暂存变更、349 个跟踪文件和 2 个环境模板；本地 `origin` 地址不含内嵌凭据，GitHub 远端读取凭据仍由 macOS Keychain 管理。
- [x] 本轮凭据门禁复验：暂存/工作区扫描覆盖 42 个暂存变更、349 个跟踪文件、157 个未跟踪文件和 2 个环境模板并通过；`scripts/test-push-history-secrets.py`、`scripts/test-production-security-guards.py` 均通过，诊断不回显凭据。`scripts/check-production-env.py` 仍按预期以退出码 1 拒绝发布，仅报告缺少 `MYSQL_PASSWORD`、`REDIS_PASSWORD`、`SECONDARY_PASSWORD`；未连接远端、未推送或部署。
- [x] 2026-10-19 复核本地环境文件边界：`.env.prod` 与 `.runtime/local.env` 文件权限均为 `0600`；`.env.prod` 被 `.gitignore` 忽略且不在 Git 索引中，当前暂存/工作区凭据扫描通过，检查未回显任何值。此后已按用户授权重写本地 `main`；`origin/main` 与回滚备份引用仍保留旧历史，生产必需凭据和部署联调仍按下列门禁单独处理。
- [x] 2026-10-19 增强模板扫描识别 `API_ENCRYPTION_KEY`，并修正 `.env.prod.example` 中高熵样式的示例值为明确占位符；只同步该模板到暂存区，未改动其他暂存文件。临时仓库回归确认未跟踪模板中的模拟密钥会被拒绝，诊断不回显值。
- [x] 两份生产 Compose 配置要求显式提供 MySQL/Redis 密码、加密密钥、精确允许来源和系统二级口令；移除 `sk-placeholder`、通配跨域和 `5215` 等不安全默认值。`SystemControlController` 在二级口令缺失时失败关闭。模拟配置正反向校验通过。
- [x] 新增 `scripts/check-production-env.py` 与 `scripts/local-release-preflight.sh`：验证 `.env.prod` 权限/索引状态/必需值和 Compose 解析，并跑本地接口、全量后端测试、前端构建与完整聊天回归。`server-deploy.sh` 在镜像传输前执行本地功能验收并只读校验服务器 Compose；部署目标与 SSH 密钥改为显式环境变量，主机指纹检查启用；数据库迁移密码不回传本机或拼入命令行。
- [x] `scripts/test-production-security-guards.py` 覆盖密钥文件权限、缺失/占位/弱值、Git 索引、通配来源、未跟踪环境模板和两份 Compose 的必填变量；全量 `mvn -q -Dapp.messaging.rabbit-enabled=false test` 通过：84 项、0 失败、0 错误、0 跳过；本地基础验证通过。没有连接或部署到线上。
- [x] 2026-10-19 澄清本机与服务器环境差异：本机完整 `.env.prod` 预检曾因服务器密码未复制到开发机而拒绝；用户当时确认线上三项已配置，因此部署流程改为 `--local-only` 执行本地功能验收，再通过 SSH 只读校验服务器 Compose，不要求复制服务器密码。后续严格配置检查发现服务器 Redis 密码实际为空，见本节最新核验记录。
- [x] 2026-10-19 经用户授权，仅重写本地 `main` 历史以脱敏扫描器命中的高置信度凭据格式；重写后同一 pre-push 扫描器通过（1 个目标引用、1,369 个带路径文件对象、28 个提交），`scripts/test-push-history-secrets.py` 通过。当前工作区凭据扫描也通过（42 个暂存变更、349 个跟踪文件、157 个未跟踪文件、2 个环境模板）；为避免旧索引中的测试 Key 再次进入暂存区，将初始化器和 `rebuild.sql` 的索引项恢复到已脱敏 `main`，对应工作区文件字节未变。`.runtime/local.env` 权限仍为 `600`，DeepSeek 与 Google OIDC 配置仍存在；没有撤销本地凭据。
- [x] 历史记录：该次复核时 `origin/main` 尚未更新，后续已按用户授权完成历史清理并推送；截至 `d31d134` 远端与本地 `main` 同步。旧对象仍保留在本地回滚引用中。生产 Redis 认证按用户选择暂缓；真实生产模型与外部服务仍需分别联调。本项状态已由 2026-10-19 最新核验更新。
- [x] 服务器旧 Compose 只读校验曾通过，同次 `docker ps` 确认现有 7 个容器均运行，其中 MySQL/Redis 报健康；该旧 Compose 允许 Redis 密码为空。2026-10-19 使用更新后的本地 Compose 对远端 `.env.prod` 做 stdin 只读解析时，因 `REDIS_PASSWORD` 为空而按预期拒绝。旧容器健康状态不证明 Redis 已启用认证；现有 Redis 无密码 `PING` 返回 `PONG`，backend Redis 密码环境变量为空。
- [x] 2026-10-19 根据用户确认“生产凭据已在服务器受控环境配置”，将部署流程拆分为本地功能验收与目标服务器密钥校验：`local-release-preflight.sh --local-only` 不要求把服务器密钥复制到开发机；`server-deploy.sh` 仍在任何镜像传输前执行目标机 `docker compose ... config -q`。`test-deploy-preflight-routing.py`、Bash 语法检查及完整 `--local-only` 本地预检通过（后端 101 项、0 失败/错误、2 项 Rabbit opt-in 跳过；前端构建与 `verify-chat` 通过）。
- [x] 2026-10-19 使用用户提供的主机、用户和本机私钥路径完成严格主机校验下的远端只读 Compose 检查，并列出现有容器状态；未输出密钥值，未执行镜像或容器变更。当前 shell 尚未常驻设置部署脚本所需环境变量；获准部署时再按需注入。
- [x] 历史记录：当时完整推送历史扫描通过，但 `origin/main` 尚未更新；之后已完成推送。当前远端状态以本节末尾 `d31d134` 检查点为准。
- [x] 2026-10-19 复核服务器 Compose 时发现 RabbitMQ、OSS、SMTP/短信和 Google OIDC 环境变量未传入后端容器；已在 `docker-compose.server.yml` 与 `docker-compose.prod.yml` 补齐透传，默认仍关闭 RabbitMQ、外发验证和 Google OAuth，敏感项默认空值。`scripts/test-deploy-preflight-routing.py` 用临时合成环境动态渲染服务器 Compose 并逐项断言变量可达；`scripts/test-production-security-guards.py` 同时验收两份 Compose 的 Redis 认证/健康检查和可选变量透传；`.env.prod.example` 字段完整，凭据扫描、测试及 `git diff --check` 通过。没有启动或修改容器；生产服务是否真实可用仍需单独联调。
- [x] 2026-10-19 部署脚本不再只更新镜像而沿用服务器旧 Compose：先把本地 `docker-compose.server.yml` 通过 SSH stdin 与服务器 `.env.prod` 做只读解析，再传输并二次校验隐藏暂存文件；镜像加载成功后备份旧配置并原子替换，最后只 `up -d redis backend frontend`。部署前确认 MySQL、Redis、backend、frontend 四个指定现有容器都在运行；不会重启 MySQL、改动数据卷或新增服务。两个生产 Compose 均要求 Redis 密码并用 RedisCLI_AUTH 做认证健康检查；服务契约测试覆盖认证参数、持久化数据卷和部署顺序，未执行部署。
- [x] 2026-10-19 远端只读核对仅输出变量名和存在状态，未读取或回传任何值：`/root/lingdian/.env.prod` 中 `REDIS_PASSWORD` 为空；`MYSQL_PASSWORD`、`SECONDARY_PASSWORD`、`API_ENCRYPTION_KEY`、`ALLOWED_ORIGINS` 非空。当前 Redis 无密码，backend 的 Redis 密码变量也为空。RabbitMQ、OSS、SMTP/短信和 Google OIDC 配置项在 `.env.prod` 和当前 backend 环境中均缺失。本次没有修改服务器文件或容器；各外部服务需由凭据所有者配置后按门禁验收。
- [x] 2026-10-19 再次只读检查发现服务器 `.env.prod` 属主为 `root`、权限为 `644`；新的服务器环境检查器按预期拒绝该权限，要求改为 `600` 后才继续检查变量。`server-deploy.sh` 已增加 stdin 传送检查器源码的只读门禁，在镜像传输前依次检查现有容器运行状态、服务器环境文件权限/必需变量和 Compose 解析；检查只报告变量名，不输出任何配置值。没有 chmod、写入环境文件、传输镜像或变更容器。服务器当前需由所有者将 `.env.prod` 收紧至 `600` 并设置强 `REDIS_PASSWORD`；改完后再做只读复核。
- [x] 2026-10-19 用户要求沿用现有容器且不新建镜像/容器；只读检查确认 backend 以 `/app/app.jar` 启动、frontend 静态根为 `/usr/share/nginx/html`，Redis 未启用认证。`server-deploy.sh` 的 Compose 路径仍会加载镜像并替换容器，因此保留显式默认拒绝门禁；后续只考虑新增的 `deploy-existing-containers.sh` 原容器内更新路径。该路径尚未应用，当前被线上法律页缺失阻挡。
- [x] 2026-10-19 最新本地检查点 `39e66f1` 完成后，针对 `main` 再次扫描完整推送历史：1 个目标引用、1,399 个文件对象、37 个提交，通过；没有推送，远端历史仍未变更。

### E2 Nginx API 路由优先级验收（2026-10-19）

- [x] `/api/`、`/chat/`、`/v1/` 改为 `^~` 前缀，确保 API 路径中的 `.png`、`.pdf` 等后缀不会被后续静态扩展名正则规则截获；静态资源缓存规则保持原样。
- [x] 临时 Nginx 容器通过 mock 后端验收：`/api/route-probe.png`、`/api/route-probe.pdf`、`/chat/*.svg`、`/v1/*.json` 均到达对应后端路径；SSE 首事件在 0.01 秒到达（上游第二段延迟 1.2 秒），未被缓冲到流结束。
- [x] `nginx -t` 通过；本地构建 CSS 仍返回 `Cache-Control: max-age=2592000` 与 `public, immutable`。临时容器和 mock 均已清理。

### E3 聊天页首屏包体优化验收（2026-10-19）

- [x] `ChatMessages` 将 Markdown 渲染器改为按需加载并提供无阻塞文本回退；Mermaid 仅在渲染 Mermaid 围栏时加载，高亮语言分别按代码块语言加载，remark-math/rehype-katex 与 KaTeX CSS 仅在检测到公式时加载。
- [x] `npm run build` 通过；聊天入口 `ChatPage` 从 1,403.39 KB（gzip 452.32 KB）降至 94.97 KB（gzip 25.97 KB），减少约 93%；Markdown 渲染器独立 chunk 为 347.57 KB，聊天入口静态依赖图没有超过 500 KB 的 chunk。
- [x] 构建仍报告 ECharts（1,111.90 KB）与另一个共享图表/页面 chunk（662.65 KB）超过 500 KB；二者不在 ChatPage 的静态依赖图内，不会随聊天入口加载。待有独立 Dashboard/Admin 图表包体问题时继续优化。
- [x] 本地浏览器回归页实际渲染 Markdown 加粗、JavaScript 代码高亮、KaTeX 公式与 Mermaid SVG，四项 DOM 检查均通过并完成视觉检查；临时回归页已清理。

### E1 本地发布前置全链路复验（2026-10-19）

- [x] `scripts/local-release-preflight.sh` 已按“本地基础服务与代理验证 → 后端全量测试 → 前端生产构建 → 完整聊天/API 回归 → 最后检查生产配置”的顺序执行；本地基础验证通过。
- [x] 本次后端 Surefire 汇总为 84 项测试、0 失败、0 错误、0 跳过；`npm run build` 通过。
- [x] 完整 `verify-chat` 通过：验证码及重放拒绝、模型输出 Token 上限、联系方式绑定、优惠券/充值未开放路径、权限与限流、幂等、消息持久化、SSE、旧 Token 撤销、B4 未持久化/响应后结算/未知上游结果恢复均通过。模拟上游收到 12 次请求，其中 3 次为流式；本地完成 20 条消息持久化和 11 笔结算。
- [x] 复验后 `scripts/ai-local-env.sh status` 确认隔离 MySQL、Redis、后端、前端和模拟模型仍运行，代理指向 `http://127.0.0.1:18080` 的本地后端。
- [x] 2026-10-19 后续再次运行 `bash scripts/local-release-preflight.sh`：后端全量 101 项测试、0 失败、0 错误、2 项 Rabbit opt-in 跳过；前端 `npm run build` 和默认完整 `verify-chat` 通过。脚本最后因生产密钥门禁退出，且没有连接服务器、传输镜像、推送或部署；`scripts/local-release-preflight.sh` 已设为可直接执行。
- [x] 历史记录：2026-10-19 那次本机发布预检因开发机没有服务器专用 `.env.prod` 而停在密钥门禁；当时没有连接、传输镜像、推送或部署。用户随后确认三项密码已在服务器配置，但最新只读核验发现 `REDIS_PASSWORD` 为空；更新后的 Compose 严格拒绝空值，须由凭据所有者在服务器配置后再做发布前验收。不要把服务器密码复制到开发机或聊天中。

### 本地开发暂停检查点（2026-10-19）

- [x] 2026-10-19 Google Cloud OAuth 核对：当前 `Gemini API` 项目的 OAuth 同意屏幕处于“外部/测试”，本地 Web 客户端只登记 `127.0.0.1` 来源和回调。生产项目“Lingdian AI Production”（ID `lingdian-ai-production`）已单独创建；尚未配置同意屏幕、OAuth 客户端或付费资源。用户已提供正式站点 URL，但线上隐私/条款路径仍返回与根首页相同的 SPA HTML；真实法律正文、运营主体和公开邮箱确认后再完成品牌与 OAuth 客户端，没有复用本地客户端。
- [x] 2026-10-19 本地浏览器只读视觉复验：`/login` 实际渲染用户名/密码、验证码输入框与图片、刷新按钮、“5 分钟内有效”提示、登录与 Google 登录入口；页面结构与截图显示一致。本次未填写账号、解答验证码或提交登录。
- [x] 2026-10-19 完整 `scripts/local-release-preflight.sh --local-only` 复验：本地 `verify` 通过；后端 34 个 Surefire suite 共 101 项、0 失败、0 错误、2 项 Rabbit opt-in 跳过；`npm run build` 通过；`verify-chat` 覆盖登录验证码错误/重放拒绝、模型可用性、联系方式、路径权限、充值关闭、聊天幂等与持久化、SSE、限流、管理员权限、结算重放及 B4 故障恢复，模拟上游收到 12 次请求（3 次 SSE），20 条消息持久化、11 笔结算完成。另 `CorsConfigTest` 3 项及生产安全配置正反向门禁通过；后端运行时 OPTIONS 对本地允许来源返回精确 ACAO/ACAC，对陌生来源返回 403。本地预检本身不连接生产；后续公网 CORS 发布验收见下文。
- [x] 随后单独重跑 `RabbitEventPublisherIntegrationTest`：使用隔离的 RabbitMQ 4.2.9 容器和随机 loopback 端口，两项测试均通过（publisher confirm/持久化/mandatory unroutable return；listener 分派后 ACK），Surefire 记录 2 项、0 失败/错误/跳过。测试容器已删除，没有修改默认本地栈或接触生产 broker。
- [x] 复验后本地 MySQL/Redis、后端、前端和 mock 均运行，前端 API 代理仍指向 `http://127.0.0.1:18080`；`.runtime/local.env` 权限 `600`。`scripts/check-staged-env-secrets.py` 通过；本地检查点提交后针对当前 `main` 的 pre-push 扫描通过。远端 `origin/main` 仍为旧历史，因此这些本地检查不授权推送。

- [x] C3 会话过期恢复单测复验通过：`npm run test:auth-session`，7 项通过、0 失败，覆盖首次 HTTP 401、刷新后重试仍为 HTTP 401 时清理会话，以及非认证错误保留会话；`npm run build` 和 `git diff --check` 通过。构建仍提示独立图表 chunk 超过 500 KB，E3 已记录这些 chunk 不在聊天入口静态依赖图内。
- [x] 本轮 `./scripts/ai-local-env.sh verify` 通过：MySQL/Redis 健康、后端启用模型列表、未登录聊天 401 边界、前端首页和本地代理模型列表均正常。
- [x] 本轮 `./scripts/ai-local-env.sh verify-chat` 以退出码 0 通过，覆盖验证码错误/重放拒绝、Token 上限、联系方式绑定、充值关闭、路径越界、聊天与幂等、权限/限流、SSE、旧 Token 撤销及 B4 结算故障恢复；模拟上游收到 12 次请求（3 次流式），本地持久化 20 条消息并完成 11 笔结算。
- [x] `./scripts/ai-local-env.sh status` 确认 MySQL、Redis、后端、前端和本地 mock 均运行，前端代理仍指向 `http://127.0.0.1:18080`。
- [x] 2026-10-19 模型可用性契约端到端复验：定向 Maven 测试 7 项、前端选择逻辑测试 3 项通过，`npm run build` 与 `bash -n scripts/ai-local-env.sh` 通过；`verify-chat` 真实登录后确认 `/platform-model/available` 的 `chatEnabled` 类型/脱敏、本地 mock 可用和缺少上游模型不可用，且拒绝请求不写消息/计费意图、不改点数、不访问 mock。普通模型 SSE/非流式回归仍通过；浏览器实际展开选择器确认未接入模型显示“暂未接入”并禁用。该段记录当时真实凭据尚未注入的状态；后续真实 DeepSeek 本地联调已完成，见上方“真实 DeepSeek 本地上游验收（2026-10-19）”；浏览器原安全中心标签已恢复。
- [x] 紧接模型可用性修复后重跑后端全量 `mvn -f Aiplatform-demo/pom.xml -q -Dapp.messaging.rabbit-enabled=false test`（先加载 `.runtime/local.env`）：34 份 Surefire 报告、99 项测试，0 失败、0 错误、2 项按 `rabbit.it` 条件跳过；Rabbit 两项 opt-in 测试已在前一轮连接隔离本地 broker 单独通过。前端 `test:auth-session` 7 项、`test:model-availability` 3 项、生产构建均通过；随后 `./scripts/ai-local-env.sh verify` 再次通过，MySQL/Redis/backend/web/mock 仍运行，代理保持 local，`git diff --check` 通过。
- [x] 本轮强化基础 `verify` 的模型诊断：后端与前端代理响应必须包含布尔 `chatEnabled` 且不泄露 API Key，并分别报告已启用、可对话、未接入数量。本机真实输出为 17 个启用、1 个可对话（Local Mock）、16 个尚未配置/接入；`verify`、脚本语法、差异检查和凭据扫描均通过。
- [x] 2026-10-19 DeepSeek 凭据复用路径：模型记录凭据优先；记录为空时仅 `deepseek` provider 可回退读取后端环境变量 `DEEPSEEK_API_KEY`。可用性判断与聊天调用共用解析规则，环境变量只透传到后端容器、不进入 API 响应。定向测试 9 项通过；`switch local` 已停旧进程、按新源码重建并启动；`verify` 通过（17 个启用、1 个可对话、16 个未接入）；完整 `verify-chat` 通过（12 次模拟上游请求、3 次 SSE，20 条消息、11 笔结算）；全量 Maven 101 项通过、0 失败/错误、2 项 Rabbit opt-in 测试因未设置 `rabbit.it` 跳过；两份 Compose 解析及 `git diff --check` 通过。该次验证仅覆盖本地 mock；后续真实本地供应商端到端验收见上方“真实 DeepSeek 本地上游验收（2026-10-19）”。
- [x] 2026-10-19 DeepSeek 凭据复用路径：模型记录凭据优先；记录为空时仅 `deepseek` provider 可回退读取后端环境变量 `DEEPSEEK_API_KEY`。单测 9 项通过；新源码已由 `switch local` 构建并加载；基础 `verify`、默认完整 `verify-chat`、全量 Maven 101 项（0 失败/错误，2 项 Rabbit opt-in 因未设 `rabbit.it` 跳过）均通过；两份 Compose 解析、脚本语法和差异检查通过。该次验证仅覆盖本地 mock；后续真实本地供应商端到端验收见上方“真实 DeepSeek 本地上游验收（2026-10-19）”。
- [x] DeepSeek 环境回退 HTTP 验收：新增 `AI_VERIFY_DEEPSEEK_FALLBACK=true ./scripts/ai-local-env.sh verify-chat` 专项模式及本地 `/responses` mock。测试临时停用原 3 条 DeepSeek 模型、插入空 `api_key` 的本地测试模型并只注入合成 Key；HTTP 确认模型 `chatEnabled=true` 且响应不含凭据字段，非流式与 SSE 聊天均以 Bearer 认证到达 `/responses`、携带 `max_output_tokens=256`，两轮回复均落库并各结算一次。退出清理删除测试模型、恢复原模型状态并重启后端；随后 DB 确认测试模型数为 0、原 3 条 DeepSeek 状态恢复启用，基础 `verify` 回到 17 个启用、1 个可对话、16 个未接入。本地模式完整回归通过：14 次 mock 请求、4 次 SSE，24 条消息、13 笔结算。测试 Key 只在测试进程内使用，不访问真实上游。
- [x] 2026-10-19 当前工作区全量复验：后端 `mvn -Dapp.messaging.rabbit-enabled=false test` 为 99 项、0 失败、0 错误、0 跳过；前端 `test:auth-session` 7 项、`test:model-availability` 3 项通过，`npm run build` 成功；登录页 CAPTCHA API 与错误码实测通过；`./scripts/ai-local-env.sh verify` 通过。后端测试使用临时 MySQL 数据库副本和独立 Redis 容器，二者均已清理，本地开发栈仍健康且代理保持 local。
- [x] 2026-10-19 本地检查点提交为 `802f5ed`，仅包含本计划与删除含非占位密码字段的旧 `.env.example`；现有其他代码/配置改动保持在工作区。
- [x] 2026-10-19 最新本地全链路复验：`./scripts/local-release-preflight.sh --local-only` 通过；34 个 Surefire suite 共 101 项（0 失败、0 错误、2 项 Rabbit opt-in 跳过），前端生产构建通过；`verify-chat` 覆盖 CAPTCHA 错误/重放、模型可用性、联系方式、充值关闭、越权路径、幂等/SSE、限流/管理员权限及 B4 恢复，12 次模拟上游请求（3 次 SSE）、20 条消息持久化、11 笔结算。结束后 MySQL、Redis、backend、web、mock 仍运行，代理为 local。
- [ ] 工作区仍有用户保留的未提交文件，本轮未纳入整改提交。现有容器内 JAR/静态资源发布路径已经实现并受法律内容门禁保护；线上发布仍待完成 Google 法律页及 OAuth 资料、必要的生产外部服务联调、旧数据完整性核验和旧会话兼容安排。MySQL 与二级口令已确认非空；Redis 认证按用户选择暂缓，不作为本轮擅自变更项。

- [x] 2026-10-19 本地 `main` 整改提交已推送。首次安全推送被 GitHub 拒绝：历史中的根目录 `mysql8.tar` 为 244,640,256 bytes，远端 `main` 未改变。用户当前工作区没有该归档文件；本地 `main` 历史已移除该文件并保留 `refs/backup/main-before-large-file-removal` 回退引用；pre-push 检查新增 100 MiB blob 限制，模拟回归通过。随后使用 `--force-with-lease` 成功更新远端 `main`，并通过 `git ls-remote` 与 `git fetch` 确认远端、本地分支均为 `d4e4a63`。尚未部署。

### 生产发布验收（2026-10-19，代码提交 `9df82a6`）

- [x] 本地发布门禁先于生产操作完成：`./scripts/local-release-preflight.sh --local-only` 通过；Maven 101 项测试通过（2 项 RabbitMQ opt-in 测试按条件跳过），前端构建及 `verify-chat` 通过，覆盖验证码、模型可用状态、聊天幂等/归属/限流、SSE、点数结算与恢复。
- [x] 将线上 `ai_gateway` 的仅结构 mysqldump（无业务数据）导入现有本地 MySQL 临时库，按 v8→v17 连续执行两遍；9 个必需列、7 张新表、4 个新索引及 `points_bill.change_type` 长度 32 均通过核对，临时库已删除。
- [x] 线上先在权限 `700` 的 `/root/lingdian/backups` 备份数据库、当前后端 JAR 与前端静态文件；备份文件均为 `600`，数据库与前端压缩包通过完整性检查。备份时间为 `20261019T065519Z`。
- [x] 线上现有 MySQL 容器内执行 v8→v17 迁移并核对结构。用户确认旧限时赠点按永久点数保留；迁移前后用户点数合计均为 `53934.000`，旧限时账本均为 6 笔、`790.000` 点，未回填或改写旧余额。
- [x] 后端 JAR 与前端静态资源已部署到原有容器；后端在原容器内重启，未构建/加载镜像、未创建或替换容器。后端、前端、MySQL、Redis 容器 ID 与发布前一致；Redis/MySQL 未重启，Redis 认证按用户选择继续暂缓。
- [x] 发布后两条正式 HTTPS 域名的 `/api/health` 与登录验证码接口均返回 200；前端首页及其引用的 15 个 JS/CSS 资源均返回 200；Nginx 配置检查成功。新文件恢复扫描将原先 2 个过期 `PARSING` 任务恢复为 `SUCCESS`，目前 58 个文件解析任务均为成功。
- [ ] 本次没有发起新的线上模型请求或付费聊天；本地模拟上游和真实凭据验收记录仍按各自章节区分。Google 生产 OAuth 的域名、首页及隐私/条款 URL 已由用户提供；线上 `/privacy` 与 `/terms` 仍回退到欢迎页，本地页面目前是待确认草案，需完成数据删除流程、补齐运营主体/公开联系邮箱并确认法律内容后才能部署和配置独立生产客户端。SMTP/短信、RabbitMQ、OSS 尚未完成线上配置与联调。直充仍按用户决定关闭。
- [ ] 旧 Token 因缺少设备绑定映射按失败关闭策略需要用户重新登录；前端本地失效提示已完成，生产发布前仍需安排公告和兼容窗口。历史邮箱不会自动视为已验证，用户需重新验证。旧伪 OAuth/TOTP Redis 数据不迁移。生产 Redis 仍无认证且未公开宿主机端口，`.env.prod` 权限为 `600`；Redis 认证保持暂缓，启用前需安排现有 Redis/backend 的一致配置和重启。
- [x] 2026-10-19 可选 Rabbit 健康修复已发布：本地全量预检通过后，提交 `e435d70` 已推送；权限 `600` 的原 JAR 备份为 `/root/lingdian/backups/backend-pre-rabbit-health-20261019T073350Z.jar`，新 Jar SHA-256 与本地产物一致，只在原 `lingdian-backend` 容器内替换 `/app/app.jar` 并重启，未新建镜像/容器，也未重启 MySQL、Redis 或 frontend。backend 容器 ID 保持不变；发布后公网 `/api/actuator/health` 返回 HTTP 200、`status=UP`，验证码接口返回 HTTP 200、业务码 200、图片数据存在且 TTL 300 秒。本次未发起线上模型请求或付费聊天。Google 生产 URL 已收齐，但 `/privacy` 与 `/terms` 当前回退到欢迎页，发布真实法律正文并核验前不能配置生产 OAuth 客户端。
- [x] 2026-10-19 CORS 加固已保存为本地 `main` 提交 `b14415c`；远端 `origin/main` 仍保留旧版引用，本次没有向远端推送。已备份旧 JAR `/root/lingdian/backups/backend-pre-cors-20261019T075800Z.jar` 和配置 `/root/lingdian/backups/env-prod-pre-cors-20261019T075800Z.bak`（均权限 `600`），候选 JAR SHA-256 为 `bbd040343ce9ddf417c5c6aefabe2abb1f76c9dbd2fde95410a165208ac647a0`；服务器 `.env.prod` 权限保持 `600`，`ALLOWED_ORIGINS` 已设为正式两个域名。由于 Redis 曾被正常 SIGTERM 停止，已启动同一 Redis 容器并确认数据集载入后 `PONG`；健康恢复后只在原 backend 容器内替换 JAR并重启，backend/Redis/MySQL/frontend 容器 ID 均保持不变。公网两个正式来源 OPTIONS 返回精确 ACAO 且 `ACAC=true`，陌生来源无 CORS 允许头并返回 403；Actuator `UP`、登录验证码接口业务码 200 且图像存在。没有发起线上模型请求或付费聊天。
- [ ] Google 生产 OAuth 的域名、首页及隐私/条款 URL 已由用户提供，独立项目 `lingdian-ai-production` 已创建；线上 `/privacy` 与 `/terms` 当前在浏览器中均落回 `/welcome`，本地法律页仍是待确认草案。运营主体全称及公开客服/隐私邮箱待用户补充；账号暂按停用处理，数据删除范围待用户确认。完成真实法律内容和上述信息后再配置品牌及生产 OAuth 客户端。SMTP/短信、RabbitMQ、OSS 尚未完成线上配置与联调；直充仍按用户决定关闭。
- [ ] 2026-10-19 最新生产只读复核：线上 `/privacy` 与 `/terms` 均重定向到 `/welcome`，当前不能用于 Google 审核；现有 7 个 `lingdian-*` 容器均运行，未更换容器。服务器 `.env.prod` 为 `0600`，MySQL/二级口令已设；Redis 口令为空（按用户决定暂不启用认证）；生产 Google OAuth、RabbitMQ、OSS、SMTP、短信变量均未设。此次仅查询变量是否存在，未输出任何密钥；未读生产数据库 Key，也不要求申请新 DeepSeek Key。当前生产 JAR SHA-256 为 `bbd040343ce9ddf417c5c6aefabe2abb1f76c9dbd2fde95410a165208ac647a0`，本地已验收账号停用候选 JAR 为 `58f9924f90bd542c76d7e947ff80e82f00f1c6220adb72da371ea0a884fb3036`，账号停用功能仍未部署。远端 `main` 与本地已同步至 `d58fa3b`；本次未改生产环境。
- [x] 2026-10-19 Google 生产资料与账号语义确认：正式域名 `https://lingdian-ai.top/`、首页 `/welcome`、隐私政策 `/privacy`、服务条款 `/terms`；使用当前 Google 账号作为 OAuth 支持邮箱，生产 OAuth 放在独立项目 `lingdian-ai-production`。运营主体全称及公开客服/隐私邮箱由用户后续补充；账号按停用处理，数据删除范围留待后续确认。法律页仍是本地草案，OAuth 生产客户端和法律页面发布继续受此门禁阻挡。
- [x] 2026-10-19 新增复用现有容器的受控发布脚本：默认 `--plan` 不联网；`--apply` 先要求法律页确认并拒绝草案标记，再运行本地全量预检，远端只用 `docker inspect/cp/exec/restart`，备份原 JAR/静态资源、核验七个容器 ID 与非 backend 启动时间，并在健康/验证码/静态资源检查失败时回滚。`bash -n` 与 `python3 scripts/test-existing-container-deploy.py` 通过；当前法律页尚未完成，因此未连接服务器、未部署。
- [x] 2026-10-19 最新本地与远端状态核验（提交 `d31d134`）：`./scripts/local-release-preflight.sh --local-only` 通过；35 个 Surefire suite 共 105 项、0 失败/错误、2 项 Rabbit opt-in 跳过；前端构建成功（ECharts 仍有既有大 chunk 警告）；`verify-chat` 通过 12 次模拟上游请求（3 次 SSE）、20 条消息和 11 笔结算，覆盖验证码错误/重放、幂等、权限/限流及 B4 故障恢复。随后 `./scripts/ai-local-env.sh verify` 通过，MySQL、Redis、backend、web、mock 仍健康，代理为 local；浏览器登录页显示图形验证码、刷新按钮和 5 分钟提示。远端只读核验确认 7 个容器均运行、`.env.prod` 为 `0600`；MySQL/二级口令已设，Redis 密码为空（按用户决定暂不认证），Google/RabbitMQ/OSS/SMTP/短信配置未设置。公网 health 为 200；浏览器访问 `/privacy`、`/terms` 均导航回 `/welcome`，故没有部署代码或法律草案。`origin/main` 与本地同步至 `d31d134`；没有读取或输出线上 API Key/密码，也没有发起线上 AI 或付费请求。
- [x] 2026-10-19 Google 生产控制台复核：浏览器当前选中项目 `lingdian-ai-production`，Google Auth Platform 显示“尚未配置”，尚无生产 OAuth 客户端。生产项目与本地测试项目已分离；等待运营主体全称、公开联系邮箱和法律内容确认后再继续设置品牌、同意屏幕与回调。

### 2026-10-19 本地复验续记

- [x] 当前工作区复跑后端全量测试：35 个 Surefire suite、105 项测试、0 失败、0 错误、2 项 Rabbit opt-in 测试跳过。测试在停止本地 backend 期间通过；backend 同时运行时此前复现一次 `FileParseTaskRecoveryLifecycleTest` 数据库死锁，需把并发运行条件保留为测试稳定性注意项，不能声称该死锁已通过代码修复。
- [x] `npm run build`、`./scripts/ai-local-env.sh verify` 和完整 `./scripts/ai-local-env.sh verify-chat` 均通过；后者覆盖验证码拒绝、模拟流式对话、权限/限流、持久化与点数结算。本地栈已恢复运行并保持 `local` 代理；这些聊天验收使用本地 mock，不代表 Google OAuth 或线上模型验收。
- [x] 本地浏览器再次确认登录表单显示图形验证码、5 分钟有效提示和 Google 登录入口；`/privacy`、`/terms` 本地路由可见，但内容仍明确标为草案。
- [ ] 生产 Google OAuth 仍未完成：测试受众和授权域名已在控制台保存；生产 Web 客户端 JSON 已收到但回调/来源登记不匹配。Google Auth Platform > Clients 中将授权来源设为 `https://lingdian-ai.top`，移除 HTTP 来源；将授权重定向 URI 设为 `https://lingdian-ai.top/oauth/callback`，然后重新导出 JSON。凭据未写入服务器；线上 OAuth 授权接口仍返回业务码 503，没有部署或改动线上容器。

### 2026-10-20 Google OIDC 线上测试配置续记

- [x] 用户在生产项目 `lingdian-ai-production` 确认现有 Web 客户端的授权来源为 `https://lingdian-ai.top`、授权重定向 URI 为 `https://lingdian-ai.top/oauth/callback`。本地下载的 JSON 仍带旧 URI/HTTP 来源，但客户端 ID 与控制台一致；服务器回调值按用户刚确认的控制台配置显式设置，未更换或撤销密钥。
- [x] 在服务器 `.env.prod` 中安全写入 `GOOGLE_OAUTH_CLIENT_ID`、`GOOGLE_OAUTH_CLIENT_SECRET`、`GOOGLE_OAUTH_REDIRECT_URI`，原子回读校验通过；文件权限保持 `0600`，更新前受限备份也为 `0600`。没有输出密钥，也未读取或修改其他生产凭据。
- [x] 现有 `lingdian-backend` 容器原先未注入 Google 环境变量，且 Docker 不会因重启自动重读主机 `.env.prod`。为遵守复用容器、不创建镜像/容器的约束，向原容器 `/app/application.properties` 写入仅含上述三项的外部 Spring 配置（权限 `0600`）并重启；容器 ID 保持不变。Spring Boot 3.2 默认从当前工作目录加载外部 `application.properties`。
- [x] 发布后原 backend 容器状态为运行，公网 `/api/actuator/health` 返回 HTTP 200/`UP`；`/api/auth/oauth/url?provider=google&purpose=login` 返回 HTTP 200/业务码 200。生成的 Google 授权 URL 使用确认的客户端 ID 和回调，包含 `openid email profile`、PKCE `S256`、state、nonce；浏览器绑定 Cookie 带 `Secure`、`HttpOnly`、`SameSite=Lax`。
- [ ] 真实登录往返仍待完成：浏览器已进入 Google 账号验证页并选中已加入测试名单的账号；Google 要求使用该账号的通行密钥验证。用户需完成验证后再确认回调落地、账号登录成功。OAuth 应用当前为外部测试模式；公开用户上线仍受正式隐私政策、服务条款、运营主体和公开联系邮箱门禁约束。
