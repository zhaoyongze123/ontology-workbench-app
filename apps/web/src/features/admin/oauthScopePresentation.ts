export const OAUTH_SCOPE_OPTIONS = [
  { value: 'profile', label: '基本身份', description: 'profile' },
  { value: 'ontology.read', label: '图谱读取', description: 'ontology.read' },
  { value: 'ontology.write', label: '图谱修改', description: 'ontology.write' },
  {
    value: 'ontology.manage',
    label: '项目与成员管理',
    description: 'ontology.manage',
  },
];

/** 用户列表优先展示权限含义；协议 scope 仅用于系统间传参。 */
export function oauthScopeLabel(scope: string): string {
  return (
    OAUTH_SCOPE_OPTIONS.find((option) => option.value === scope)?.label ||
    '其他权限'
  );
}
