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
  APP_CLIENT_ENABLED: { label: '启用 OAuth 客户端', result: '成功' },
  APP_CLIENT_UPDATED: { label: '更新 OAuth 客户端配置', result: '成功' },
  APP_CLIENT_SECRET_ROTATED: { label: '轮换 OAuth 客户端 Secret', result: '成功' },
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

/** 筛选控件提交稳定事件编码，界面只展示已登记的中文名称。 */
export const AUDIT_EVENT_OPTIONS = Object.entries(EVENTS).map(
  ([value, event]) => ({
    value,
    label: event.label,
  }),
);

export function presentAuditEvent(
  event: AuditEvent,
): EventPresentation & { object: string } {
  const known = EVENTS[event.eventType];
  let object = event.objectDisplayName?.trim() || '—';
  if (!event.objectDisplayName?.trim()) {
    if (
      event.eventType.startsWith('ACCOUNT_') ||
      event.eventType === 'USER_SESSIONS_REVOKED'
    ) {
      object = '账号';
    } else if (event.eventType.startsWith('APP_CLIENT_')) {
      object = '应用客户端';
    } else if (event.eventType.startsWith('PROJECT_')) {
      object = '项目或项目成员';
    } else if (event.eventType.startsWith('SECURITY_')) {
      object = '接口请求';
    }
  }

  return {
    label: known?.label || '其他事件',
    result: known?.result || '未标注',
    object,
  };
}

export function presentAuditActor(
  event: Pick<AuditEvent, 'actorUserId' | 'actorDisplayName' | 'actorUsername'>,
): string {
  if (!event.actorUserId?.trim()) return '未记录身份';
  const displayName = event.actorDisplayName?.trim();
  const username = event.actorUsername?.trim();
  if (displayName && username) return `${displayName}（@${username}）`;
  return displayName || username || '已注销账号';
}
