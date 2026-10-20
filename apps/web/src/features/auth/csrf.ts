type CsrfPayload = { token?: string; headerName?: string };

let csrfToken: string | null = null;
let csrfHeader = 'X-CSRF-TOKEN';
let refreshPromise: Promise<void> | null = null;

/** CSRF 令牌只驻留内存；会话身份与令牌都不写入浏览器持久存储。 */
export async function refreshCsrf(): Promise<void> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    const response = await fetch('/api/v1/auth/csrf', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) throw new Error('无法获取安全令牌，请刷新页面后重试');
    const payload = (await response.json()) as CsrfPayload;
    if (!payload.token) throw new Error('服务未返回安全令牌');
    csrfToken = payload.token;
    // 后端契约固定该头名，忽略意外响应中的任意头名。
    csrfHeader = payload.headerName === 'X-CSRF-TOKEN' ? payload.headerName : 'X-CSRF-TOKEN';
  })();
  try {
    await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

export function clearCsrf(): void {
  csrfToken = null;
}

export async function csrfRequestOptions(
  url: string,
  options: any,
): Promise<{ url: string; options: any }> {
  const method = String(options.method || 'GET').toUpperCase();
  const unsafe = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method);
  const next = { ...options, withCredentials: true };
  if (unsafe) {
    if (!csrfToken) await refreshCsrf();
    next.headers = { ...(next.headers || {}), [csrfHeader]: csrfToken! };
  }
  return { url, options: next };
}
