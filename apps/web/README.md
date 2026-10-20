# Ontology Web

多用户页面使用 Umi / React / Ant Design，部署前缀统一为 `/ontology-management/`，业务 API 固定经同源 `/api/v1` 访问。

```sh
npm ci
npm run setup
npm run typecheck
MULTIUSER_BUILD=1 npm run build
```

本机多用户构建输出到 `dist-multiuser/`；当前隔离验收入口为 `http://127.0.0.1:18083/ontology-management/`。后端未完成的能力不会因页面按钮存在而视为已实现。

`npm start` / `npm run dev` 的代理默认只连接 `127.0.0.1:13002`，需要改目标时显式设置 `ONTOLOGY_API_TARGET`；开发服务器的 Origin 还须加入后端可信来源配置。构建后的静态站点由同源 Nginx 转发 API，不使用开发代理。

本目录遗留 Docker / Nginx 文件来自原项目，未作为新多用户部署链路验收。请以根目录 README 和 `deploy/local/` 的当前说明为准。旧云端发布脚本已移除，所有凭据放入仓库之外的私有配置。
