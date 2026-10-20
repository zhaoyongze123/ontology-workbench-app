# 开发与交付

使用 `main` 作为可检查的集成分支，未完成工作放在功能分支并通过 Pull Request 说明状态。

分支命名：`feat/multiuser-auth`、`fix/graph-access-check`、`docs/workbench-integration`、`chore/ci`。提交使用 `feat:`、`fix:`、`test:`、`docs:`、`refactor:`、`chore:`，每次提交围绕一个可解释的变化。

修改前阅读方案和调用链。PR 描述应说明具体问题、变化后的行为、数据库影响、验证证据及尚未验证事项。权限变更必须包含越权负向用例；协议变更必须同时核对前后端和 App 契约。

运行 `make verify`。涉及实际 AGE / MyBatis 事务、数据库权限或会话重启恢复时，再按验证文档执行隔离库集成测试。CI 默认不读取本机凭据，也不自动部署。

数据库使用新增 Flyway 版本，不改写已应用迁移。发布包通过构建生成，不将 ZIP、JAR、node_modules、数据库、运行日志和私有配置提交进 Git。

版本使用 `v主版本.次版本.修订号`。只有完成对应验收的版本才创建发布标签；历史 `0.1.1` App 模板与正在开发的新多用户功能分别标明状态。

