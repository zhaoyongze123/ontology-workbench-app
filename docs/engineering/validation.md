# 验证方式

本文保留基础版本的命令与历史验证记录。当前候选版的一次性数据库测试、浏览器契约检查和人工验收边界见 [0.2.0-rc.1 交付指南](workbench-app-0.2.0-rc.1-release-and-manual-validation.md)，最新执行结果以 GitHub Actions 为准。

## 可复现的仓库检查

```sh
python3 -m pip install -r requirements-dev.txt
cd apps/web && npm ci && cd ../..
make verify
```

Java 测试中需要实际数据库的用例以环境条件隔离，默认不会连接开发者原库。App 测试针对配置模板和打包契约，不能代替 Workbench 真实上传测试。

## PostgreSQL / AGE 集成验证

`apps/server/ontology-it/run.sh` 每次启动一次性 PostgreSQL/AGE 库，先用临时 DBA 账号准备角色，再通过 Maven Flyway 插件使用 `ontology_migrator` 执行版本迁移，最后收紧 ACL 并以 `ontology_it_runtime` 运行集成测试。脚本退出时销毁数据库临时盘。它不复用本机业务副本凭据。

`DatabasePrivilegesIntegrationTest` 在该隔离库验证：认证登录角色是 `NOINHERIT`，连接后有效角色为 `ontology_app_runtime`；运行角色无数据库级 CREATE、Ontology schema CREATE、TEMP、超级用户、建库、建角色或 BYPASSRLS 权限。AGE 动态图权限只作用于单个图 schema，已有及未来标签对象不由登录角色直接拥有，且直接 ACL 不授予登录角色；业务表、审计表、bootstrap ledger、Flyway 历史、序列和函数权限与 allowlist 一致。迁移窗口结束后迁移账号不再能切换到维护角色，仍可在自己拥有的 `ontology` schema 中创建未来表/序列，而新函数不会默认对 PUBLIC 开放；AGE 任意建删函数对运行账号不可执行，Ontology 图建删仍可通过受限包装层工作。OAuth 集成测试还验证客户端列表可读取且返回不含 `clientSecret`。`BootstrapDatabaseIntegrationTest` 使用独立 bootstrap 登录身份实际执行并重复核验初始化，再回滚种子数据；同时验证该角色没有数据库或 schema 创建权，只能读取冲突检测需要的列、插入必要身份/项目/成员/审计及 `og_bootstrap_run`，运行身份仍无法访问 ledger。

单独运行数据库测试：

- 使用仓库中的隔离库脚本：

```sh
bash apps/server/ontology-it/run.sh
```

定位权限测试时可设置 `ONTOLOGY_IT_TESTS=DatabasePrivilegesIntegrationTest,BootstrapDatabaseIntegrationTest` 只跑数据库和 bootstrap 权限边界用例；默认仍运行全部 PostgreSQL/AGE 集成用例。

用例也覆盖 OAuth 客户端管理列表、创建、禁用及禁用后的 Token 撤销，并覆盖授权、刷新和撤销；此外验证 MyBatis 项目记录与 AGE 图写入共同回滚。图谱回滚用例使用新的随机 ID，不删除原有图谱。

本机现有隔离副本升级前先做 PostgreSQL/AGE 完整备份；数据库管理员运行 `deploy/local/prepare-db-roles.sql`，然后仅在维护进程环境中设置 `ONTOLOGY_MIGRATOR_DB_URL`、`ONTOLOGY_MIGRATOR_USER=ontology_migrator`、`ONTOLOGY_MIGRATOR_PASSWORD` 并执行 `mvn -f apps/server/ontology-core/pom.xml flyway:migrate`（它会先校验迁移）。成功后可单独运行 `flyway:validate` 确认已同步。迁移完成后由管理员运行 `deploy/local/finalize-db-permissions.sql`，撤销迁移账号的临时 owner 成员资格，再以普通运行账号启动 Java 并执行接口验证。迁移 Secret 不通过 `-D` 命令行参数传入，也不进入 Java 运行配置。

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

## OIDC 启用与公网预检（2026-10-20）

修复 OIDC 过滤器创建时机：通过对象后处理器设置 OIDC 过滤器专属的空 SecurityContextRepository，避免构建配置时访问尚未创建的过滤器。新增真实构建 SecurityFilterChain 的回归用例，同时验证密码登录仍持久化内部 principal，不保存密码凭据。

相关 9 项测试通过；本次 Java package 共 72 项，67 项通过、5 项按条件跳过。数据库集成用例本次未重跑，历史隔离库验收结果见上文。

独立 Cloudflare 临时隧道经 Nginx 18083 访问 Java 13002，启用 Workbench OIDC 后完成 16 项公网预检：页面与双登录入口、密码登录及项目列表、Secure/HttpOnly/SameSite=None 的 host-only 会话、CSRF 退出、授权入口 302、既有客户端与精确回调、PKCE S256、state/nonce，以及非法回调被拒绝。上游 JWKS 接口可访问。

临时域名和真实配置留在仓库外的受保护运行记录中，不写入版本化模板。Workbench 注册中新增回调地址及真实账号授权回跳仍待用户配置后联调；这些预检不代表真实 OIDC 或 Agent OAuth2 已验收。
