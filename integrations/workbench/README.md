# Workbench App 接入

此目录保存既有 `0.1.1` App 的去凭据模板、工具脚本和打包契约测试，便于继续开发和比对正式导出格式。

## 单域名打包配置

打包配置只需维护一个应用域名 `serviceOrigin`。`pageUrl`、`embedUrl`、`authorizationEndpoint`、`tokenEndpoint` 和 `userInfoEndpoint` 可省略，工具会按固定路由从该域名派生。旧配置仍可使用这些字段，但每个显式值必须与 `serviceOrigin` 和固定路由完全一致；上游身份提供方 URL 或不一致的覆盖值会被拒绝。

仍需显式提供 `redirectUri`、`appEntryPath`、`clientId`、`clientSecret` 和 `clientAuthMethod`。`redirectUri` 必须是 Workbench 固定的 App OAuth callback；它不是网页登录回调。`appEntryPath` 固定为 `/ontology`，客户端认证方式固定为 `body`。不要把任何真实凭据写入模板或示例文件。

此 `0.1.1` 模板对应历史 Python broker 链路。当前新 Java 多用户版本的下游 OAuth2 授权、scope 和刷新轮换尚未实现；因此用本模板成功生成的包不能据此宣布新 Java App 包可上传或已适配。新版本仍须等 Java 授权契约完成并通过 Workbench 实际联调。

`source/oauth-clients.yaml` 中的 Secret 必须保持为空或占位符，发布时只从服务端受保护配置填入管理员私密部分；生成的 ZIP 不进入 Git。

更换域名时涉及的私有配置、Java 重启及 Workbench 后台步骤见[域名更换清单](../../docs/engineering/changing-domain.md)。
