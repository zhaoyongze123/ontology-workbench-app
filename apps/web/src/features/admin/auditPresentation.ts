import type { AuditEvent } from './service';

type EventPresentation = { label: string; result: string };

// 只为已登记的事件写明含义；未知事件统一展示原码和“未标注”，不靠字符串推断结果。
const EVENTS: Record<string, EventPresentation> = {
  ACCOUNT_ACTIVATION_LINK_ISSUED: { label: '生成账号激活链接', result: '成功' },
  ACCOUNT_ENABLED: { label: '启用账号', result: '成功' },
  ACCOUNT_DISABLED: { label: '停用账号', result: '成功' },
  ACCOUNT_PASSWORD_RESET_LINK_ISSUED: {
    label: '生成密码重置链接',
    result: '成功',
  },
  ACCOUNT_PASSWORD_ACTIVATED: { label: '设置本地密码', result: '成功' },
  ACCOUNT_PASSWORD_RESET: { label: '重置本地密码', result: '成功' },
  USER_SESSIONS_REVOKED: { label: '撤销账号会话和 App 授权', result: '成功' },
  APP_CLIENT_CREATED: { label: '注册 OAuth 客户端', result: '成功' },
  APP_CLIENT_DISABLED: { label: '停用 OAuth 客户端', result: '成功' },
  WORKBENCH_OIDC_LOGIN: { label: 'Workbench 账号登录', result: '成功' },
  WORKBENCH_OIDC_USER_CREATED: {
    label: '创建 Workbench 身份绑定',
    result: '成功',
  },
  WORKBENCH_OIDC_BOUND: { label: '绑定 Workbench 账号', result: '成功' },
  PROJECT_CREATED: { label: '创建项目', result: '成功' },
  PROJECT_UPDATED: { label: '更新项目', result: '成功' },
  PROJECT_DELETED: { label: '删除项目', result: '成功' },
  PROJECT_GRAPH_CREATED: { label: '创建图谱', result: '成功' },
  PROJECT_GRAPH_UPDATED: { label: '更新图谱', result: '成功' },
  PROJECT_GRAPH_DELETED: { label: '删除图谱', result: '成功' },
  PROJECT_GRAPH_EXPORTED: { label: '导出图谱', result: '成功' },
  PROJECT_GRAPH_IMPORTED: { label: '导入图谱', result: '成功' },
  PROJECT_NODE_CREATED: { label: '创建图谱节点', result: '成功' },
  PROJECT_NODE_UPDATED: { label: '更新图谱节点', result: '成功' },
  PROJECT_NODE_DELETED: { label: '删除图谱节点', result: '成功' },
  PROJECT_RELATIONSHIP_CREATED: { label: '创建图谱关系', result: '成功' },
  PROJECT_RELATIONSHIP_UPDATED: { label: '更新图谱关系', result: '成功' },
  PROJECT_RELATIONSHIP_DELETED: { label: '删除图谱关系', result: '成功' },
  PROJECT_INVITATION_CREATED: { label: '发出项目邀请', result: '成功' },
  PROJECT_INVITATION_ACCEPTED: { label: '接受项目邀请', result: '已接受' },
  PROJECT_INVITATION_DECLINED: { label: '拒绝项目邀请', result: '已拒绝' },
  PROJECT_INVITATION_EXPIRED: { label: '项目邀请过期', result: '已过期' },
  PROJECT_INVITATION_REVOKED: { label: '撤销项目邀请', result: '已撤销' },
  PROJECT_MEMBER_ROLE_CHANGED: { label: '修改项目成员角色', result: '成功' },
  PROJECT_MEMBER_REMOVED: { label: '移除项目成员', result: '成功' },
  PROJECT_MEMBER_LEFT: { label: '退出项目', result: '成功' },
  PROJECT_OWNERSHIP_TRANSFERRED: { label: '转移项目所有权', result: '成功' },
  SECURITY_AUTHENTICATION_FAILURE: {
    label: '请求身份验证失败',
    result: '已拒绝',
  },
  SECURITY_ACCESS_DENIED: { label: '请求访问被拒绝', result: '已拒绝' },
  SECURITY_RATE_LIMITED: { label: '请求触发频率限制', result: '已限流' },
  SECURITY_REQUEST_FAILURE: { label: '请求处理失败', result: '失败' },
  SECURITY_OAUTH_PROTOCOL_REJECTED: {
    label: 'OAuth 请求被拒绝',
    result: '协议拒绝',
  },
};

const SHORT_ID_LENGTH = 8;

function shortenedId(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value.trim()) return undefined;
  const normalized = value.trim();
  return normalized.length <= SHORT_ID_LENGTH
    ? normalized
    : `${normalized.slice(0, SHORT_ID_LENGTH)}…`;
}

function detailString(
  details: Record<string, unknown>,
  key: string,
): string | undefined {
  const value = details[key];
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

export function presentAuditEvent(
  event: AuditEvent,
): EventPresentation & { object: string } {
  const known = EVENTS[event.eventType];
  const details = event.details || {};
  let object = '—';

  if (
    event.eventType.startsWith('ACCOUNT_') ||
    event.eventType === 'USER_SESSIONS_REVOKED'
  ) {
    const userId = shortenedId(detailString(details, 'targetUserId'));
    if (userId) object = `用户 ID ${userId}`;
  } else if (event.eventType.startsWith('APP_CLIENT_')) {
    const clientId = shortenedId(detailString(details, 'clientId'));
    if (clientId) object = `客户端 ID ${clientId}`;
  } else if (event.eventType.startsWith('PROJECT_')) {
    const projectId = shortenedId(detailString(details, 'projectId'));
    const invitationId = shortenedId(detailString(details, 'invitationId'));
    const targetUserId = shortenedId(detailString(details, 'targetUserId'));
    const subject = invitationId
      ? `邀请 ID ${invitationId}`
      : targetUserId
      ? `用户 ID ${targetUserId}`
      : undefined;
    if (projectId)
      object = subject
        ? `项目 ID ${projectId} · ${subject}`
        : `项目 ID ${projectId}`;
  } else if (event.eventType.startsWith('SECURITY_')) {
    const path = detailString(details, 'path');
    if (path) object = path;
  }

  return {
    label: known?.label || '其他事件',
    result: known?.result || '未标注',
    object,
  };
}

export function presentAuditActor(actorUserId: string | null): string {
  if (!actorUserId?.trim()) return '未记录身份';
  return `用户 ID ${shortenedId(actorUserId)}`;
}
