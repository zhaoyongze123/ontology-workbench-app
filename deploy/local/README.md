# 本机隔离验收配置

当前实际链路：浏览器 → Nginx `127.0.0.1:18083` → 静态前端 / Java `127.0.0.1:13002` → Docker PostgreSQL + AGE 的独立数据库副本。

- `application.example.yaml`：仓库外私有配置模板；启动时启用 `multiuser` profile，用 `--spring.config.additional-location=file:/absolute/private/application.yaml` 显式加载。
- `nginx.example.conf`：与实际本机验收相同的路由结构。先替换绝对路径和本机 `mime.types` 位置，配置、PID 与日志放在仓库之外；只监听回环地址，不自动启动隧道。
- 本机 HTTP 验收须在私有配置中覆盖 `cookie-secure: false`、`cookie-same-site: Lax`，并设置确切的 `trusted-origins: http://127.0.0.1:18083`。HTTPS 嵌入配置使用模板中的 Secure + SameSite=None，实际平台 Cookie 兼容仍须验收。
- 日常运行账号不得使用数据库超级用户；Flyway 迁移和受控 bootstrap 分别由维护流程执行。模板默认关闭自动迁移，不能直接用它初始化空库。
- Workbench 默认关闭。真实 clientSecret 只能放入私有后端配置，不能写入前端构建产物。

原项目 Docker 文件属于参考遗留资料，不是新的多用户启动入口。此处不自动升级原库、不覆盖旧公网 Nginx，也不发布正式 App 包。
