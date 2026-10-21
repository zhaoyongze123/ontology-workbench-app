import {
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import {
  App,
  Avatar,
  Button,
  Drawer,
  Dropdown,
  Layout,
  Menu,
  Space,
  Typography,
} from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'umi';
import SideMenu from '@/layout/components/SideMenu';
import {
  ADMIN_NAV_ROUTES,
  matchNavigationRoute,
  WORKSPACE_NAV_ROUTES,
} from '@/config/routes';
import styles from './AuthFrame.less';
import { beginWorkbenchBinding, logout, type InternalUser } from './service';

const { Header, Sider, Content } = Layout;

const AuthFrame: React.FC<React.PropsWithChildren<{ user: InternalUser }>> = ({
  user,
  children,
}) => {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const mobileNavTriggerRef = useRef<HTMLButtonElement>(null);
  const pathname = location.pathname;
  const organizationId = pathname.split('/')[2] || '';
  const inProjectWorkspace = pathname.startsWith('/manage/');
  const activeRoute = matchNavigationRoute(pathname);
  const selectedKey = activeRoute?.path.startsWith('/manage/')
    ? activeRoute.path.replace(':id', organizationId)
    : activeRoute?.path;

  const closeMobileNav = () => {
    setMobileNavOpen(false);
    // 抽屉关闭后将键盘焦点还给打开按钮，保持可预测的键盘顺序。
    window.requestAnimationFrame(() => mobileNavTriggerRef.current?.focus());
  };

  useEffect(() => {
    if (mobileNavOpen) closeMobileNav();
  }, [pathname]);

  const performLogout = async (allDevices: boolean) => {
    try {
      await logout(allDevices);
      navigate('/login', { replace: true });
    } catch (error) {
      message.error((error as Error)?.message || '退出失败，请重试');
    }
  };

  const accountItems: MenuProps['items'] = [
    { key: 'bind', label: '绑定 Workbench' },
    { type: 'divider' },
    { key: 'logout', label: '退出登录' },
    { key: 'logout-all', label: '退出全部设备' },
  ];

  const handleAccountMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key === 'bind') {
      try {
        const result = await beginWorkbenchBinding();
        if (
          result.authorizationUrl !== '/api/v1/oauth2/authorization/workbench'
        ) {
          throw new Error('服务返回的授权地址无效');
        }
        window.location.assign(result.authorizationUrl);
      } catch (error) {
        message.error(
          (error as Error)?.message ||
            '暂时无法绑定 Workbench，请重新登录后再试',
        );
      }
      return;
    }

    if (key === 'logout' || key === 'logout-all') {
      modal.confirm({
        title: key === 'logout-all' ? '退出全部设备' : '退出登录',
        content:
          key === 'logout-all'
            ? '其他已登录设备也会被退出。'
            : '确定退出当前账号吗？',
        okText: key === 'logout-all' ? '退出全部设备' : '退出登录',
        cancelText: '取消',
        onOk: () => performLogout(key === 'logout-all'),
      });
    }
  };

  const workspaceItems = useMemo(
    () =>
      WORKSPACE_NAV_ROUTES.map((route) => {
        const Icon = route.icon;
        return { key: route.path, icon: <Icon />, label: route.name };
      }),
    [],
  );
  const adminItems = useMemo(
    () =>
      ADMIN_NAV_ROUTES.map((route) => {
        const Icon = route.icon;
        return { key: route.path, icon: <Icon />, label: route.name };
      }),
    [],
  );

  const navigation = (drawer = false) => (
    <div className={styles.navScroll}>
      <section className={styles.navGroup} aria-label="工作空间">
        {!collapsed || drawer ? (
          <Typography.Text className={styles.groupTitle} type="secondary">
            工作空间
          </Typography.Text>
        ) : null}
        <Menu
          mode="inline"
          inlineCollapsed={!drawer && collapsed}
          selectedKeys={selectedKey ? [selectedKey] : []}
          items={workspaceItems}
          onClick={({ key }) => {
            navigate(key);
            if (drawer) closeMobileNav();
          }}
        />
      </section>

      {inProjectWorkspace ? (
        <SideMenu
          collapsed={!drawer && collapsed}
          onNavigate={drawer ? closeMobileNav : undefined}
        />
      ) : null}

      {user.systemAdmin ? (
        <section className={styles.navGroup} aria-label="系统管理">
          {!collapsed || drawer ? (
            <Typography.Text className={styles.groupTitle} type="secondary">
              系统管理
            </Typography.Text>
          ) : null}
          <Menu
            mode="inline"
            inlineCollapsed={!drawer && collapsed}
            selectedKeys={selectedKey ? [selectedKey] : []}
            items={adminItems}
            onClick={({ key }) => {
              navigate(key);
              if (drawer) closeMobileNav();
            }}
          />
        </section>
      ) : null}
    </div>
  );

  return (
    <Layout className={styles.frame}>
      <Header className={styles.header}>
        <Button
          ref={mobileNavTriggerRef}
          className={styles.mobileNavTrigger}
          type="text"
          icon={<MenuOutlined aria-hidden="true" />}
          aria-label="打开主导航"
          aria-expanded={mobileNavOpen}
          onClick={() => setMobileNavOpen(true)}
        />
        <Link className={styles.brand} to="/home">
          <Typography.Text strong>Ontology</Typography.Text>
        </Link>
        <Dropdown
          menu={{ items: accountItems, onClick: handleAccountMenuClick }}
          trigger={['click']}
        >
          <Button
            type="text"
            className={styles.accountButton}
            aria-label={'打开账号菜单：' + (user.displayName || user.username)}
          >
            <Space size={8}>
              <Avatar size="small">
                {(user.displayName || user.username).slice(0, 1)}
              </Avatar>
              <span className={styles.accountName}>
                {user.displayName || user.username}
              </span>
            </Space>
          </Button>
        </Dropdown>
      </Header>
      <Layout className={styles.body}>
        <Sider
          className={styles.sider}
          width={232}
          collapsedWidth={72}
          collapsed={collapsed}
          trigger={null}
        >
          {navigation()}
          <div className={styles.collapseBar}>
            <Button
              type="text"
              aria-label={collapsed ? '展开导航侧栏' : '折叠导航侧栏'}
              onClick={() => setCollapsed((value) => !value)}
              icon={
                collapsed ? (
                  <MenuUnfoldOutlined aria-hidden="true" />
                ) : (
                  <MenuFoldOutlined aria-hidden="true" />
                )
              }
            />
          </div>
        </Sider>
        <Content id="main-content" className={styles.content}>
          {children}
        </Content>
      </Layout>
      <Drawer
        title="Ontology 导航"
        placement="left"
        width={288}
        open={mobileNavOpen}
        onClose={closeMobileNav}
        destroyOnClose
      >
        <nav aria-label="主导航">{navigation(true)}</nav>
      </Drawer>
    </Layout>
  );
};

export default AuthFrame;
