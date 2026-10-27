---
version: alpha
name: "凌点 AI"
description: "面向中文用户的 AI 工作台、账户中心和运营控制台，使用轻量紫蓝玻璃层次表达状态和数据。"
colors:
  primary: "#7C3AED"
  secondary: "#2563EB"
  background: "#F8FAFC"
  text: "#0F172A"
  muted: "#64748B"
  success: "#059669"
  warning: "#D97706"
  danger: "#E11D48"
typography:
  sans:
    fontFamily: "Inter, SF Pro Text, PingFang SC, Microsoft YaHei, sans-serif"
  mono:
    fontFamily: "JetBrains Mono, SF Mono, Consolas, monospace"
rounded:
  DEFAULT: "0.75rem"
  md: "1rem"
  lg: "1.25rem"
spacing:
  section-gap: "1.5rem"
  page-max: "92.5rem"
components:
  button: {}
  card: {}
  dialog: {}
  table: {}
  input: {}
---

# 凌点 AI UI Design Context

## Product surface

凌点 AI 当前的核心界面是面向中文用户的 AI 工作台、个人中心和管理员控制台。界面需要让用户清楚知道三件事：这次回答是否消耗点数、会员权益和到期时间是什么、历史数据是否完整可追溯。

## Visual direction

- 延续现有深色/浅色双主题、紫蓝渐变和半透明卡片的视觉语言；首次访问默认明亮主题，用户主动切换后记住选择，深色主题仍可用。
- 使用现有 CSS 变量和组件样式，不在单个页面引入另一套颜色或圆角体系。
- 页面以紧凑的信息密度为主：概览卡片用于摘要，表格/分页用于可检索的历史记录。
- 管理控制台采用控制室式信息架构：桌面端使用分组侧栏承载模块层级，内容区优先展示健康状态、核心指标和可操作的数据表；移动端将侧栏折叠为可横向滚动的模块导航。
- 使用活动热力图保留近一年数据，但网格按容器自适应伸展，保证数据密度和两侧留白平衡；图表保持现有紫蓝渐变和 ECharts 视觉语言。
- 系统无衬线字体用于正文；用户 ID、流水号、金额和点数等精确值可使用等宽字体或数字对齐样式。
- 关键操作使用清晰的成功/警告/错误状态；封禁等破坏性操作必须先确认。

## Interaction principles

- 点数、会员、管理员日志和用户管理都以服务端返回结果为准，不能把本地乐观状态当成成功记录。
- 有总数的列表必须提供分页；筛选条件变化时回到第一页，并显示当前分类或筛选状态。
- 新增的业务操作使用应用内弹窗和 Toast，不使用浏览器原生 `prompt`/`confirm`。
- 加载、空数据、失败和重试都要有可见状态；提交按钮在请求期间不可重复点击。
- 会员续费和升级要明确说明结果；低于当前等级的方案不可购买。

## Component ownership

- 用户点数和记录：`Aiplatform-web/src/pages/UserCenterPage.tsx`
- 会员购买和续费：`Aiplatform-web/src/pages/RechargePage.tsx`
- 管理员用户操作：`Aiplatform-web/src/pages/AdminPage.tsx`、`Aiplatform-web/src/components/AdminUserActionDialog.tsx`
- 通用确认：`Aiplatform-web/src/components/ConfirmDialog.tsx`

## Avoid

- 不用“AI 对话更聪明”作为唯一产品卖点。
- 不隐藏点数扣费、会员到期、封禁结果或管理员操作结果。
- 不为一次性页面增加重量级前端状态库或新的 UI 框架。
