import { App, ConfigProvider, Layout } from 'antd';
import React, { useState } from 'react';
import { Outlet } from 'umi';
import SideMenu from './components/SideMenu';
import styles from './index.less';
// import { ReactFlowProvider } from '@xyflow/react';

const { Sider, Content } = Layout;

/** 主布局组件 */
const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);

  /** 切换菜单折叠状态 */
  const toggleCollapsed = () => {
    setCollapsed(!collapsed);
  };

  return (
      <>
        <Layout className={styles.mainLayout}>
          <Sider
            className={styles.sider}
            collapsible /* 可折叠 */
            collapsed={collapsed} /* 折叠状态 */
            trigger={null} /* 触发器 */
            width={160} /* 展开宽度 */
            collapsedWidth={92} /* 折叠宽度 */
          >
            <SideMenu
              collapsed={collapsed}
              toggleCollapsed={toggleCollapsed}
            />
          </Sider>
          <Content className={styles.content}>
            <Outlet />
          </Content>
        </Layout>
      </>
  );
};

export default MainLayout;
