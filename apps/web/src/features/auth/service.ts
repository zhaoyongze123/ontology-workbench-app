import { localRequest } from '@/features/errors/localRequest';
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
  return localRequest<InternalUser>('/api/v1/auth/me', { method: 'GET' });
}

export async function getAuthOptions(): Promise<AuthOptions> {
  return localRequest<AuthOptions>('/api/v1/auth/options', { method: 'GET' });
}

export async function login(username: string, password: string): Promise<void> {
  const data = new URLSearchParams({ username, password });
  await localRequest('/api/v1/auth/login', {
    method: 'POST',
    data,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
  clearCsrf();
  await refreshCsrf();
}

export async function logout(allDevices = false): Promise<void> {
  await localRequest(`/api/v1/auth/${allDevices ? 'logout-all' : 'logout'}`, { method: 'POST' });
  clearCsrf();
  // 服务端已销毁认证会话；若匿名CSRF刷新暂时失败，后续写请求会重新获取且不会带旧令牌。
  await refreshCsrf().catch(() => undefined);
}

export async function beginWorkbenchBinding(): Promise<{ authorizationUrl: string; expiresInSeconds: number }> {
  return localRequest('/api/v1/auth/workbench/bind', { method: 'POST' });
}
