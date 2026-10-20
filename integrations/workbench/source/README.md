# Ontology App 成员资源

此目录是供 Workbench App 上传联调的成员资源，不包含 broker 服务端的上游 OIDC 凭证。Manifest 使用独立 App OAuth broker 和 Workbench 原生 App OAuth 授权；成员只接收 Runtime 注入的 opaque Bearer。

`ontology-operations/scripts/runtime.py` 负责地址、Bearer 和 HTTP 边界；`scripts/ontology.py` 负责固定操作和参数验证；`references/operations.json` 来自本地原始 Ontology OpenAPI。业务请求统一使用 `service.json` 的 `/api/v1` 前缀；身份请求走 `/oidc/app/userinfo`，只输出身份字段。

Manifest 用字符串传递单个业务 ID 和 ID 数组成员，尤其保留 19 位十进制 ID 精度。分页参数仍为整数。业务参数通过 stdin JSON 传递，Token 不放进参数、文件或日志。

浏览器登录已由用户确认成功；上传后仍需验证 Workbench App OAuth 客户端连接、凭证注入、`getCurrentUser`、broker 外部 HTTPS iframe URL 和相对 `app.entryPath`。`status.verified=false` 只反映成员脚本不会伪称远端身份验证成功；使用 `identity` 才向 broker 查询当前用户。

当前多个用户共享业务数据库，尚未实施用户级项目 / 图谱授权隔离。App OAuth 身份认证不能代替业务级授权。
