import { Breadcrumb, Space, Typography } from 'antd';
import React from 'react';
import { Link } from 'umi';
import styles from './AdminPage.less';

type AdminPageProps = React.PropsWithChildren<{
  title: string;
  description: string;
  actions?: React.ReactNode;
}>;

/** 管理页共享骨架；路由负责当前位置，业务页只负责自己的内容和操作。 */
const AdminPage: React.FC<AdminPageProps> = ({
  title,
  description,
  actions,
  children,
}) => (
  <main
    className={styles.page}
    aria-labelledby="admin-page-title"
    tabIndex={-1}
  >
    <Breadcrumb
      className={styles.breadcrumb}
      items={[
        { title: <Link to="/home">项目管理</Link> },
        { title: '系统管理' },
        { title },
      ]}
    />
    <header className={styles.header}>
      <div className={styles.heading}>
        <Typography.Title
          id="admin-page-title"
          level={1}
          className={styles.title}
        >
          {title}
        </Typography.Title>
        <Typography.Paragraph type="secondary" className={styles.description}>
          {description}
        </Typography.Paragraph>
      </div>
      {actions ? (
        <Space className={styles.actions} wrap>
          {actions}
        </Space>
      ) : null}
    </header>
    {children}
  </main>
);

export default AdminPage;
