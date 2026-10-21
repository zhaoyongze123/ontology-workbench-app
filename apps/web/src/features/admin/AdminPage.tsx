import React from 'react';
import PageShell from '@/features/layout/PageShell';

type AdminPageProps = React.PropsWithChildren<{
  title: string;
  actions?: React.ReactNode;
}>;

/** 管理页复用统一页面骨架；路由配置提供准确的当前位置。 */
const AdminPage: React.FC<AdminPageProps> = ({ title, actions, children }) => (
  <PageShell title={title} actions={actions}>
    {children}
  </PageShell>
);

export default AdminPage;
