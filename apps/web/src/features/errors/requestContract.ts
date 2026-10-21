/** 只给普通业务 REST API 协商 Problem Details，协议端点保留其原生响应格式。 */
export function shouldRequestProblemDetails(url: string): boolean {
  try {
    const pathname = new URL(url, 'http://ontology.local').pathname;
    const isBusinessApi = pathname.startsWith('/api/');
    const isProtocolEndpoint = /(?:^|\/)(?:oidc|oauth2)(?:\/|$)|(?:^|\/)\.well-known(?:\/|$)/i.test(pathname);
    return isBusinessApi && !isProtocolEndpoint;
  } catch {
    return false;
  }
}
