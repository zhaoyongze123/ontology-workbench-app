# 本体前端项目

`@umijs/max` 模板项目，更多功能参考 [Umi Max 简介](https://umijs.org/docs/max/introduce)

## 环境配置

项目支持多环境配置，通过环境变量 `API_ENV` 来切换不同的API地址：

- `local`: 本地开发环境 (http://192.168.110.109:13001/)
- `prod`: 线上生产环境 (https://ontology.gateway.tyrion.cloud/)

## 可用命令

```bash
# 安装依赖
npm install

# 启动开发服务器（默认使用本地环境）

npm run start:local

# 使用线上环境启动开发服务器
npm start
# 或
npm run start:prod

# 构建项目（默认环境）
npm run build

# 使用线上环境构建项目
npm run build:prod

# 格式化代码
npm run format
```

## 代理配置

代理配置位于 `src/config/proxy.ts` 文件中，会根据 `API_ENV` 环境变量自动选择对应的API地址。
