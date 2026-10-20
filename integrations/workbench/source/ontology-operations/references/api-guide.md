# API 合同与操作路由

本目录 operations.json 的来源是本机编译启动的原始 Ontology 后端 `/api/v1/v3/api-docs`，包含请求字段、必填、枚举和参数结构。分页响应保留 addition.total/pageNum/pageSize。

- 项目：organizationPage、saveOrganization、editOrganization、deleteOrganization。
- 图谱：pageGraph、saveGraph、editGraph、deleteGraph、getGraphInfo、exportGraph、getLinkOntologyNodeList。
- 对象：pageNode、saveNode、editNode、deleteNode、getNodeInfo、getNodeOptional。
- 关系：pageRelationship、saveRelationship、editRelationship、deleteRelationship、getRelationshipInfo、getRelationshipOptional、getLinkOntologyRelationshipList。
- 展示：open 返回原应用页面；previewGraph 执行固定的图节点预览。

POST 分页接口仍属于读取；其余变更接口要求 _confirm=true。原始自由 Cypher 和 multipart JSON 导入未向此成员开放，仍可在原应用页面使用。

Workbench App OAuth broker 与上游 OIDC 客户端由服务端分开持有。App 包通过固定 Workbench 回调完成 App 资源授权，opaque Bearer 由 Workbench 注入 `APP_OAUTH_ONTOLOGY_ACCESS_TOKEN`；成员 `identity` / `getCurrentUser` 通过 `/oidc/app/userinfo` 获取必需的 `id`、`username`、`sub`，并在上游提供时保留 `name`，业务请求仍使用 `/api/v1`。成员不保存客户端 Secret，也不直接拿上游 OIDC Token。

浏览器入口 `/oidc/login` 的登录完成不等于 App 资源授权已经注入；需要通过 Workbench 原生 `request_user_input` 授权合同后再验证 `identity`。本合同与真实 Token 注入仍需在宿主上传联调。当前共享业务数据库未实施用户级隔离，认证身份不能被当作项目 / 图谱权限隔离。
