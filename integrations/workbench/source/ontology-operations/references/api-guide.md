# API 合同与操作路由

本目录 operations.json 的来源是本机编译启动的原始 Ontology 后端 `/api/v1/v3/api-docs`，包含请求字段、必填、枚举和参数结构。分页响应保留 addition.total/pageNum/pageSize。

- 项目：organizationPage、saveOrganization、editOrganization、deleteOrganization。
- 图谱：pageGraph、saveGraph、editGraph、deleteGraph、getGraphInfo、exportGraph、getLinkOntologyNodeList。
- 对象：pageNode、saveNode、editNode、deleteNode、getNodeInfo、getNodeOptional。
- 关系：pageRelationship、saveRelationship、editRelationship、deleteRelationship、getRelationshipInfo、getRelationshipOptional、getLinkOntologyRelationshipList。
- 展示：open 返回原应用页面；previewGraph 执行固定的图节点预览。

POST 分页接口仍属于读取；其余变更接口要求 _confirm=true。原始自由 Cypher 和 multipart JSON 导入未向此成员开放，仍可在原应用页面使用。

Workbench App OAuth broker 与上游 OIDC 客户端由服务端分开持有。App 包通过固定 Workbench 回调完成 App 资源授权，opaque Bearer 由 Workbench 注入 `APP_OAUTH_ONTOLOGY_ACCESS_TOKEN`；成员 `identity` / `getCurrentUser` 通过 `/api/v1/oidc/app/userinfo` 获取 `id`、`username`、`sub`、`name`，业务请求仍使用 `/api/v1`。成员不保存客户端 Secret，也不直接拿上游 OIDC Token。

浏览器页面 `/ontology-management/login` 和 Workbench 的 App consent 是分开的步骤。匿名 GET authorize 会带原始授权请求跳到登录页；用户登录后回到固定同源 authorize 路径，再完成 consent。登录 Cookie 的有效期、scope 和当前项目 ACL 分别由 Java 会话及业务链验证。远端宿主是否注入 Bearer、自动刷新或调用 token revoke，仍须上传后观察并记录真实证据。
