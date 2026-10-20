# Ontology Server

Java 17 / Spring Boot，业务数据与图数据使用 PostgreSQL / Apache AGE。`ontology-common` 提供存储与通用配置，`ontology-core` 提供身份、项目权限和图谱业务。

```sh
mvn -B verify
```

新多用户链路显式启用 `multiuser` profile，并加载仓库外的私有配置。参考 `../../deploy/local/application.example.yaml` 和根目录说明。迁移、初始化使用受控维护身份；日常运行账号不得具有 PostgreSQL 超级用户权限。

已有 Flyway 脚本不得改写。旧数据库升级前必须备份、恢复并核对 AGE 目录关联和图数据。禁止将原数据库作为集成测试目标。

本目录 Docker 和 `docs/docker/` 文件保留自原项目，尚未按新的身份与迁移边界验收；不能视为可直接上线的新部署方案。
