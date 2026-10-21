import {
  ApiOutlined,
  FileSearchOutlined,
  HomeOutlined,
  KeyOutlined,
  MenuOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import {
  App,
  Avatar,
  Button,
  Drawer,
  Dropdown,
  Layout,
  Space,
  Typography,
} from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'umi';
import styles from './AuthFrame.less';
import { beginWorkbenchBinding, logout, type InternalUser } from './service';

const { Header, Content } = Layout;

type NavigationItem = {
  key: string;
  label: string;
  icon: React.ReactNode;
  active: boolean;
};

/** 将跨模块入口放入全局导航；项目图谱侧栏仍由项目布局单独负责。 */
const AuthFrame: React.FC<React.PropsWithChildren<{ user: InternalUser }>> = ({
  user,
  children,
}) => {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const mobileNavTriggerRef = useRef<HTMLButtonElement>(null);
  const pathname = location.pathname;

  const closeMobileNav = () => {
    setMobileNavOpen(false);
    // Drawer 卸载后把键盘焦点还给触发按钮，避免焦点落到页面 body。
    window.requestAnimationFrame(() => mobileNavTriggerRef.current?.focus());
  };

  useEffect(() => setMobileNavOpen(false), [pathname]);

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

  const navigationItems: NavigationItem[] = [
    {
      key: '/home',
      label: '项目管理',
      icon: <HomeOutlined aria-hidden="true" />,
      active: pathname === '/home' || pathname.startsWith('/manage/'),
    },
    {
      key: '/invitations',
      label: '站内邀请',
      icon: <TeamOutlined aria-hidden="true" />,
      active: pathname.startsWith('/invitations'),
    },
    {
      key: '/settings/app-authorizations',
      label: 'App 授权',
      icon: <KeyOutlined aria-hidden="true" />,
      active: pathname.startsWith('/settings/app-authorizations'),
    },
    ...(user.systemAdmin
      ? [
          {
            key: '/admin/users',
            label: '用户管理',
            icon: <UserOutlined aria-hidden="true" />,
            active: pathname.startsWith('/admin/users'),
          },
          {
            key: '/admin/oauth-clients',
            label: 'OAuth 客户端',
            icon: <ApiOutlined aria-hidden="true" />,
            active: pathname.startsWith('/admin/oauth-clients'),
          },
          {
            key: '/admin/audit-events',
            label: '安全审计',
            icon: <FileSearchOutlined aria-hidden="true" />,
            active: pathname.startsWith('/admin/audit-events'),
          },
        ]
      : []),
  ];

  const renderNavItems = (mobile = false) =>
    navigationItems.map((item) => (
      <Link
        key={item.key}
        to={item.key}
        className={`${mobile ? styles.mobileNavLink : styles.navLink} ${
          item.active ? styles.navLinkActive : ''
        }`}
        aria-current={item.active ? 'page' : undefined}
        onClick={() => setMobileNavOpen(false)}
      >
        {item.icon}
        <span>{item.label}</span>
      </Link>
    ));

  return (
    <Layout className={styles.frame}>
      <Header className={styles.header}>
        <Link
          className={styles.brand}
          to="/home"
          aria-label="Ontology 项目管理首页"
        >
          <Typography.Text strong>本体管理</Typography.Text>
        </Link>
        <nav className={styles.desktopNav} aria-label="主导航">
          {renderNavItems()}
        </nav>
        <Space className={styles.headerActions} size={8}>
          <Button
            ref={mobileNavTriggerRef}
            className={styles.mobileNavTrigger}
            type="text"
            icon={<MenuOutlined aria-hidden="true" />}
            aria-label="打开主导航"
            aria-expanded={mobileNavOpen}
            onClick={() => setMobileNavOpen(true)}
          />
          <Dropdown
            menu={{ items: accountItems, onClick: handleAccountMenuClick }}
            trigger={['click']}
          >
            <Button
              type="text"
              className={styles.accountButton}
              aria-label={`打开账号菜单：${user.displayName || user.username}`}
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
        </Space>
      </Header>
      <Content id="main-content" className={styles.content}>
        {children}
      </Content>
      <Drawer
        title="导航"
        placement="left"
        width={288}
        open={mobileNavOpen}
        onClose={closeMobileNav}
        destroyOnClose
      >
        <nav className={styles.mobileNav} aria-label="主导航">
          {renderNavItems(true)}
        </nav>
      </Drawer>
    </Layout>
  );
};

export default AuthFrame;
