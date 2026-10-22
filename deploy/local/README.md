# Docker 部署共用数据库脚本

生产服务的唯一部署入口是 [`../ontology.sh`](../ontology.sh)，完整安装、升级、反向代理、备份恢复和 App 打包流程见 [Docker 部署指南](../../docs/deployment.md)。本目录不再包含宿主 Java 或 Nginx 的启动模板；Docker Compose 的迁移命令会按顺序执行数据库权限准备与收口脚本。

- `prepare-db-roles.sql`：迁移前创建受限运行、迁移和初始化身份，并准备迁移窗口权限。
- `finalize-db-permissions.sql`：迁移后撤销迁移临时权限并校验最终角色策略。

两个 SQL 文件由 Compose 维护镜像与一次性 PostgreSQL/AGE 集成测试共同使用。不要直接对生产数据库手工执行，也不要把私有凭据写进脚本。

本机仅用于 HTTP smoke 的可选 Cookie 覆盖文件为 `deploy/private/api-config/ontology.security.cookie-secure=false` 和 `ontology.security.cookie-same-site=Lax`；不要在生产 HTTPS 部署中创建这两个覆盖文件，默认 Secure + SameSite=None 配置应保持有效。
