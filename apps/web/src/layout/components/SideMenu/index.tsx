import routes from '@/config/routes';
import { organizationPage } from '@/services_v1/organizationService';
import {
  EllipsisOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { Button, Menu, message, Popover, Row, Select, Spin } from 'antd';
import React, { useMemo } from 'react';
import { useLocation, useNavigate, useParams, useRequest } from 'umi';
import styles from './index.less';

/** 侧边栏菜单组件
 * @param collapsed 是否折叠
 * @param toggleCollapsed 切换折叠状态回调
 */
const SideMenu: React.FC<{
  collapsed: boolean;
  toggleCollapsed: () => void;
}> = ({ collapsed, toggleCollapsed }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const organizationId = params.id || '';

  /** 使用useRequest获取项目列表 */
  const { data: projectListData, loading: projectListLoading } = useRequest(
    () =>
      organizationPage({
        pageNum: 1,
        pageSize: 100,
      }),
    {
      formatResult: (response) => response.data ?? [],
    },
  );

  /** 项目列表数据 */
  const projectList = projectListData ?? [];

  /** 获取主路由配置（提取公共逻辑，避免重复查找） */
  const mainRoute = useMemo(
    () => routes.find((route) => route.path === '/manage/:id'),
    [],
  );

  /** 从routes.ts获取菜单项配置 */
  const menuItems = useMemo(() => {
    if (!mainRoute?.routes) return [];
    return mainRoute.routes
      .filter((route) => route.name && route.path && !route.redirect)
      .map((route) => {
        const IconComponent = route.icon;
        const path = organizationId
          ? route.path?.replace(':id', organizationId)
          : route.path;
        return {
          key: path || '',
          icon: IconComponent ? <IconComponent /> : null,
          label: route.name,
        };
      });
  }, [mainRoute, organizationId]);

  /** 处理菜单点击 */
  const handleMenuClick = (info: { key: string }) => {
    if (!organizationId) {
      message.warning('请先选择一个项目');
      return;
    }
    navigate(info.key);
  };

  /** 处理项目选择变更 */
  const handleProjectChange = (value: string) => {
    if (!value) return;
    /** 智能导航：保持当前页面类型，只替换项目ID */
    const pathParts = location.pathname.split('/');
    /** 如果在 /manage/:id 路径下，尝试保持当前子路由 */
    if (pathParts[1] === 'manage' && pathParts.length >= 3) {
      const currentSubPath = pathParts[3]; // 例如 'ontology' 或 'entity'
      if (currentSubPath && mainRoute?.routes) {
        /** 查找匹配的子路由 */
        const matchedRoute = mainRoute.routes.find((route) => {
          const routeParts = (route.path as string)?.split('/');
          return routeParts?.[2] === currentSubPath;
        });
        if (matchedRoute?.path) {
          navigate(matchedRoute.path.replace(':id', value));
          return;
        }
      }
    }
    /** 默认导航到项目首页 */
    navigate(`/manage/${value}`);
  };

  /** 根据当前路径确定应该选中的菜单项 */
  const selectedKeys = useMemo(() => {
    const { pathname } = location;
    if (!mainRoute?.routes) return [];
    const allRoutes = mainRoute.routes;
    /** 尝试精确匹配 */
    const exactMatch = allRoutes.find((route) => {
      if (!route.path) return false;
      /** 替换路径中的:id为实际的organizationId */
      const routePath = organizationId
        ? route.path.replace(':id', organizationId)
        : route.path;
      return pathname === routePath;
    });
    if (exactMatch) {
      const matchPath = organizationId
        ? exactMatch.path?.replace(':id', organizationId)
        : exactMatch.path;
      return [matchPath || ''];
    }

    /** 查找可能的父级路径  获取所有可见的菜单项路径 */
    const visibleMenuPaths = allRoutes
      .filter((route) => route.path && route.path !== '/manage/:id')
      .map((route) =>
        organizationId
          ? (route.path as string).replace(':id', organizationId)
          : (route.path as string),
      );
    /** 按路径长度降序排序，确保先匹配最具体的父路径 */
    visibleMenuPaths.sort((a, b) => b.length - a.length);
    /** 查找最匹配的父路径 */
    for (const menuPath of visibleMenuPaths) {
      /** 确保是路径前缀匹配，而不是部分字符串匹配 */
      if (pathname.startsWith(`${menuPath}/`)) {
        return [menuPath];
      }
    }
    return [];
  }, [location.pathname, organizationId, mainRoute]);

  /** 项目选择器内容 */
  const ProjectSelectorContent = projectListLoading ? (
    <Row style={{ textAlign: 'center', padding: '5px 0' }}>
      <Spin size="small" />
    </Row>
  ) : (
    <Select
      value={organizationId || undefined}
      onChange={handleProjectChange}
      style={{ width: collapsed ? 120 : '100%' }}
      options={projectList.map((project) => ({
        value: project.id ?? '',
        label: project.name ?? '未命名项目',
      }))}
      placeholder="选择项目"
      notFoundContent="暂无项目"
    />
  );

  return (
    <Row className={styles.sideMenuContainer}>
      <Row className={styles.projectSelector}>
        {collapsed ? (
          <Popover placement="right" content={ProjectSelectorContent}>
            <Button icon={<EllipsisOutlined />} />
          </Popover>
        ) : (
          ProjectSelectorContent
        )}
      </Row>
      <Menu
        className={styles.menu}
        mode="inline"
        selectedKeys={selectedKeys}
        items={menuItems}
        onClick={handleMenuClick}
        disabled={!organizationId}
      />
      <Row className={styles.sidebarTrigger}>
        <Button type="text" onClick={toggleCollapsed}>
          {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
        </Button>
      </Row>
    </Row>
  );
};

export default SideMenu;
