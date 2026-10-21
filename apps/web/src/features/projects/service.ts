import { localRequest as request } from '@/features/errors/localRequest';
import type { ProjectRole } from './types';
export type { ProjectRole } from './types';

export type ProjectCapability = 'READ' | 'EDIT' | 'EDIT_PROJECT' | 'DELETE_GRAPH' | 'MANAGE_MEMBERS'
  | 'DELETE_PROJECT' | 'TRANSFER_OWNERSHIP';
export type ProjectAccess = { role: 'SYSTEM_ADMIN' | ProjectRole; capabilities: ProjectCapability[] };
export type ProjectMember = { userId: string; username: string; displayName: string; role: ProjectRole };
export type ProjectUserOption = { id: string; username: string; displayName: string };
export type ProjectInvitation = {
  id: string;
  projectName: string;
  inviterUserId: string;
  inviterDisplayName: string;
  targetUserId: string;
  targetDisplayName: string;
  role: ProjectRole;
  status: string;
  createdTime: string;
  expiresTime: string;
};

export type ApiEnvelope<T> = { success?: boolean; message?: string; data?: T; addition?: { total?: number; pageNum?: number; pageSize?: number } };

export async function getProjectAccess(projectId: string) {
  return request<ApiEnvelope<ProjectAccess>>(`/api/v1/projects/${encodeURIComponent(projectId)}/access`, { method: 'GET' });
}

export async function getProjectMembers(projectId: string, pageNum = 1, pageSize = 20) {
  return request<ApiEnvelope<ProjectMember[]>>(`/api/v1/projects/${encodeURIComponent(projectId)}/members`, {
    method: 'GET', params: { pageNum, pageSize },
  });
}

export async function searchProjectUsers(q: string, pageNum = 1, pageSize = 20) {
  return request<ApiEnvelope<ProjectUserOption[]>>('/api/v1/users/search', { method: 'GET', params: { q, pageNum, pageSize } });
}

export async function inviteProjectUser(projectId: string, targetUserId: string, role: Exclude<ProjectRole, 'OWNER'>) {
  return request<ApiEnvelope<unknown>>(`/api/v1/projects/${encodeURIComponent(projectId)}/invitations`, {
    method: 'POST', data: { targetUserId, role },
  });
}

export async function updateProjectMember(projectId: string, userId: string, role: Exclude<ProjectRole, 'OWNER'>) {
  return request<ApiEnvelope<unknown>>(`/api/v1/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(userId)}`, {
    method: 'PUT', data: { role },
  });
}

export async function removeProjectMember(projectId: string, userId: string) {
  return request<ApiEnvelope<unknown>>(`/api/v1/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(userId)}`, { method: 'DELETE' });
}

export async function leaveProject(projectId: string) {
  return request<ApiEnvelope<unknown>>(`/api/v1/projects/${encodeURIComponent(projectId)}/leave`, { method: 'POST' });
}

export async function transferProjectOwner(projectId: string, targetUserId: string) {
  return request<ApiEnvelope<unknown>>(`/api/v1/projects/${encodeURIComponent(projectId)}/transfer-owner`, {
    method: 'POST', data: { targetUserId },
  });
}

export async function getMyInvitations(pageNum = 1, pageSize = 20) {
  return request<ApiEnvelope<ProjectInvitation[]>>('/api/v1/project-invitations/mine', { method: 'GET', params: { pageNum, pageSize } });
}

export async function respondProjectInvitation(invitationId: string, action: 'accept' | 'decline') {
  return request<ApiEnvelope<ProjectInvitation>>(`/api/v1/project-invitations/${encodeURIComponent(invitationId)}/${action}`, { method: 'POST' });
}
