import { AppstoreOutlined, BranchesOutlined } from '@ant-design/icons';

export default [
  {
    path: '/login',
    name: '登录',
    component: './Login',
    layout: false,
  },
  { path: '/account/activate', name: '设置账号密码', component: './AccountAction', layout: false },
  { path: '/account/reset-password', name: '重置账号密码', component: './AccountAction', layout: false },
  {
    path: '/',
    redirect: '/home',
  },
  {
    path: '/home',
    name: '项目管理',
    component: './ProgramManage',
    wrappers: ['@/wrappers/auth'],
  },
  {
    path: '/invitations',
    name: '站内邀请',
    component: './Invitations',
    wrappers: ['@/wrappers/auth'],
  },
  { path: '/settings/app-authorizations', name: 'App 授权', component: './AppAuthorizations', wrappers: ['@/wrappers/auth'] },
  { path: '/admin/users', name: '用户管理', component: './AdminUsers', wrappers: ['@/wrappers/auth'] },
  { path: '/admin/oauth-clients', name: 'OAuth 客户端', component: './AdminOAuthClients', wrappers: ['@/wrappers/auth'] },
  { path: '/admin/audit-events', name: '审计记录', component: './AdminAuditEvents', wrappers: ['@/wrappers/auth'] },
  {
    path: '/manage/:id',
    component: '@/layout',
    routes: [
      {
        path: '/manage/:id',
        redirect: '/manage/:id/ontology',
      },
      {
        name: '本体图谱',
        path: '/manage/:id/ontology',
        component: './OntologyAtlas',
        wrappers: ['@/wrappers/auth'],
        icon: AppstoreOutlined,
      },
      {
        name: '实体图谱',
        path: '/manage/:id/entity',
        component: './EntityAtlas',
        wrappers: ['@/wrappers/auth'],
        icon: BranchesOutlined,
      },
      // {
      //   name: 'react-flow',
      //   path: '/manage/:id/react-flow',
      //   component: './reactFlow',
      //   icon: CodeOutlined,
      // },
    ],
  },
  {
    name: '编辑本体图谱',
    path: '/manage/:id/ontology/editOntologyGraph/:graphId',
    component: './OntologyAtlas/EditOntologyGraph',
    wrappers: ['@/wrappers/auth'],
    // layout: false,
  },
  {
    name: '编辑实体图谱',
    path: '/manage/:id/entity/editEntityGraph/:graphId',
    component: './EntityAtlas/EditEntityGraph',
    wrappers: ['@/wrappers/auth'],
    // layout: false,
  },
];
