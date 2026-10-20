import { beginWorkbenchBinding, logout, type InternalUser } from './service';
import { App, Avatar, Button, Dropdown, Layout, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import React from 'react';
import { useNavigate } from 'umi';

const { Header, Content } = Layout;

/** 为受保护页面提供统一会话菜单；具体业务页仍由路由和各 feature 负责。 */
const AuthFrame: React.FC<React.PropsWithChildren<{ user: InternalUser }>> = ({ user, children }) => {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();

  const performLogout = async (allDevices: boolean) => {
    try {
      await logout(allDevices);
      navigate('/login', { replace: true });
    } catch (error) {
      message.error((error as Error)?.message || '退出失败，请重试');
    }
  };

  const items: MenuProps['items'] = [
    { key: 'invitations', label: '站内邀请' },
    { key: 'bind', label: '绑定 Workbench' },
    { type: 'divider' },
    { key: 'logout', label: '退出登录' },
    { key: 'logout-all', label: '退出全部设备' },
  ];

  const handleMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key === 'invitations') {
      navigate('/invitations');
    } else if (key === 'bind') {
      try {
        const result = await beginWorkbenchBinding();
        if (result.authorizationUrl !== '/api/v1/oauth2/authorization/workbench') {
          throw new Error('服务返回的授权地址无效');
        }
        window.location.assign(result.authorizationUrl);
      } catch (error) {
        message.error((error as Error)?.message || '暂时无法绑定 Workbench，请重新登录后再试');
      }
    } else if (key === 'logout' || key === 'logout-all') {
      modal.confirm({
        title: key === 'logout-all' ? '退出全部设备' : '退出登录',
        content: key === 'logout-all' ? '其他已登录设备也会被退出。' : '确定退出当前账号吗？',
        okText: '确认',
        cancelText: '取消',
        onOk: () => performLogout(key === 'logout-all'),
      });
    }
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f6f8' }}>
      <Header style={{ height: 56, lineHeight: '56px', paddingInline: 24, display: 'flex',
        alignItems: 'center', justifyContent: 'space-between', background: '#fff', borderBottom: '1px solid #edf0f3' }}>
        <Typography.Text strong style={{ color: '#20242b' }}>本体管理</Typography.Text>
        <Dropdown menu={{ items, onClick: handleMenuClick }} trigger={['click']}>
          <Button type="text" aria-label="打开用户菜单">
            <Space><Avatar size="small">{(user.displayName || user.username).slice(0, 1)}</Avatar>
              <span>{user.displayName || user.username}</span></Space>
          </Button>
        </Dropdown>
      </Header>
      <Content style={{ minWidth: 0, minHeight: 0 }}>{children}</Content>
    </Layout>
  );
};

export default AuthFrame;
