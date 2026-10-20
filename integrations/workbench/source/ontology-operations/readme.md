# Ontology App 成员

scripts/runtime.py 管地址、App 凭证与 HTTP；scripts/ontology.py 管固定操作选择和参数验证；references/operations.json 来自本地真实启动服务的 OpenAPI。

根级 Manifest 与 oauth-clients.yaml 不属于成员运行依赖。成员只读取 `APP_OAUTH_ONTOLOGY_ACCESS_TOKEN` opaque Bearer，不读取 App broker Client Secret、上游 OIDC Secret 或原服务固定 Key。Manifest 用户信息 Action 调用 `/oidc/app/userinfo`；`identity` 操作走同一 broker 端点，业务 API 保持 `/api/v1`。

示例输入：`{"operation":"organizationPage","query":{"pageNum":1,"pageSize":20}}`。本地凭证状态：`{"operation":"status"}`；读取用户身份：`{"operation":"identity"}`。通过 stdin 输入，不把 Token 放进参数。`status.verified=false` 只表示脚本不自行声称远端身份验证成功；需要运行 `identity` 才会向 broker 请求当前用户。

当前 Workbench 授权注入合同需要在上传后联调。共享业务数据库暂不提供用户级隔离；身份门禁不能替代项目 / 图谱级授权设计。
