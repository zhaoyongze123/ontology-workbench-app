# Ontology App 接入说明

更新日期：2026-10-19。当前交付面向本机运行、临时公网地址和 Workbench 上传联调，不涉及服务器部署。原始前后端 268 个文件基线未改变。

## 业务与数据链路

Ontology 前端为 React / Umi，页面路径为 `/ontology-management/`；后端为 Java 17 / Spring Boot，接口前缀为 `/api/v1`。本体定义类型、属性和关系，实体关联本体并记录具体对象。

业务请求经过 Controller → GraphStrategy → 本体 Schema 校验 → PostgreSQL 业务表 / AGE 图存储。Agent 使用同一业务 API，不直接写数据库，也不绕过本体校验。当前使用专用数据库 `ontology_workbench_local`，没有改动其他项目数据库。

## 接入层职责

```text
浏览器 → 临时 HTTPS 域名 → Nginx → OIDC 网关验会话 → 原 Ontology 页面和 API
                              ↓
                   Workbench OIDC 提供方

Workbench App 授权 → 本地 App OAuth broker → 复用浏览器 OIDC 登录身份
                  → 授权码返回 Workbench → Workbench 换取短期 Bearer
                  → App 成员脚本 / Actions → Nginx 验 Bearer → 原业务 API
```

独立 Python 接入层与 Nginx 完成登录和访问门禁，保留原业务前后端。Workbench 的 OIDC 客户端注册页面本身不会自动生成上述代理逻辑。

## 两段授权分别持有状态

上游网页登录使用用户注册的 Workbench OIDC Client ID / Secret，回调为临时公网域名的 `/oidc/callback`。接入层校验 state、PKCE、nonce、ID Token 签名、issuer、audience 和有效期。用户已确认授权后返回项目管理页且显示项目数据；本地安全日志也记录到成功回调。

App OAuth broker 是独立 OAuth2 提供方，端点为 `/oidc/app/authorize`、`/oidc/app/token`、`/oidc/app/userinfo`。它使用本机生成的独立 App 客户端配置；成功登录后的真实用户身份绑定到短期授权码和 Bearer。App 不接收上游 OIDC Secret 或原业务 API Key。

App 授权码返回 Workbench 的 `/basilcraw/api/apps/oauth2/callback`，由宿主持有自己的 state 和 PKCE verifier。broker 只接受精确回调和 S256，不把浏览器回调冒充 App 回调。此设计不需要给现有上游 OIDC 注册增加 Workbench 回调。

授权码一次性使用，访问令牌不超过一小时且不能超过上游身份有效期；当前不提供刷新令牌。状态保存在内存中，网关重启后需要重新登录 / 授权。这适用于当前临时测试环境，不能直接视为生产认证服务。

## App 包与成员

App 声明身份、图标、外部登录入口、OAuth2 配置、用户身份查询及项目 / 图谱 / 节点 / 关系操作。成员脚本从宿主注入的 `APP_OAUTH_ONTOLOGY_ACCESS_TOKEN` 获取用户凭证，不保存 Token、客户端 Secret 或业务 Key。

ID 以十进制字符串传递，避免 19 位业务 ID 经过 JavaScript 数字丢失精度。成员写入操作需要明确用户目标和确认；预览执行固定查询，自由 Cypher 和文件导入保留在原页面。

## 权限与宿主验证范围

应用登录身份与业务数据权限分别管理。当前原后端数据模型没有接入用户 / 工作区隔离，项目 `organizationId` 不是租户身份；通过登录的测试用户访问同一套测试数据。传给后端的身份 Header 不能代替后端的数据授权规则。

Library / Base 提供 App 包结构、OAuth 控制面和成员运行时参考，但没有包含 Workbench 宿主源码。上传后仍需验证相对宿主 `app.entryPath=/ontology` 与 `plugin.iframeUrl` / `app.websiteUrl` 的消费方式、原生 App 授权卡片、回调处理、账号绑定和凭证注入。若宿主不支持外部入口，应改宿主入口配置或承载方式，不能凭空声明一个 Workbench 内部页面路由已存在。

## 配置与验收

服务端私有配置位于 `04-测试与交付/本地运行/private/`。根级 `oauth-clients.yaml` 仅随管理员上传包提供 App 客户端配置，不属于可下发的成员资源。

网关 / Nginx 的实际验收与进程管理见[本地运行说明](../04-测试与交付/本地运行/README.md)。包的结构、离线校验和上传步骤见[版本说明](../03-接入包/0.1.0/README.md)。Workbench 真实安装、App 授权及 Agent 调用以用户上传后的联调结果为准。

0.1.1 依据 Calendar 正式导出补齐外部 iframe 声明，并将 `clientAuthMethod` 对齐为 `body`。Base / Library 新旧非凭证文件一致。详情见[修正对比](../03-接入包/0.1.1/正式导出对比与上传说明.md)。
