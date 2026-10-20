# 身份与配置边界

真实 OIDC Client Secret、OAuth Client Secret、数据库密码和令牌只在服务端私有配置中保存。仓库和 App 公开资源使用占位模板。测试账号的初始密码策略仅用于已确认的本机测试初始化。

浏览器通过 HttpOnly 会话 Cookie 登录；写请求同时校验 CSRF。公开 HTTPS 入口使用 Secure / SameSite=None，独立本机 HTTP 副本显式使用非 Secure / SameSite=Lax。二者不能混用。

项目权限由 Java 业务服务基于数据库中的当前成员关系判断。前端按钮、App scope、平台入口或成功登录都不能替代资源授权。

不记录密码、完整令牌、Cookie、授权码、Secret、原始 SQL 或敏感业务正文。问题报告使用脱敏的请求编号、状态、异常类型和 SQLState 等定位信息。

如凭据曾进入 Git，不能只删除工作区文件；须先撤销或轮换该凭据，再处理历史。不要在公开 Issue、PR 或日志中粘贴凭据。

