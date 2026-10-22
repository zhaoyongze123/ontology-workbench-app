# Ontology 单机部署

服务器安装 Docker Engine 和 Docker Compose v2，并预先收到固定 tag 或 commit 对应的完整 Ontology 源码压缩包。压缩包根目录应包含 `apps/server/`、`apps/web/` 和 `deploy/compose.yaml`。Compose 从源码本机构建 PostgreSQL/AGE、Java API 和 Web；API 镜像同时提供独立的一次性 Flyway 迁移任务。部署不需要 Git、镜像仓库账号或 Ontology 预构建镜像。首次构建需能访问 Maven、npm、APT 和容器基础镜像仓库。

## 首次部署

1. 将完整源码压缩包上传到服务器并解压到固定目录：

   ```sh
   mkdir -p /opt/ontology
   tar -xzf /path/to/ontology-source.tar.gz --strip-components=1 -C /opt/ontology
   cd /opt/ontology
   ```

2. 创建配置并设置域名和 HTTPS 网关来源网段：

   ```sh
   cp deploy/.env.example deploy/.env
   vi deploy/.env
   ```

   至少修改 `PUBLIC_ORIGIN` 和 `EDGE_TRUSTED_PROXY_CIDRS`。若 Docker 网段与服务器已有网络冲突，再调整 `BACKEND_SUBNET`、`WEB_BACKEND_IP`、`API_TRUSTED_PROXY_REGEX`。

3. 生成数据库密码和运行配置：

   ```sh
   deploy/ontology.sh init-config
   ```

4. 创建首个管理员文件 `deploy/private/bootstrap-input/bootstrap-admin.json`：

   ```sh
   install -d -m 700 deploy/private/bootstrap-input
   umask 077
   cat > deploy/private/bootstrap-input/bootstrap-admin.json <<'JSON'
   {
     "schemaVersion": 1,
     "username": "admin",
     "displayName": "System Administrator",
     "password": "<至少12位的随机强密码>"
   }
   JSON
   chmod 600 deploy/private/bootstrap-input/bootstrap-admin.json
   ```

5. 如需 Workbench 登录，在 `deploy/.env` 设置 `WORKBENCH_OIDC_ENABLED=true`，然后将 Client ID 和 Client Secret 分别写入以下文件：

   - `deploy/private/api-config/ontology.workbench.client-id`
   - `deploy/private/api-config/ontology.workbench.client-secret`

   ```sh
   vi deploy/private/api-config/ontology.workbench.client-id
   vi deploy/private/api-config/ontology.workbench.client-secret
   chmod 600 deploy/private/api-config/ontology.workbench.client-id deploy/private/api-config/ontology.workbench.client-secret
   ```

6. 构建并启动：

   ```sh
   deploy/ontology.sh install
   ```

   该命令按顺序构建三个部署镜像、启动数据库、运行 Flyway 迁移、初始化管理员，再启动 API 和 Web。迁移失败时不会启动应用服务。

7. 配置现有 HTTPS 网关，将域名转发到 `127.0.0.1:18083`，然后检查服务：

   ```sh
   deploy/ontology.sh status
   ```

## 更新版本

1. 先备份数据库：

   ```sh
   deploy/ontology.sh backup
   ```

2. 将新交付版本的完整源码压缩包上传并覆盖解压到 `/opt/ontology`，然后重建并启动。保留 `deploy/.env` 和 `deploy/private/`：

   ```sh
   tar -xzf /path/to/ontology-source.tar.gz --strip-components=1 -C /opt/ontology
   cd /opt/ontology
   deploy/ontology.sh up
   ```

   `up` 会从新版本源码构建数据库、API 和 Web 镜像；Compose 在 API 启动前执行迁移。

应用包由项目负责人单独构建并上传 Workbench，不属于服务器部署步骤。
