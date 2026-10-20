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

