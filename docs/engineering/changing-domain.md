# 临时公网域名更换清单

App 打包时只维护一个服务域名，不代表 Workbench 后台和运行中的 Java 服务能自动知道隧道换址。

| 位置 | 修改内容 | 生效方式 |
| --- | --- | --- |
| 运行目录 `private/app-package.local.json` | `serviceOrigin` | 使用本工程打包工具重新生成 ZIP；页面、嵌入、业务 API 与 App OAuth broker 地址统一派生 |
| 运行目录 `private/application-multiuser-runtime.yaml` | `ontology.security.trusted-origins` = 新 HTTPS 域名；`ontology.workbench.redirect-uri` = 新域名 + `/oidc/callback` | 重启独立 Java 副本 |
| Workbench 后台 OIDC 应用 | 新增新域名 + `/oidc/callback`，可保留旧回调 | 后台保存，真实登录回跳再验证 |

运行目录和真实凭据都在仓库之外；私有文件不提交 Git。`private/app-package.local.json` 已由既有 App 配置精简生成，凭据、固定回调和原域名保留；旧 `oidc-registration.json` 是历史配置，不再作为新的单域名维护入口。

App 打包配置里的 `redirectUri` 是固定 Workbench App OAuth callback，不随 Ontology 域名变化，也不是网页登录使用的 `/oidc/callback`。仅换域名时不更换既有客户端凭据。

`source/manifest.yaml` 保持占位模板；`dist` 内 Manifest、成员 `service.json` 与 ZIP 是生成产物。旧解压目录不会因生成新 ZIP 自动更新，上传时使用重新生成的 ZIP，不能手改旧解压 Manifest 后继续上传旧 ZIP。

前端使用同源相对 API 和固定部署前缀；Nginx / Cloudflared 仍转发相同的本机端口，通常不需因公网域名变化修改源码、重新构建前端或更改 Nginx。

注意：当前单域名工具仍基于历史 0.1.1 Python broker 契约。新 Java 多用户应用的下游 OAuth2 尚未完成；仅更换此配置的域名，不能把旧包转换成已适配新 Java 的上传包。域名管理能力与新版 App 的协议验收分别管理。
