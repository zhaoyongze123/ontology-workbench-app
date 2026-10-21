import {
  ApiOutlined,
  AppstoreOutlined,
  BranchesOutlined,
  FileSearchOutlined,
  HomeOutlined,
  KeyOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ComponentType } from 'react';

export type NavigationRoute = {
  path: string;
  name: string;
  icon: ComponentType;
};

/** 路由元数据是菜单高亮、项目切换和页面面包屑的共同事实源。 */
export const WORKSPACE_NAV_ROUTES: NavigationRoute[] = [
  { path: '/home', name: '项目管理', icon: HomeOutlined },
  { path: '/invitations', name: '站内邀请', icon: TeamOutlined },
  {
    path: '/settings/app-authorizations',
    name: 'App 授权',
    icon: KeyOutlined,
  },
];

export const ADMIN_NAV_ROUTES: NavigationRoute[] = [
  { path: '/admin/users', name: '用户管理', icon: UserOutlined },
  {
    path: '/admin/oauth-clients',
    name: 'OAuth 客户端',
    icon: ApiOutlined,
  },
  {
    path: '/admin/audit-events',
    name: '安全审计',
    icon: FileSearchOutlined,
  },
];

export const PROJECT_NAV_ROUTES: NavigationRoute[] = [
  {
    path: '/manage/:id/ontology',
    name: '本体图谱',
    icon: AppstoreOutlined,
  },
  {
    path: '/manage/:id/entity',
    name: '实体图谱',
    icon: BranchesOutlined,
  },
];

export const PROJECT_EDIT_ROUTES = [
  {
    path: '/manage/:id/ontology/editOntologyGraph/:graphId',
    name: '编辑本体图谱',
    component: './OntologyAtlas/EditOntologyGraph',
    parentPath: '/manage/:id/ontology',
  },
  {
    path: '/manage/:id/entity/editEntityGraph/:graphId',
    name: '编辑实体图谱',
    component: './EntityAtlas/EditEntityGraph',
    parentPath: '/manage/:id/entity',
  },
];

export type RouteBreadcrumb = { label: string; href?: string };

export function matchNavigationRoute(pathname: string): NavigationRoute | undefined {
  const globalRoutes = [...WORKSPACE_NAV_ROUTES, ...ADMIN_NAV_ROUTES];
  const globalMatch = globalRoutes.find(
    (route) => pathname === route.path || pathname.startsWith(route.path + '/'),
  );
  if (globalMatch) return globalMatch;

  const organizationId = pathname.split('/')[2];
  if (!organizationId) return undefined;
  return PROJECT_NAV_ROUTES.find((route) => {
    const concretePath = route.path.replace(':id', organizationId);
    return pathname === concretePath || pathname.startsWith(concretePath + '/');
  });
}

export function concreteProjectRoute(route: NavigationRoute, projectId: string) {
  return route.path.replace(':id', projectId);
}

export function getRouteBreadcrumbs(pathname: string): RouteBreadcrumb[] {
  const editRoute = PROJECT_EDIT_ROUTES.find((route) =>
    new RegExp(`^${route.path.replace(/:[^/]+/g, '[^/]+')}$`).test(pathname),
  );
  if (editRoute) {
    const organizationId = pathname.split('/')[2];
    const parent = PROJECT_NAV_ROUTES.find((route) => route.path === editRoute.parentPath);
    return [
      { label: '工作空间', href: '/home' },
      { label: '项目管理', href: '/home' },
      ...(parent
        ? [{ label: parent.name, href: concreteProjectRoute(parent, organizationId) }]
        : []),
      { label: editRoute.name },
    ];
  }
  const route = matchNavigationRoute(pathname);
  if (!route) return [];
  if (route.path.startsWith('/admin/')) {
    return [
      { label: '系统管理', href: '/admin/users' },
      { label: route.name },
    ];
  }
  if (route.path.startsWith('/manage/')) {
    return [
      { label: '工作空间', href: '/home' },
      { label: '项目管理', href: '/home' },
      { label: route.name },
    ];
  }
  return [{ label: '工作空间', href: '/home' }, { label: route.name }];
}

const authenticatedWrapper = ['@/wrappers/auth'];

export default [
  {
    path: '/login',
    name: '登录',
    component: './Login',
    layout: false,
  },
  {
    path: '/account/activate',
    name: '设置账号密码',
    component: './AccountAction',
    layout: false,
  },
  {
    path: '/account/reset-password',
    name: '重置账号密码',
    component: './AccountAction',
    layout: false,
  },
  { path: '/', redirect: '/home' },
  ...WORKSPACE_NAV_ROUTES.map((route) => ({
    path: route.path,
    name: route.name,
    component:
      route.path === '/home'
        ? './ProgramManage'
        : route.path === '/invitations'
        ? './Invitations'
        : './AppAuthorizations',
    wrappers: authenticatedWrapper,
  })),
  ...ADMIN_NAV_ROUTES.map((route) => ({
    path: route.path,
    name: route.name,
    component:
      route.path === '/admin/users'
        ? './AdminUsers'
        : route.path === '/admin/oauth-clients'
        ? './AdminOAuthClients'
        : './AdminAuditEvents',
    wrappers: authenticatedWrapper,
  })),
  {
    path: '/manage/:id',
    component: '@/layout',
    routes: [
      { path: '/manage/:id', redirect: '/manage/:id/ontology' },
      ...PROJECT_NAV_ROUTES.map((route) => ({
        path: route.path,
        name: route.name,
        component: route.name === '本体图谱' ? './OntologyAtlas' : './EntityAtlas',
        wrappers: authenticatedWrapper,
        icon: route.icon,
      })),
    ],
  },
  ...PROJECT_EDIT_ROUTES.map((route) => ({
    name: route.name,
    path: route.path,
    component: route.component,
    wrappers: authenticatedWrapper,
  })),
];
