# Ontology 单机部署

服务器需运行 Linux x86_64、Docker Engine 和 Docker Compose v2，并能访问私有 GHCR。使用服务器现有 HTTPS 网关；Workbench App 包由项目负责人单独处理。

## 首次部署

1. 将交付包解压到服务器的独立目录：

   ```sh
   sudo mkdir -p /opt/ontology
   sudo chown "$(id -u):$(id -g)" /opt/ontology
   tar -xzf ontology-deployment-bundle.tar.gz -C /opt/ontology
   cd /opt/ontology
   ```

2. 复制并编辑部署配置：

   ```sh
   cp deploy/.env.example deploy/.env
   vi deploy/.env
   ```

   设置正式 HTTPS 域名、网关连接 web 时的实际来源网段和固定镜像版本：

   ```dotenv
   PUBLIC_ORIGIN=https://<正式域名>
   EDGE_TRUSTED_PROXY_CIDRS=<网关来源网段>
   IMAGE_PREFIX=ghcr.io/zhaoyongze123/ontology-workbench-app
   IMAGE_TAG=0.2.0-rc.2
   ```

   服务器端口默认 `18083`。若 `BACKEND_SUBNET` 与服务器网络冲突，再一并调整 `BACKEND_SUBNET`、`WEB_BACKEND_IP`、`API_TRUSTED_PROXY_REGEX`。需要 Workbench 账号登录时，同时设置 `WORKBENCH_OIDC_ENABLED=true`。

3. 生成本机私有配置和数据库密码：

   ```sh
   deploy/ontology.sh init-config
   ```

   **Workbench 账号登录（可选）**

   仅在第 2 步设置了 `WORKBENCH_OIDC_ENABLED=true` 时配置。把 Workbench 提供的 Client ID 和 Client Secret 分别保存为两个文件：

   - `deploy/private/api-config/ontology.workbench.client-id`：只放 Client ID。
   - `deploy/private/api-config/ontology.workbench.client-secret`：只放 Client Secret。

   ```sh
   vi deploy/private/api-config/ontology.workbench.client-id
   vi deploy/private/api-config/ontology.workbench.client-secret
   chmod 600 deploy/private/api-config/ontology.workbench.client-id deploy/private/api-config/ontology.workbench.client-secret
   ```

4. 登录 GHCR 并拉取服务镜像及数据库迁移工具：

   ```sh
   docker login ghcr.io
   deploy/ontology.sh pull
   ```

   使用有私有镜像读取权限的账户；不要把访问令牌写入命令参数或配置文件。部署只运行 `ontology-db`、`ontology-api`、`ontology-web` 三个常驻镜像；`ontology-migrate` 只在迁移时运行一次，不拉取或运行 App 打包镜像。

5. 初始化数据库结构和权限：

   ```sh
   deploy/ontology.sh migrate
   ```

6. 创建首个系统管理员并初始化：

   文件路径是 `deploy/private/bootstrap-input/bootstrap-admin.json`，内容如下：

   ```json
   {
     "schemaVersion": 1,
     "username": "admin",
     "displayName": "System Administrator",
     "password": "<至少12位的随机强密码>"
   }
   ```

   将占位密码换成随机强密码；用户名和密码不能相同。用编辑器创建该文件，再执行：

   ```sh
   install -d -m 700 deploy/private/bootstrap-input
   umask 077
   vi deploy/private/bootstrap-input/bootstrap-admin.json
   chmod 600 deploy/private/bootstrap-input/bootstrap-admin.json
   deploy/ontology.sh init-admin
   ```

7. 启动服务，并让现有 HTTPS 网关将该域名转发到 `127.0.0.1:18083`：

   ```sh
   deploy/ontology.sh up
   deploy/ontology.sh status
   ```
