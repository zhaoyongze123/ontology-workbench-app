import { request } from 'umi';

export type AccountActionPurpose = 'ACTIVATE' | 'RESET_PASSWORD';
export async function inspectAccountAction(token: string) {
  return request<{ purpose: AccountActionPurpose; valid: boolean }>('/api/v1/auth/account-actions/inspect', {
    method: 'POST', data: { token }, skipErrorHandler: true,
  });
}
export async function submitAccountAction(purpose: AccountActionPurpose, token: string, newPassword: string) {
  return request<{ status: 'activated' | 'password_reset' }>(
    `/api/v1/auth/${purpose === 'ACTIVATE' ? 'activate' : 'password-reset'}`,
    { method: 'POST', data: { token, newPassword }, skipErrorHandler: true },
  );
}
