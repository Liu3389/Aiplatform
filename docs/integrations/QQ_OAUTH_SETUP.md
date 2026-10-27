# QQ 互联登录配置

后端实现采用 QQ 互联网站应用的 Authorization Code 流程：授权码由服务端兑换，服务端读取 OpenID 和昵称；OpenID 与平台用户关联。QQ AppKey 只能放在受控服务端配置中。

## QQ 互联控制台

在 [QQ 互联管理平台](https://connect.qq.com/manage.html#/) 以开发者身份创建网站应用并申请上线。应用审核要求网站和回调地址的备案信息与工信部登记一致，且登录按钮使用腾讯提供的标准图标。页面图标 `Aiplatform-web/public/qq-connect-login.png` 来自 QQ 互联官方标准 16×16 登录标识。

建议应用资料使用：

- 应用名称：凌点 AI
- 网站地址：`https://lingdian-ai.top`
- 回调地址：`https://lingdian-ai.top/oauth/callback`
- 授权范围：`get_user_info`（用于取得 QQ OpenID、昵称和头像）

以 QQ 互联控制台实际允许的地址格式和备案资料为准。授权回调必须与服务器配置完全一致；官方要求回调使用外网域名，不能使用 localhost 或端口，所以本地验收使用模拟响应，真实 QQ 登录必须通过已备案的公开回调域名测试。收到 AppID/AppKey 后，先确认应用审核状态为“已上线”。

## 服务端配置

数据库改动已按项目约定脚本化：`Aiplatform-demo/src/main/resources/sql/migration-v12-oauth-identities.sql` 创建第三方身份绑定表，`Aiplatform-demo/src/main/resources/sql/migration-v18-oauth-avatar.sql` 幂等地增加头像字段。新环境按版本顺序执行；已有环境先确认 v12 已执行，再执行 v18。不要用 `rebuild.sql` 覆盖线上数据库。

线上 `docker-compose.server.yml` 从受控环境变量读取 QQ 配置；本地和其他部署也可通过进程环境变量提供。`application.yml` 的外部 properties 导入是可选配置，不代表 QQ 配置必须写入该文件。不要把 AppKey 写入仓库或聊天：

```properties
QQ_OAUTH_APP_ID=控制台分配的AppID
QQ_OAUTH_APP_KEY=控制台分配的AppKey
QQ_OAUTH_REDIRECT_URI=https://lingdian-ai.top/oauth/callback
```

仅本地协议测试使用模拟响应，不需要真实 QQ 凭据；真实网页授权需要 QQ 互联控制台中的已审核网站应用和公开回调地址。`QQ_OAUTH_REVIEW_STATUS` 用于如实展示审核状态，审核通过前保持 `pending`，通过后改为 `approved`。审核状态不拦截授权跳转，因此可以提前开放入口并进行腾讯允许的调试；腾讯仍可能拒绝未审核应用的普通授权。

## 验证门禁

1. 本地运行 `mvn -Dtest=QQConnectClientTest,OAuthServiceLifecycleTest test` 和 `npm run build`。
2. 配置真实凭据后，在受控环境确认 `/auth/oauth/url?provider=qq&purpose=login` 返回授权地址；不要输出 AppKey、access token 或完整敏感请求 URL。
3. 通过真实 QQ 账号完成授权，检查回调、账号创建、重复登录、绑定/解绑和两步验证。
4. 以上本地验收通过并确认生产凭据后，再按项目既有部署门禁更新线上后端与前端。
