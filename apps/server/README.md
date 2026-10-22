# Ontology Server

Java 17 / Spring Boot 服务源码与测试。`ontology-common` 提供通用配置，`ontology-core` 提供身份、项目权限和图谱业务；PostgreSQL / Apache AGE 迁移与权限脚本位于 `deploy/local/`，由 Docker 部署 CLI 和隔离 IT 使用。

```sh
mvn -B verify
```

服务部署、运行配置、首次管理员初始化和升级恢复统一见根目录 [Docker 部署指南](../../docs/deployment.md)。本目录不再提供手工 Java 启动配置；数据库集成验证使用 `bash ontology-it/run.sh` 创建的一次性 PostgreSQL/AGE 环境。

已有 Flyway 脚本不得改写。禁止将原数据库作为集成测试目标。
