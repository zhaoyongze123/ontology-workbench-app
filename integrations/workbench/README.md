# Workbench Ontology App 0.2.0-rc.1

`source/` 是 Workbench App 的版本化 schema 和 Agent 行为事实源。`tools/finalize.py` 从单一 `serviceOrigin` 生成入口、iframe、API 与 Java App OAuth broker URL，再按正式 App 导出结构校验并打包。先复制 [config.example.json](config.example.json) 到仓库外受限目录；示例 origin 只作占位。管理员须先在 Java Admin 创建正式 App client，再将返回的 `clientId`、`clientSecret` 写入私有配置。该示例故意留空凭据；未配置或仍为占位符时不会生成 ZIP，测试夹具凭据不能用于真实环境。

当前 Manifest 与 Java broker 的 OAuth 契约一致：固定 `/oidc/app/authorize`、`/oidc/app/token`、`/oidc/app/userinfo`，form token request、PKCE、body client authentication，以及 `profile`、`ontology.read`、`ontology.write`、`ontology.manage` scope。token endpoint 同时承担 refresh，声明 `refresh_token` 字段。Java Admin 必须将客户端登记为 `Ontology`，redirect URI 与 Workbench App callback 完全一致，并配置上述 scope。

凭据有两类，不能混用：管理员上传包根级 `oauth-clients.yaml` 中的 Workbench **App client** 凭据由 Java `/admin/oauth/clients` 登记结果产生；服务端 `ontology.workbench.client-id/client-secret` 是登录 Ontology 时使用的上游 **Workbench OIDC** 凭据，仅保存在后端私密配置。管理员 ZIP 含 App client secret，必须按受限管理员资料管理、传输、上传和清理；它不是公开发布包或成员资源。私有配置、ZIP、校验报告和哈希都不得提交 Git。自动化测试中的 fixture 凭据只是假值。

示例私有配置结构：

```json
{
  "serviceOrigin": "https://ontology.example.invalid",
  "redirectUri": "https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback",
  "appEntryPath": "/ontology",
  "clientId": "由 Java App Admin 登记取得",
  "clientSecret": "由 Java App Admin 登记取得",
  "clientAuthMethod": "body"
}
```

把实际配置放在仓库外的权限受限目录。确认 Java `ontology.security.public-origin` 与 `serviceOrigin` 相同；域名变动时更新唯一公共 origin，重启服务并重新生成管理员包。WorkBench 固定 App callback 通常不随 Ontology 域名变化。先在 Java 管理页按 scope 注册 App，再将返回的凭据写入本机私有 JSON，然后生成 `0.2.0-rc.1`。发布物只能被描述为“可进行 Workbench 手动验收”，直到用户完成正式宿主授权、refresh、token 撤销、身份、API 与成员隔离联调。

```sh
python3 integrations/workbench/tools/finalize.py \
  --config /absolute/private/app-package.local.json \
  --output /absolute/private/ontology-app-release
```

离线契约测试：

```sh
python3 -m unittest discover -s integrations/workbench/tests -v
```

Java OAuth 的安全集成测试另见 [Workbench App 发布和手动验收](../../docs/engineering/workbench-app-0.2.0-rc.1-release-and-manual-validation.md)。离线包检查不会验证 Workbench 是否接受 Manifest、真正保存 App client secret、向成员隔离凭据或按预期刷新/撤销。数据库迁移、备份恢复与本机切换按 #17 单独验收；不要从此打包操作推导其已通过。
