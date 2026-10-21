# Workbench Ontology App 0.2.0-rc.1 发布与验收

本版本是私有开发验收候选包。源码契约和离线打包通过，不代表真实 Workbench 已上传、App OAuth 已完成、Secret 隔离已实测或数据库切换已批准。管理员上传 ZIP 含 Workbench App client secret，只能在批准的管理通道使用；普通用户发布包、源码仓库、issue、日志和共享附件不得出现该 secret。Java 上游 Workbench OIDC client id/secret 只存在服务端私密配置，与 App client 凭据完全分开。

## Java 服务和包配置对齐

Java `ontology.security.public-origin` 是唯一公开 Origin，必须和打包配置 `serviceOrigin` 完全相同；只填写 origin，不包含 `/api/v1`。它用于 App Manifest/API URL、可信浏览器 Origin 和 Workbench OIDC callback 前缀。OAuth issuer 则由该 Origin 加上 `server.servlet.context-path` 组成（当前为 `/api/v1`），SAS 的授权、Token、撤销和 introspection endpoint 再相对 issuer 生成。当前 discovery URI 按 RFC 8414 为 `https://<origin>/.well-known/oauth-authorization-server/api/v1`；[nginx.example.conf](../../deploy/local/nginx.example.conf) 展示了将其精确转发到 Java context 内 metadata 路径的规则。私有运行配置按 [application.example.yaml](../../deploy/local/application.example.yaml) 准备，并仅从仓库外权限受限目录加载。服务端至少需要 `ONTOLOGY_PUBLIC_ORIGIN`、数据库连接变量及上游 Workbench OIDC client credentials；上游凭据不要复制到 App 配置或包内。

在隔离数据库迁移和初始化完成后，从仓库根目录启动已构建的 Java 服务，必须显式启用 `multiuser`，不能用默认配置替代多用户验收：

```sh
java -jar apps/server/ontology-core/target/ontology-core-0.0.1.jar \
  --spring.profiles.active=multiuser \
  --spring.config.additional-location=file:/绝对路径/private/application.local.yaml
```

私有配置中的数据库应使用隔离副本的非超级用户账号；上游凭据核对完成后，将 `ontology.workbench.enabled` 设为 `true`。前端使用在 `apps/web` 执行 `MULTIUSER_BUILD=1 npm run build` 生成的 `dist-multiuser`，并将私有 Nginx 配置的静态文件目录指向该目录。Nginx 18083、Java 13002 是独立测试入口，不覆盖旧 18081/13001 服务。

Java 管理员先创建 App OAuth client：display name `Ontology`、Workbench 固定 callback `https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback`、认证方式 body/`client_secret_post`、PKCE 开启，并包含 `profile`、`ontology.read`、`ontology.write`、`ontology.manage`。把新建结果仅存入本机私密 App 打包配置；旧 client 若 redirect 或 scopes 不符合，须登记新 client。WorkBench 固定 App callback 通常不随 Ontology 域名改变；Java 上游登录 OIDC callback 是 `${ONTOLOGY_PUBLIC_ORIGIN}/oidc/callback`，改域名时须同步 Workbench 登录 OIDC 配置并重新做真实登录回调验证。

`serviceOrigin` 只能是纯 HTTPS origin。一次生成会产出版本、SHA-256、脱敏校验报告和管理员 ZIP：

```sh
python3 integrations/workbench/tools/finalize.py \
  --config /绝对路径/private/app-package.local.json \
  --output /绝对路径/private/ontology-app-release
```

`integrations/workbench/config.example.json` 仅是无凭据格式示例，不能用于生成可上传 ZIP。必须由管理员先在 Java Admin 为正式环境创建 `Ontology` App client，再把真实 id/secret 写入仓库外的私有副本。当前未提供真实 App client 凭据，因此没有生成或声称存在可用管理员上传包。

输入缺少 client id/secret、仍含占位符、callback 或 scope 不匹配时，命令须失败且不创建 ZIP。不要将凭据放在 shell 参数、环境 dump、终端录屏或仓库文件里。生成物设置为当前用户可读；上传后删除临时副本并保留受限管理员审计记录。`upload-validation.json` 是离线 schema/秘密边界报告，不能当作 Workbench 验收报告。

## 隔离数据、备份与恢复边界

所有迁移、权限、备份恢复、用户/项目归属核对和回滚均单独归 #17。本交付没有操作原运行库、没有切换运行库。临时集成测试库的迁移和数据库测试证据以运行报告为准，不能代替对原库备份恢复、存量归属与回滚的验收。最终验收只能在原库的备份恢复副本或一次性隔离库上做，保留原库不变：

