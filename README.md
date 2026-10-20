# Ontology Workbench App

Ontology 独立图谱应用及 Workbench App 接入工程。前端使用 Umi / React / Ant Design，后端使用 Java 17 / Spring Boot，业务表与图数据存储在 PostgreSQL / Apache AGE。

当前是本机开发测试版本。已实现密码登录、数据库持久会话、项目角色权限、站内成员邀请和受限只读图查询；Workbench OIDC 的 Java 迁移已写入代码，真实平台回归尚待完成。Agent OAuth2 令牌、刷新轮换及新上传包仍在后续实施范围内，历史 App 模板不能当作已验收的新版本。

## 目录

| 目录 | 内容 |
| --- | --- |
| `apps/server/` | Java 后端、多用户安全模块、数据库迁移与测试 |
| `apps/web/` | Ontology 页面、双登录入口、项目协作界面 |
| `integrations/workbench/` | App 配置模板、工具脚本与打包契约测试 |
| `docs/design/` | 已确认的功能及技术方案 |
| `docs/engineering/` | 工程流程、验证记录与后续任务 |
| `deploy/local/` | 数据库、反向代理与应用配置示例 |
| `scripts/` | 仓库检查与本地开发辅助脚本 |

## 开发与检查

需要 Java 17、Maven、Node.js 22、Python 3 和 Docker。真实配置在本机受保护目录创建，不能复制进仓库。

```sh
cd apps/web
npm ci
cd ../..
make verify
```

`make verify` 检查仓库内容、运行 Java 测试、构建前端及验证 App 模板。需要数据库的集成测试默认跳过，必须显式指向隔离测试库并传入环境凭据；详情见 [验证说明](docs/engineering/validation.md)。

前端多用户产物输出到 `apps/web/dist-multiuser/`。启动后端时使用 `multiuser` profile，应用配置参考 [本地模板](deploy/local/application.example.yaml)。不要将模板中的占位信息当成真实登录配置。

## 方案与协作

- [已确认方案总稿](docs/design/Ontology-独立App多用户方案总稿-v1.0.md)
- [工程管理约定](CONTRIBUTING.md)
- [当前实施状态](docs/engineering/status.md)
- [安全与私有配置](SECURITY.md)

