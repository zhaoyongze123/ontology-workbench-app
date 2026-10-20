import { defineConfig } from '@umijs/max';
import proxy from './src/config/proxy';
import routes from './src/config/routes';
import { APP_BASE } from './src/config/appBase';

export default defineConfig({
  antd: {},
  base: APP_BASE,
  publicPath: APP_BASE,
  // 多用户版独立产物目录，不覆盖正在对外使用的 dist。
  outputPath: process.env.MULTIUSER_BUILD === '1' ? 'dist-multiuser' : 'dist',
  // history: {
  //   type: 'hash', // 切换为hash模式（URL带#，无需服务器配置）
  // },
  access: {},
  model: {},
  initialState: {},
  request: {
    dataField: 'data',
  },
  layout: {
    title: '本体管理',
  },
  /** 修改icon */
  favicons: [`${APP_BASE}TyrionIcon.png`],
  routes,
  npmClient: 'npm',
  proxy,
  hash: true,
  esbuildMinifyIIFE: true,
  // mako: {}, /** 启用 mako 构建工具 优化运行构建速度 */
});
