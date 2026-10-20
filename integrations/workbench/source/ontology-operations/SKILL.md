---
name: ontology-operations
description: 管理 Ontology 项目、本体图谱、实体、属性和关系；当用户需要建模、查看或维护 Ontology 图谱时使用。
---

# Ontology 操作

使用本成员 scripts/ontology.py，通过 stdin 传入单个 JSON 对象。每个任务开始先执行 operation=status；它只检查当前 Runtime 是否注入 App Bearer，`verified=false` 不表示 App 授权或远端身份已验证。

若缺少 Bearer，实际调用 Workbench 原生 `request_user_input`，参数必须为：

```json
{"title":"连接 Ontology","description":"通过 Workbench 授权后继续访问 Ontology。","authorization":{"type":"app_oauth","appKey":"ontology"},"fields":[]}
```

不要展示伪造登录链接、询问用户复制 Token，或把 Client Secret 放到成员资源中。若宿主不支持该原生授权合同，明确报告并停止依赖身份的请求。授权成功并恢复执行后，重新运行 status，再调用 `identity` 验证 broker 返回的当前用户；status 自身不调用身份端点，也不证明用户身份。

App OAuth broker 使用独立 App 客户端和固定 Workbench 回调；它在服务端持有自己的上游 OIDC 客户端。成员只接收 `APP_OAUTH_ONTOLOGY_ACCESS_TOKEN` opaque Bearer，通过 `/oidc/app/userinfo` 查询身份、通过 `/api/v1` 访问业务 API。成员不接触 broker Client Secret、上游 OIDC Secret 或上游 Token。Workbench 原生授权面板是否按此配置完成交换和凭证注入，仍需上传联调确认。

先区分本体 ONTOLOGY 与实体 ENTITY：本体定义类型、属性与关系；实体图谱关联本体图谱，具体对象关联本体类型。项目 → 图谱 → 节点 / 关系依次解析，不虚构 ID，也不把图存储内部 ID 当业务 ID。

按 references/operations.json 的白名单选择 operation，只传 query 和 body 所声明的参数。接口说明见 references/api-guide.md。ID 使用原始十进制字符串。保留 success、data 和 addition，完整处理分页，不将一次返回的部分记录说成全部。

写入前确定用户授权的具体目标、变更范围与输入，再设置 _confirm=true；仅查看或列出图谱不授权修改。业务校验由原 Ontology 后端执行，不绕过 API 直接写数据库。

previewGraph 只执行固定的 MATCH (n) RETURN n LIMIT 100，不接受自由 Cypher。第一版的 JSON 文件导入与自由查询通过原应用页面进行。

响应中的页面链接仅从当前 service.json 和真实业务 ID 构造；失败时报告原因，HTTP 401 引导重新授权，403 说明无权限，网络失败检查临时服务及隧道，不切换凭证掩盖失败。

`identity` 操作只返回 broker 约定的 `id`、`username`、`sub`，以及上游实际提供时的 `name`；不使用其他字段伪造姓名。身份 ID 按提供方的原始字符串保留；业务 API ID 以十进制字符串传递。当前共享业务数据库没有用户级数据隔离；App 授权证明调用者身份，不代表项目或图谱已按用户隔离。

包已准备供 Workbench 上传联调；入口、浏览器登录成功确认和 App 资源授权是不同检查点。浏览器登录成功不代表 App API 授权已验证，也不代表租户级数据隔离已经实现。
