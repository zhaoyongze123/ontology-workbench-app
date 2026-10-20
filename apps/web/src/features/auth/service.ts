import { request } from 'umi';
import { clearCsrf, refreshCsrf } from './csrf';

export type InternalUser = {
  id: string;
  username: string;
  displayName: string;
  systemAdmin: boolean;
  enabled: boolean;
  authVersion: number;
};

export type AuthOptions = { passwordEnabled: boolean; workbenchEnabled: boolean };

export async function getCurrentUser(): Promise<InternalUser> {
  return request<InternalUser>('/api/v1/auth/me', { method: 'GET' });
}

export async function getAuthOptions(): Promise<AuthOptions> {
  return request<AuthOptions>('/api/v1/auth/options', { method: 'GET' });
}

export async function login(username: string, password: string): Promise<void> {
  const data = new URLSearchParams({ username, password });
  await request('/api/v1/auth/login', {
    method: 'POST',
    data,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    skipErrorHandler: true,
  });
  clearCsrf();
  await refreshCsrf();
}

export async function logout(allDevices = false): Promise<void> {
  await request(`/api/v1/auth/${allDevices ? 'logout-all' : 'logout'}`, { method: 'POST' });
  clearCsrf();
  // 服务端已销毁认证会话；若匿名CSRF刷新暂时失败，后续写请求会重新获取且不会带旧令牌。
  await refreshCsrf().catch(() => undefined);
}

export async function beginWorkbenchBinding(): Promise<{ authorizationUrl: string; expiresInSeconds: number }> {
  return request('/api/v1/auth/workbench/bind', { method: 'POST' });
}
