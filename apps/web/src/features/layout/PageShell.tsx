import { Breadcrumb, Space, Typography } from 'antd';
import React from 'react';
import { Link, useLocation } from 'umi';
import { getRouteBreadcrumbs } from '@/config/routes';
import styles from './PageShell.less';

type PageShellProps = React.PropsWithChildren<{
  title: string;
  actions?: React.ReactNode;
  titleIcon?: React.ReactNode;
}>;

/** 所有列表页共享标题、当前位置和主要操作的几何；不渲染空说明占位。 */
const PageShell: React.FC<PageShellProps> = ({
  title,
  actions,
  titleIcon,
  children,
}) => {
  const { pathname } = useLocation();
  const breadcrumbs = getRouteBreadcrumbs(pathname);

  return (
    <main className={styles.page} aria-labelledby="page-title" tabIndex={-1}>
      <Breadcrumb
        className={styles.breadcrumb}
        items={breadcrumbs.map((item) => ({
          key: item.href || item.label,
          title: item.href ? <Link to={item.href}>{item.label}</Link> : item.label,
        }))}
      />
      <header className={styles.header}>
        <Typography.Title id="page-title" level={1} className={styles.title}>
          <Space size={8}>
            {titleIcon}
            {title}
          </Space>
        </Typography.Title>
        {actions ? (
          <Space className={styles.actions} wrap>
            {actions}
          </Space>
        ) : null}
      </header>
      {children}
    </main>
  );
};

export default PageShell;
