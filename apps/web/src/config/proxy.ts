/** 开发代理仅默认连接本机多用户副本，其他环境通过进程变量显式指定。 */
const target = process.env.ONTOLOGY_API_TARGET || 'http://127.0.0.1:13002';
export default {
  '/api/v1': { target, changeOrigin: true },
};
