# 本机隔离验收配置

当前实际链路：浏览器 → Nginx `127.0.0.1:18083` → 静态前端 / Java `127.0.0.1:13002` → Docker PostgreSQL + AGE 的独立数据库副本。

- `application.example.yaml`：仓库外私有配置模板；启动时启用 `multiuser` profile，用 `--spring.config.additional-location=file:/absolute/private/application.yaml` 显式加载。
- `application-bootstrap.example.yaml`：单独的受控初始化配置，只使用 `ONTOLOGY_BOOTSTRAP_DB_USER` / `ONTOLOGY_BOOTSTRAP_DB_PASSWORD`；该凭据对应 `ontology_bootstrap_maintenance` 的窄权限，不得复用 Java 运行账号或 Flyway 迁移账号。
- `nginx.example.conf`：与实际本机验收相同的路由结构。先替换绝对路径和本机 `mime.types` 位置，配置、PID 与日志放在仓库之外；只监听回环地址，不自动启动隧道。
- 本机 HTTP 验收须在私有配置中覆盖 `cookie-secure: false`、`cookie-same-site: Lax`，并设置确切的 `trusted-origins: http://127.0.0.1:18083`。HTTPS 嵌入配置使用模板中的 Secure + SameSite=None，实际平台 Cookie 兼容仍须验收。
- 日常 Java 只使用 `ONTOLOGY_DB_USER` 对应的运行登录账号；账号设为 `NOINHERIT`，在该数据库新建连接时自动切换到 NOLOGIN 权限组 `ontology_app_runtime`。登录名只保留 CONNECT 和受限角色切换能力，不直接拥有表、序列或图 schema；漏配角色切换时业务权限默认拒绝。有效运行角色没有数据库级 CREATE、建角色、TEMP 或 `ontology` schema CREATE；AGE 动态标签写入所需的 CREATE 仅限具体图 schema，不能跨图建删。
- Flyway 通过 `ontology_migrator` 独立登录账号和 Maven Flyway 插件运行；它拥有 `ontology` 应用 Schema，但不是超级用户，不能创建数据库或角色。迁移凭据只注入维护命令，不加载进 Java 进程。迁移窗口由 DBA 临时授予对象 owner 成员资格，完成后 `finalize-db-permissions.sql` 撤销，迁移账号不会长期持有 AGE 数据库 CREATE 权限。
- `ontology_age_graph_maintainer`、`ontology_audit_maintenance` 与 `ontology_bootstrap_maintenance` 是 NOLOGIN 维护角色。前者独占数据库级 CREATE，并通过带图名校验的 `ontology.create_age_graph` / `ontology.drop_age_graph` 执行 AGE 图建删；AGE 图 schema 由维护角色持有，图目录表和动态标签对象由运行角色组持有，以满足 AGE 的 owner 检查并避免登录名直接拥有图对象。运行账号不能直接调用 AGE 的任意图库建删函数。审计维护组只用于受控保留操作；bootstrap 组只对 ledger、身份、成员、项目和审计追加所需列拥有 SELECT/INSERT，不可更新/删除，也不对未来对象设置默认权限。
- 新业务表和序列由迁移账号的默认 ACL 自动授予运行角色组最小 CRUD 与 `USAGE/SELECT`；AGE 写入会话以运行角色创建新标签对象。审计表保持只读加追加，bootstrap ledger、Flyway 历史和审计保留策略表不授予运行账号。`DatabasePrivilegesIntegrationTest` 检查登录名与有效角色分离、现有对象 ACL、迁移账号创建未来表/序列/函数时的默认 ACL，以及 AGE 未来标签 owner 和受限图建删；OAuth 集成测试覆盖客户端创建、列表、禁用及禁用后的 Token 撤销。新增管理/私有对象须显式撤权并更新权限 allowlist 测试。
- 新增对象前先备份隔离库。由数据库管理员运行 `prepare-db-roles.sql` 后，通过独立环境变量运行 `mvn -f apps/server/ontology-core/pom.xml flyway:migrate`（迁移会先校验）；成功后可再运行 `flyway:validate`，最后运行 `finalize-db-permissions.sql`。prepare 只给迁移窗口临时成员资格；不要把 Secret 放进 `-D` 参数、Java 服务配置或日志；模板默认关闭应用启动时自动迁移。Bootstrap 必须显式启用 `bootstrap` profile 与命令开关，并加载 `application-bootstrap.example.yaml` 的私有副本；其身份独立于运行与迁移账号，`og_bootstrap_run` 不向运行角色开放。
- Workbench 默认关闭。真实 clientSecret 只能放入私有后端配置，不能写入前端构建产物。

原项目 Docker 文件属于参考遗留资料，不是新的多用户启动入口。此处不自动升级原库、不覆盖旧公网 Nginx，也不发布正式 App 包。
