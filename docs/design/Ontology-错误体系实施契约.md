# Ontology 错误体系实施契约

本文记录普通业务 API、Workbench UI 和 OAuth/OIDC 协议端点之间的错误边界。目标是让业务原因稳定可识别、HTTP 状态可依赖、用户提示安全，并让成功响应和 OAuth 协议保持现有契约。

## 普通业务 API

服务端错误原因集中定义在 `ApiErrorCode`。领域服务抛出稳定原因，不依赖 HTTP；`HttpErrorMapping` 只在 HTTP 边界决定状态码。未知异常统一作为 `INTERNAL_ERROR` 返回，不从数据库、SQL、请求正文或异常文本推导用户提示。

请求明确接受 `application/problem+json` 时，普通 REST API 返回 RFC 9457 Problem Details，并增加 `errorCode`、`requestId` 以及可选字段错误：

```json
{
  "type": "urn:ontology:problem:account-last-admin",
  "title": "状态冲突",
  "status": 409,
  "detail": "至少需要保留一个启用的系统管理员。",
  "instance": "urn:uuid:...",
  "errorCode": "ACCOUNT_LAST_ADMIN",
  "requestId": "...",
  "errors": [{ "field": "username", "code": "VALIDATION_FAILED", "message": "用户名不能为空。" }]
}
```

`requestId` 由服务端生成；响应体和 `X-Request-ID` 一致。错误响应设置 `Cache-Control: no-store`、`X-Content-Type-Options: nosniff` 和 `Vary: Accept`。字段错误只包含字段路径、稳定原因和安全提示，不包含用户提交的拒绝值。

旧客户端未明确请求 Problem Details 时，继续收到对应端点原有的 `ApiResult` 或 `{ "error": "..." }` JSON 结构。普通错误的 HTTP 状态按照下表修正；成功响应结构不变。

| HTTP 状态 | 含义 |
| --- | --- |
| 400 | 格式、参数或字段校验失败 |
| 401 | 未登录或凭证无效 |
| 403 | 已认证但无权操作，或 CSRF/Origin 校验拒绝 |
| 404 | 资源不存在或当前用户不可见 |
| 409 | 重复资源或业务状态冲突 |
| 422 | 查询语法、安全策略或查询资源限制 |
| 429 | 请求频率受限，可附 `Retry-After` |
| 500 | 未知内部故障 |
| 503 | 已识别的暂时性依赖故障 |

SQLSTATE `42601` 只在只读 Cypher 执行层解释为查询语法问题；其他数据库异常不得借此变成 4xx。连接类 SQLSTATE `08` 与暂时性资源故障才归为 503；已识别的唯一键/外键约束冲突（`23505`、`23503`）归为 409，其他未识别的数据库错误归为安全的 500。

## 前端错误呈现

`normalizeApiError` 是前端协议入口，统一解析 Problem Details、遗留错误体、取消、超时和网络错误。它使用状态码及稳定 `errorCode`，不解析中文异常文案或数据库内容。服务端 5xx 提示会附上 `requestId`。网络/超时导致写请求没有 HTTP 响应时，提示“提交结果暂未确认”，不自动重放。

普通 API 默认由全局处理器提示。需要表单字段、页面内错误区域或确认弹窗负责提示的调用使用 `localRequest`，它关闭全局提示并将同一错误转换成 `AppError`；该页面必须负责展示或保留可见错误状态，不能用空 `catch` 吞掉失败。取消请求不提示。

## OAuth/OIDC 边界

授权、Token 与撤销等 OAuth 协议端点继续由 Spring Authorization Server 及 OAuth 专用过滤器处理，保留 `invalid_request`、`invalid_grant`、`invalid_client` 等标准错误语义；前端错误协商器和生成的 OpenAPI 也会排除 OAuth/OIDC 协议路径。OIDC UserInfo 成功响应保持现有 JSON 结构，身份/权限拒绝由 Bearer 认证过滤器处理。Bearer API 401 必须携带适当的 `WWW-Authenticate: Bearer` challenge；无效 Token 使用 `invalid_token`，缺少权限使用 `insufficient_scope`。撤销不存在或已失效的 Token 仍按 RFC 7009 成功语义返回 200。

生成的 OpenAPI 为普通业务 API 发布 `OntologyProblemDetails`、字段错误及旧 JSON envelope 组件，并为业务操作补充常见错误响应；OAuth/OIDC 操作保持专属协议文档，不套普通业务响应 schema。

相关标准：[RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html)、[RFC 6750 Bearer Token Usage](https://www.rfc-editor.org/rfc/rfc6750.html)、[RFC 7009 Token Revocation](https://www.rfc-editor.org/rfc/rfc7009.html)。

## 变更与验证要求

新增业务错误时，在 `ApiErrorCode` 中增加稳定代码和中英文安全消息，在 `HttpErrorMapping` 中明确状态，在业务服务抛出该代码，并为 Problem Details、遗留格式和状态语义补充测试。不得通过异常文案、数据库消息或前端分支猜测错误原因。OAuth 改动必须额外验证标准错误、Bearer challenge、令牌撤销和响应体兼容。
