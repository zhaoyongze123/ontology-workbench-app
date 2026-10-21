# Ontology App 成员

scripts/runtime.py 管地址、App 凭证与 HTTP；scripts/ontology.py 管固定操作选择和参数验证；references/operations.json 来自本地真实启动服务的 OpenAPI。

根级 Manifest 与 oauth-clients.yaml 不属于成员运行依赖。成员只读取 `APP_OAUTH_ONTOLOGY_ACCESS_TOKEN` opaque Bearer，不读取 App broker Client Secret、上游 OIDC Secret 或原服务固定 Key。Manifest 用户信息 Action 调用 `/oidc/app/userinfo`；`identity` 操作走同一 broker 端点，业务 API 保持 `/api/v1`。

示例输入：`{"operation":"organizationPage","query":{"pageNum":1,"pageSize":20}}`。本地凭证状态：`{"operation":"status"}`；读取用户身份：`{"operation":"identity"}`。通过 stdin 输入，不把 Token 放进参数。`status.verified=false` 只表示脚本不自行声称远端身份验证成功；需要运行 `identity` 才会向 broker 请求当前用户。

Workbench 授权注入、自动刷新和撤销仍须在上传后联调。Bearer scope 会在 Java API 与当前项目成员权限交集校验；本地脚本测试不证明真实 Workbench 成员身份或跨项目隔离已通过。