1. 维护人员先记录原库标识、Flyway 历史、拥有者/项目归属摘要和备份校验值；暂停写入后做一份新备份。凭据与业务正文不放在 issue 或普通日志。
2. 把备份恢复到名称明确、网络仅本机可达的隔离数据库；先核对 schema、项目 OWNER、节点/关系总量及业务 ID 样本，再按受控迁移账号执行未应用的增量脚本。不得改写已执行 migration。
3. 只把非超级用户、无 `BYPASSRLS` 的运行账号交给 Java；对实际对象权限、AGE 建图/查询能力和跨项目拒绝做证据核验。完成停机备份/恢复演练后才允许另行审议本机切换；本候选包不会切换原库。
4. 失败时停止写流量，在隔离库保留错误证据并恢复该隔离副本；原库继续保持原状态。任何未来本机切换要先有切换前备份及已验证恢复路径，并按 #17 的安全条件回滚，不得回到公开无权限入口。

`apps/server/ontology-it/init/01-bootstrap.sh` 仅为临时测试容器创建 `ontology` schema 和低权限运行角色。业务初始化使用 Java CLI：先在已完成备份恢复和存量归属核对的隔离数据库准备仓库外 JSON（严格符合 `BootstrapService.BootstrapInput`，含 7 用户、2 测试项目；目录 `0700`、文件 `0600`），然后从仓库根目录通过已编译工件并明确加载隔离库配置执行：

```sh
java -jar apps/server/ontology-core/target/ontology-core-0.0.1.jar \
  --spring.config.additional-location=file:/绝对路径/private/application.local.yaml \
  --spring.profiles.active=bootstrap \
  --ontology.bootstrap.apply=true \
  --spring.main.web-application-type=none \
  --ontology.bootstrap.file=/绝对路径/private/multiuser-bootstrap.local.json
```

`application.local.yaml` 必须只含隔离库连接及所需私密配置；启动前逐项确认 JDBC host/database，禁止省略该参数让 Spring 使用默认库。密码与用户名相同是源码限定的本地测试策略，不用于正式用户。命令要求唯一管理员 `admin`，在无 OWNER 的存量项目上为其建立 OWNER 关系；遇到已有身份或 OWNER 冲突会停止，不自动覆盖。重复执行仅核验 bootstrap ledger、用户和项目，不重置密码。该命令尚未用于原运行库；不要把开发密码输入正式/原有数据库。HTTP ACL smoke 也会创建项目/图谱记录，只能指向隔离副本。

## 自动化证据

下列命令只证明它们所覆盖的本机/离线合同，不代替平台最终验收：

```sh
python3 -m unittest discover -s integrations/workbench/tests -v

# 一次性 Docker/PostgreSQL/AGE IT；容器数据在脚本退出时销毁。
bash apps/server/ontology-it/run.sh

# 静态前端契约 smoke；需先在 apps/web 执行 MULTIUSER_BUILD=1 npm run build，并在 Node 环境提供 Playwright。
node scripts/frontend_contract_smoke.cjs

# 已初始化的隔离数据库副本才允许运行，会创建临时项目/图谱记录。
python3 scripts/http_acl_smoke.py
```

本地隔离数据库必须使用 `ontology_multiuser_dev_20261020` 测试库名与非超级用户凭据；集成测试读取 `ONTOLOGY_IT_JDBC_URL`、`JDBC_USER`、`JDBC_PASSWORD`。不要把这些环境变量写入文档、CI 日志或提交文件。CI 的命令以 workflow 实际矩阵为准。`#17` 的备份/恢复、历史数据归属、实际最小权限和回滚仍须真实证据，不能由一次性空库 IT 代替。

## Issue 验收矩阵

自动命令只记录本地可重复证据。最后一列列出仍依赖管理员/测试用户在真实 Workbench、隔离数据或部署环境完成的验收；没有真实结果时 Issue 保持未完成。

