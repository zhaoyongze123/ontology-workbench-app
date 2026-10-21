import { MenuOutlined } from '@ant-design/icons';
import { Button, Drawer, Layout } from 'antd';
import React, { useRef, useState } from 'react';
import { Outlet } from 'umi';
import SideMenu from './components/SideMenu';
import styles from './index.less';

const { Sider, Content } = Layout;

/** 主布局组件 */
const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const mobileNavTriggerRef = useRef<HTMLButtonElement>(null);

  /** 切换菜单折叠状态 */
  const toggleCollapsed = () => {
    setCollapsed(!collapsed);
  };

  const closeMobileNav = () => {
    setMobileNavOpen(false);
    // 键盘关闭项目导航抽屉后，焦点回到打开抽屉的按钮。
    window.requestAnimationFrame(() => mobileNavTriggerRef.current?.focus());
  };

  return (
    <Layout className={styles.mainLayout}>
      <Sider
        className={styles.sider}
        collapsible
        collapsed={collapsed}
        trigger={null}
        width={160}
        collapsedWidth={92}
      >
        <SideMenu collapsed={collapsed} toggleCollapsed={toggleCollapsed} />
      </Sider>
      <Content className={styles.content}>
        <div className={styles.mobileNavigation}>
          <Button
            ref={mobileNavTriggerRef}
            icon={<MenuOutlined aria-hidden="true" />}
            onClick={() => setMobileNavOpen(true)}
          >
            项目与图谱导航
          </Button>
        </div>
        <Outlet />
      </Content>
      <Drawer
        title="项目与图谱导航"
        placement="left"
        width={288}
        open={mobileNavOpen}
        onClose={closeMobileNav}
        destroyOnClose
      >
        {mobileNavOpen ? (
          <SideMenu
            collapsed={false}
            toggleCollapsed={() => setMobileNavOpen(false)}
          />
        ) : null}
      </Drawer>
    </Layout>
  );
};

export default MainLayout;
