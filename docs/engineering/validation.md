# 验证方式

## 可复现的仓库检查

```sh
python3 -m pip install -r requirements-dev.txt
cd apps/web && npm ci && cd ../..
make verify
```

Java 测试中需要实际数据库的用例以环境条件隔离，默认不会连接开发者原库。App 测试针对配置模板和打包契约，不能代替 Workbench 真实上传测试。

## PostgreSQL / AGE 集成验证

`MultiuserDatabaseIntegrationTest` 只接受名称为 `ontology_multiuser_dev_20261020` 的隔离测试库。先通过受控迁移和本机 bootstrap 准备副本，并使用非超级用户运行账号，再将以下值放入本机环境，禁止提交：

- `ONTOLOGY_IT_JDBC_URL`：隔离库 JDBC URL，schema 为 ontology。
- `JDBC_USER`、`JDBC_PASSWORD`：副本运行账号。

```sh
mvn -f apps/server/pom.xml -pl ontology-core -am \
  -Dtest=MultiuserDatabaseIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

用例覆盖 MyBatis 项目记录与 AGE 图写入共同回滚，以及引号、反斜杠、换行、中文和美元符号的原样写入读回。通过回滚撤销测试新建对象，不删除原有图谱。

浏览器验收须检查登录、不同角色、过期/退出、错误状态和窄屏布局。实际检查过的状态与证据应在实施记录中注明，不能以 TypeScript 或打包成功代替浏览器检查。

## 本机端到端验证

只在已初始化的独立副本运行，脚本有端口限制；会创建并删除本次临时项目/图谱，邀请记录保留为审计历史。种子项目需保持方案约定的名称及角色，不能在原库运行。

```sh
python3 scripts/http_acl_smoke.py
# 需要安装普通 Playwright Node 库与 Chrome，不启动 MCP 服务。
PLAYWRIGHT_MODULE=/absolute/path/to/node_modules/playwright node scripts/browser_smoke.cjs
```

HTTP 补充验收 17 项已通过。浏览器脚本验证管理员、查看者、无项目用户和跨项目 OWNER 的实际列表，以及新建/路由/CSRF/退出；登录页另检查 390×844 视口。截图和无凭据报告输出到已忽略的 `output/`。这不覆盖 Workbench iframe、跨站 Cookie、真实 OIDC 或 Agent OAuth2。

首次建库 CI 已在 GitHub 真实运行，三项检查全部通过。CI 不携带本机数据库和 Workbench Secret，实际数据库与浏览器验收须在隔离本机单独执行。
