import React from 'react';
import { Outlet } from 'umi';

/** 图谱路由不再创建第二套常驻侧栏，由已验证的应用壳统一承载导航。 */
const MainLayout: React.FC = () => <Outlet />;

export default MainLayout;
