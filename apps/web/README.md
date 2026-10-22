# Ontology Web

多用户页面使用 Umi / React / Ant Design，部署前缀统一为 `/ontology-management/`，业务 API 固定经同源 `/api/v1` 访问。生产静态资源由 `deploy/Dockerfile.web` 构建并由交付 Nginx 提供；部署和反向代理配置见根目录 [Docker 部署指南](../../docs/deployment.md)。

前端源码检查：

```sh
npm ci
npm run setup
npm run typecheck
MULTIUSER_BUILD=1 npm run build
```

本目录只维护前端源码、构建和测试，不再提供独立 Nginx 或旧 Docker 启动入口。后端未完成的能力不会因页面按钮存在而视为已实现。
