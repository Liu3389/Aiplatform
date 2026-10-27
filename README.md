# 凌点 AI

凌点 AI 是一个由 Java 后端和 Web 前端组成的 AI 平台。仓库包含平台业务源码、本地开发环境、部署配置、数据库脚本和项目文档。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `Aiplatform-demo/` | Spring Boot 后端源码、资源文件、数据库迁移与后端测试 |
| `Aiplatform-web/` | Web 前端源码、静态资源与前端构建配置 |
| `scripts/` | 本地环境、发布辅助、数据库与运维脚本 |
| `docker-compose.local.yml` | 本地隔离开发栈配置 |
| `docker-compose.server.yml`、`docker-compose.prod.yml` | 部署环境配置；运行前先准备受控环境变量 |
| `docs/` | 设计约定、集成说明、部署说明和调研资料 |
| `Aiplatform-web/public/` | 正式产品使用的图标、品牌图片等静态资源 |

## 本地开发

本地环境脚本会在被 Git 忽略的 `.runtime/` 下生成运行配置、日志与上传文件。请勿把真实凭据写进源码或示例配置。

```bash
./scripts/ai-local-env.sh up
./scripts/ai-local-env.sh status
./scripts/ai-local-env.sh verify
./scripts/ai-local-env.sh down
```

前端依赖和命令见 `Aiplatform-web/package.json`；后端构建入口见 `Aiplatform-demo/pom.xml`。首次启动前需准备 Java、Node.js、Docker Compose 等项目运行依赖。

克隆后可执行 `./scripts/install-git-hooks.sh` 安装本仓库的提交与推送检查钩子。

## 配置与数据库

- `.env.prod.example` 等示例配置只提供配置项模板；实际值放在本地忽略文件或受控部署环境中。
- 数据库迁移按版本顺序执行。`rebuild.sql` 面向初始化或开发场景，不应用来覆盖已有生产数据库。
- 生产发布前应确认目标环境变量、数据库备份、迁移内容和回滚方式；具体约定见 [`docs/deployment/README.md`](docs/deployment/README.md)。

## 文档索引

从 [`docs/README.md`](docs/README.md) 查看文档分类。设计约定、第三方登录配置和平台方向调研分别归档在 `docs/design/`、`docs/integrations/` 与 `docs/research/`。

## 本地资料边界

`design-previews/` 用于本地展示和比较中的页面方案，目前不属于正式产品前端；它保持在本机，不随本仓库提交。个人上传文件、构建产物、日志、运行数据库和 IDE 配置也由 `.gitignore` 排除。正式品牌图标和产品静态资源仍保留在 `Aiplatform-web/public/`。

`cq666-game/` 是独立的上游游戏源码检出，不属于本平台仓库的提交内容；需要时应在其自身仓库中管理。

## 许可证

项目自有代码按根目录 [`LICENSE`](LICENSE) 中的 MIT 许可发布。该许可不覆盖第三方商标、登录图标、依赖或其他素材；相关内容仍受其原有许可和平台规则约束，见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
