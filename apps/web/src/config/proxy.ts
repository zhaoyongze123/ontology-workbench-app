/** 代理配置 */
const proxyHosts = {
  prod: 'https://ontology.gateway.tyrion.cloud/' /** app */,
  local: 'http://192.168.10.6:13001/' /** 本地 */,
};

/** 获取当前环境变量 */
const ENV = process.env.API_ENV || 'local';

/** 配置开发环境API代理 */
const proxy = {
  /** 代理配置 */
  '/api/v1': {
    target: proxyHosts[ENV as keyof typeof proxyHosts],
    /** 是否改变请求源 */
    changeOrigin: true,
    /** 路径重写 */
    pathRewrite: { '^/api/v1': '/api/v1' },
  },
};

export default proxy;
