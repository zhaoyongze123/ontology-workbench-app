# Docker 单机部署

本指南面向全新服务器部署。宿主机只需 Docker Engine、Docker Compose v2 和 POSIX shell；应用、迁移与 App 打包工具均在镜像中运行。部署 bundle 只包含 Compose、运维脚本和无密钥配置模板，私有凭据由服务器本地 `init-config` 生成。部署入口是 `deploy/compose.yaml` 和 `deploy/ontology.sh`。请使用同一组 `IMAGE_TAG` 固定 web、api、db、migrate、package 镜像版本；候选镜像存放于私有 GHCR 仓库。数据库基于固定摘要的 PostgreSQL 16.15、Apache AGE `PG16/v1.6.0-rc0` 和 pgvector `v0.8.1` 组合；不要单独升级数据库主版本或插件。

## 首次安装

1. 从经审阅的版本获取仓库中的 `deploy/` 文件，创建仅管理员可读写的部署目录。复制 `deploy/.env.example` 为 `deploy/.env`，设置 `IMAGE_PREFIX=ghcr.io/<owner>/<repo>`、发布的固定 `IMAGE_TAG`、外部访问地址、HTTP 监听端口及实际反向代理 CIDR。使用具备该私有包读取权限的账户登录 GHCR（不要把访问令牌写入命令参数或部署文件）。真实配置和密钥不要提交 Git。
2. 执行 `deploy/ontology.sh init-config` 生成 `deploy/private/` 私有运行配置和数据库凭据；目录权限为 `0700`，生成文件为私有权限。保管好该目录及管理员初始化 JSON，丢失凭据前需先安排凭据轮换。
3. 在安装 Docker Compose 的主机拉取固定标签镜像并执行首次数据库迁移：

   ```sh
   deploy/ontology.sh pull
   deploy/ontology.sh migrate
   ```

   `migrate` 先启动数据库并完成一次性迁移。迁移失败时停止后续初始化并先检查日志。

4. 创建 `deploy/private/bootstrap-input/bootstrap-admin.json` 首个系统管理员输入文件，限制父目录权限为 `0700`，文件权限为 `0600`（Docker secret 只读挂载也可用 `0400`）。JSON 格式如下，密码须为 12–1024 个字符且不得与用户名相同：

   ```json
   {"schemaVersion":1,"username":"owner","displayName":"System Owner","password":"请替换为高强度随机密码"}
   ```

   执行 `deploy/ontology.sh init-admin` 运行受控 bootstrap 工具。生产初始化仅在用户和组织均为空、且没有本地种子记录时创建一个启用的系统管理员；不创建测试用户或项目，也不认领旧项目。相同输入重跑只核验管理员仍存在，不改密码；输入变化或检测到已有身份/项目时明确失败，需先人工核对数据库状态。随后运行 `deploy/ontology.sh up` 启动 web、api、db 三个常驻服务。

5. `deploy/ontology.sh status` 查看服务状态，`deploy/ontology.sh logs` 查看服务日志。用浏览器访问部署域名，验证登录以及项目和图谱操作。

## 反向代理、HTTPS、Cookie 与 OIDC

Compose 默认仅将 web 绑定到本机回环地址。由外部 Nginx、Caddy 或企业网关终止 TLS，并将请求转发到该回环地址；反代必须保留 Host，并正确设置 `X-Forwarded-For`。`EDGE_TRUSTED_PROXY_CIDRS` 只列外部 HTTPS 网关连接到 web 时可见的来源网段；`API_TRUSTED_PROXY_REGEX` 只信任内部 web 容器地址（模板默认 `172.30.0.10`）。不能信任任意客户端传入的转发头。外部访问地址必须使用最终 HTTPS Origin。

生产 Cookie 使用 Secure，并为跨站 Workbench iframe 场景配置 `SameSite=None`；`init-config` 不会生成 Cookie 覆盖文件。仅本机 HTTP smoke 可在私有 `api-config` 中额外创建 `ontology.security.cookie-secure=false` 和 `ontology.security.cookie-same-site=Lax`，生产 HTTPS 环境不得创建这两个文件。浏览器对第三方 Cookie 的限制仍需在目标 Workbench 环境实测。

