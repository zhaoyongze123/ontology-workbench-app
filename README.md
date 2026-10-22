# Ontology Workbench App

Ontology 独立图谱应用及 Workbench App 接入工程。前端使用 Umi / React / Ant Design，后端使用 Java 17 / Spring Boot，业务表与图数据存储在 PostgreSQL / Apache AGE。

当前是本机开发测试候选版本。Workbench 接入采用 Java 契约的 `0.2.0-rc.1` 源模板；真实平台联调与原库迁移验收须另行完成，不能复用历史 `0.1.1` 包。Docker 单机交付入口、首次管理员初始化、升级及备份恢复见 [部署指南](docs/deployment.md)；Workbench App 构建与平台验收见 [候选版交付指南](docs/engineering/workbench-app-0.2.0-rc.1-release-and-manual-validation.md)。

## 目录

| 目录 | 内容 |
| --- | --- |
| `apps/server/` | Java 后端、多用户安全模块、数据库迁移与测试 |
| `apps/web/` | Ontology 页面、双登录入口、项目协作界面 |
| `integrations/workbench/` | App 配置模板、工具脚本与打包契约测试 |
| `docs/design/` | 已确认的功能及技术方案 |
| `docs/engineering/` | 工程流程、验证记录与后续任务 |
| `deploy/` | Docker 单机部署 Compose、管理入口、配置模板与数据库权限脚本 |
| `scripts/` | 仓库检查与本地开发辅助脚本 |

## 开发与检查

本机开发需要 Java 17、Maven、Node.js 22、Python 3 和 Docker。服务器部署只需要 Docker Engine、Docker Compose v2 和 POSIX shell；真实配置在服务器受保护目录创建，不能复制进仓库。

```sh
cd apps/web
npm ci
cd ../..
make verify
```

`make verify` 检查仓库内容、运行 Java 测试、构建前端及验证 App 模板。需要数据库的集成测试默认跳过，必须显式指向隔离测试库并传入环境凭据；详情见 [验证说明](docs/engineering/validation.md)。

服务运行、首次管理员初始化、反向代理、备份恢复和 App 打包统一使用 [Docker 部署指南](docs/deployment.md)。Compose 镜像构建前端多用户静态产物并启动 Java API；不再支持从仓库模板手动启动 Java/Nginx 服务。

## 方案与协作

- [已确认方案总稿](docs/design/Ontology-独立App多用户方案总稿-v1.0.md)
- [工程管理约定](CONTRIBUTING.md)
- [当前实施状态](docs/engineering/status.md)
- [安全与私有配置](SECURITY.md)