| Issue | 代码/配置入口 | 自动证据命令 | 仅用户/运维可完成的最终验收 |
| --- | --- | --- | --- |
| #2 | `identity/`、账号 lifecycle Controller/Service、`AccountLifecycleDatabaseIntegrationTest` | `bash apps/server/ontology-it/run.sh` | 首个管理员/账号启停、密码重置/激活和现有身份绑定按真实账号完成；核实不覆盖既有密码或项目 OWNER。 |
| #3 | `oidc/`、`WorkbenchLoginController`、`apps/web/src/pages/Login/` | Java OIDC filter chain 与 identity unit/HTTP IT；执行 workflow 报告 | 在 Workbench 实际登录、回调、显式绑定、冲突/停用拒绝、iframe Cookie 及新窗口登录；保存脱敏回调/会话证据。 |
| #4 | `oauth/AgentOAuthServerConfiguration.java`、`AgentClientRepository.java`、`DigestOAuth2AuthorizationService.java` | `AgentOAuthAuthorizationIntegrationTest`（一次性 PostgreSQL/AGE IT） | Java 后端实际登记 App client 与固定 redirect、body auth、PKCE，Workbench consent 与 callback 成功。 |
| #5 | `integrations/workbench/source/`、`tools/finalize.py` | `python3 -m unittest discover -s integrations/workbench/tests -v` | 管理员上传准确 ZIP/哈希，用户看见 App 并完成页面授权、当前身份、Action 与模型调用；不得用本地 ZIP 验证替代。 |
| #6 | OAuth token generation / `DigestOAuth2AuthorizationService` / token IT | `AgentOAuthAuthorizationIntegrationTest` 覆盖 rotate、replay、期限、并发 | Workbench 客户端真实自动刷新、丢失响应重试策略和刷新后的成员请求成功；8 小时绝对期限不能被刷新延长。 |
| #7 | `AgentBearerAuthenticationFilter`、`AgentScopePolicy`、project membership Service | OAuth/database IT 覆盖用户、scope、伪造身份头和成员关系 | 用至少两个真实 Workbench 用户检查跨项目读写拒绝及只读/写入范围；不得以管理员账号单例通过推断隔离成立。 |
| #8 | `OAuthTokenTransactionFilter`、refresh/revoke transaction、`DigestOAuth2AuthorizationService` | OAuth HTTP IT 的 refresh/revoke 并发与 token 历史断言 | 在 Workbench 重试/断开流程中验证客户端实际调用顺序、最终 access 与 refresh 都失效；仅 HTTP 200 不足以证明状态撤销。 |
| #13 | `SecurityAuditRecorder`、request correlation、代理 Resolver、日志配置 | 日志/安全/代理单测；`bash apps/server/ontology-it/run.sh` | 运维复核真实 Nginx trusted proxy CIDR、谁可读取审计日志、保留/清理策略和线上异常日志；不查看或复制凭据。 |
| #15 | `identity/AccountLifecycleController.java`、`AccountLifecycleService.java`、`apps/web/src/pages/AdminUsers/`、`AdminOAuthClients/`、`AdminAuditEvents/` | `bash apps/server/ontology-it/run.sh`；前端执行 `MULTIUSER_BUILD=1 npm run build` 后运行 `node scripts/frontend_contract_smoke.cjs` | 管理员登录真实环境，验证用户创建/启停/激活/重置、App client 创建/禁用、审计查询和权限边界；以真实账号与审计记录验收。 |
| #17 | Flyway `V0.0.x`、`apps/server/ontology-it/`、数据库角色/AGE 配置 | `bash apps/server/ontology-it/run.sh` 只验证临时新库 | 对原库备份、隔离恢复、存量归属、最小权限、AGE 查询边界、切换前备份与恢复/回滚做逐项证据。本文未执行原库操作。 |
| #18 | Java token/revoke endpoint 与 Workbench App manifest refresh fields | Java token IT、`python3 -m unittest discover -s integrations/workbench/tests -v` | 在真实 Workbench 实测 refresh 请求、替换 token 注入及连接断开撤销；该项未由本地脚本验收。 |
| #19 | ZIP finalizer secret scan、Java 安全日志边界 | Python finalizer tests、OAuth secret-redaction tests | 上传后检查管理面、普通成员资源、iframe、Action/模型上下文、下载文件、浏览器存储及日志，确认凭据永不对普通成员可见。 |

Issue #18 的验收依赖真实 Workbench 授权和账号隔离，Issue #19 明确要求上传后以受控测试标记实查秘密可见性；本地测试和静态文件扫描都没有关闭这两项。

## 用户最终 Workbench 联调步骤

1. 运维确认服务 origin、HTTPS、Nginx 转发与 OIDC discovery/JWKS，再从受保护的 Java 运行配置启用上游 Workbench OIDC。实际更换域名时同步 `ONTOLOGY_PUBLIC_ORIGIN`、可信 Origin、`ontology.workbench.redirect-uri` 和 Workbench 登录 OIDC callback，重启 Java/Nginx 后以真实用户验证登录回跳。
2. 管理员登录 `/ontology-management/admin/oauth-clients` 页面新建 `Ontology` App client（后端接口为 `/api/v1/admin/oauth/clients`），登记正式固定 App callback、PKCE 和四个 scope。将响应里的 App client id/secret 放入仓库外的私有 package JSON；不要把 Java 上游 OIDC client secret 混进来。
3. 用工具生成 `0.2.0-rc.1` ZIP，记录 SHA-256。受限管理员检查 Manifest 与 hash，然后只通过 Workbench 管理入口上传；保存版本和 hash，不把 ZIP 分享给普通成员。
4. 普通测试用户打开 Workbench App，完成 OAuth consent。观察回调、`getCurrentUser` 的 `id/username/name` 映射和一个只读 Action；用有/无项目、只读与可写身份分别验证 scope×项目权限矩阵。
5. 等 access token 自然到期后触发一次请求，验证 Workbench 使用 refresh endpoint 轮换 refresh token、旧 token 不能复用。随后从 Workbench 断开连接，并分别重试旧 access token 和 refresh token，确认两者都失效；记录 HTTP 状态、脱敏请求路径和时间，不记录 Authorization header 或 token 内容。
6. 用第二个内部账号核验成员资源、iframe、Action、导出文件、模型上下文和错误日志里没有 App Secret、上游 OIDC Secret 或令牌原值。按 #19 保留脱敏结果和可见性结论。
7. 按 #17 另做存量备份恢复和迁移/回滚演练。平台 OAuth、#19 秘密可见性、#17 数据恢复任一项未验收，都不得把 RC 候选称为完整发布，也不能切换原数据库。