Workbench OIDC 配置需要一组由 Workbench 管理员签发的 Client ID 和 Client Secret；服务端凭据放在 `deploy/private/api-config/`，回调 URI 必须与平台登记值逐字一致。Ontology App OAuth 另有一组 Java Admin 签发的 App client 凭据：App `clientSecret` 必须位于管理员上传 ZIP 根级 `oauth-clients.yaml`，按受限管理员资料管理；上游 Workbench OIDC secret 绝不能进入 App ZIP。两组 secret 均不得进入前端变量、Docker 镜像、deployment bundle 或日志。部署域名只在 `.env` 设置 `PUBLIC_ORIGIN`，`package-app` 会将该值传给 package 镜像并覆盖私有 JSON 中的 service origin。完成配置后生成并上传 App 包，并在真实 Workbench 中验证登录、嵌入 Cookie 和 Agent 授权；本地部署不代表平台验收完成。

## 升级、备份与恢复

升级前先安排维护窗口，备份数据库及私有配置，然后停止应用以留出迁移窗口。将 `IMAGE_TAG` 改为已审阅版本并拉取镜像，完成迁移后再启动应用，避免新版本应用在迁移前访问旧 schema。

```sh
deploy/ontology.sh backup
deploy/ontology.sh down
deploy/ontology.sh pull
deploy/ontology.sh migrate
deploy/ontology.sh up
deploy/ontology.sh status
```

备份命令生成的归档含业务数据，应移出服务器或存入受控备份存储。恢复前先停写并保留当前数据副本；恢复到一个从未存在过的隔离数据库：

```sh
deploy/ontology.sh restore /受保护路径/ontology.dump ontology_restore_check
```

该操作只创建独立目标库，不覆盖或切换当前应用数据库；恢复时会在新库重建迁移和运行角色权限。验证备份无误后，可停应用并将 `.env` 的 `DATABASE_NAME` 改为目标名，再执行 `init-config` 同步 JDBC 配置和 `up` 重建 API/web；确认登录、项目/图谱读取及 AGE 查询正常后完成切换。运行账号连接新库时必须切换到 `ontology_app_runtime`。禁止对未备份的原数据库直接做试恢复。

App 包使用 `deploy/ontology.sh package-app`，读取 `deploy/private/package.json` 私有配置并在 `deploy/private/package-output/` 生成管理员上传 ZIP。ZIP 根级 `oauth-clients.yaml` 必须含 Java Admin 签发的 Ontology App `clientId` 和 `clientSecret`，该 ZIP 是受保护的管理员凭据材料，只能通过受限管理员通道暂存和上传。服务端上游 Workbench OIDC `client-id/client-secret` 只保存在 `deploy/private/api-config/`，绝不能进入 App ZIP。该 ZIP 是 Workbench App 源包，不含 web 镜像中的前端构建产物；前端由 web 镜像提供。上传后在 Workbench 检查 App 可见性、页面授权、当前用户身份、Action 和模型调用。Docker 镜像与 deployment bundle 不含任何真实凭据。

手动运行仓库的“Docker images”工作流后，可下载 `ontology-deployment-bundle-*` 工件。该压缩包只含 Compose、部署 CLI、本地权限 SQL/模板和本指南；不含源码构建依赖、`.env`、`private/` 或真实凭据。目标主机需安装 Docker Engine 与 Compose 插件，解压后按上文创建私有配置，再拉取与之匹配的固定镜像标签。

## 健康检查与故障排查

运行 `deploy/ontology.sh status` 和 `deploy/ontology.sh logs`。检查顺序为：db 是否健康且持久卷已挂载；api 是否能以运行账号连接数据库；迁移是否完成；web 到 api 的内部路由是否可达；反代是否传递正确 Host 与 HTTPS 协议。修改配置后按 CLI 提示重启受影响服务。数据库容器或应用容器反复重启时，先保存日志和备份，再处理迁移或凭据问题；不要通过清空卷恢复服务。

镜像标签由手动 GitHub Actions `Docker images` 工作流产生。工作流默认只构建验证；只有显式选择推送时才发布到私有 GHCR，不创建 GitHub Release。部署时固定版本标签，不使用浮动 `latest` 标签。
