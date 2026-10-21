import { localRequest as request } from '@/features/errors/localRequest';

export type ManagedUser = { id: string; username: string; displayName: string; systemAdmin: boolean; enabled: boolean; passwordActivated: boolean; createTime: string };
export type AccountPage = { items: ManagedUser[]; page: number; size: number; total: number };
export const getUsers = (page: number, size: number) => request<AccountPage>('/api/v1/admin/users', { method: 'GET', params: { page, size } });
export const createUser = (username: string, displayName: string) => request<{ user: ManagedUser; activationUrl: string }>('/api/v1/admin/users', { method: 'POST', data: { username, displayName } });
export const setUserEnabled = (id: string, enabled: boolean) => request<ManagedUser>('/api/v1/admin/users/' + encodeURIComponent(id) + '/enabled', { method: 'PATCH', data: { enabled } });
export const createResetLink = (id: string) => request<{ resetUrl: string }>('/api/v1/admin/users/' + encodeURIComponent(id) + '/password-reset-link', { method: 'POST' });
export const createActivationLink = (id: string) => request<{ activationUrl: string }>('/api/v1/admin/users/' + encodeURIComponent(id) + '/activation-link', { method: 'POST' });

export type AppClient = { id: string; clientId: string; displayName: string; redirectUri: string; scopes: string[]; enabled: boolean; createdAt: string };
export type CreatedAppClient = { clientId: string; clientSecret: string; displayName: string; redirectUri: string; scopes: string[] };
export const getOAuthClients = () => request<AppClient[]>('/api/v1/admin/oauth/clients', { method: 'GET' });
export const createOAuthClient = (data: { displayName: string; redirectUri: string; scopes: string[] }) => request<CreatedAppClient>('/api/v1/admin/oauth/clients', { method: 'POST', data });
export const disableOAuthClient = (id: string) => request<{ status: string }>('/api/v1/admin/oauth/clients/' + encodeURIComponent(id), { method: 'DELETE' });

export type AuditEvent = { id: number; eventType: string; actorUserId: string | null; details: Record<string, unknown>; createdAt: string | null };
export const getAuditEvents = (limit: number, beforeId?: number) => request<AuditEvent[]>('/api/v1/admin/audit-events', { method: 'GET', params: { limit, beforeId } });

export type AppAuthorization = { id: string; applicationName: string; scopes: string; authorizedAt: string; absoluteExpiresAt: string; revokedAt: string | null };
export const getAppAuthorizations = () => request<AppAuthorization[]>('/api/v1/auth/app-authorizations', { method: 'GET' });
export const revokeAppAuthorization = (id: string) => request<{ status: string }>('/api/v1/auth/app-authorizations/' + encodeURIComponent(id), { method: 'DELETE' });
