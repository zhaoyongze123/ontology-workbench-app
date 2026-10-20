/** 构建与运行时共享部署前缀，避免登录回跳重复拼接。 */
export const APP_BASE = '/ontology-management/';

export function appRelativePath(path: string): string {
  const prefix = APP_BASE.slice(0, -1);
  return path === prefix ? '/' : path.startsWith(APP_BASE) ? path.slice(prefix.length) : path;
}
