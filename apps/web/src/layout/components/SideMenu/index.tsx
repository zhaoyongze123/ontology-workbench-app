import { ArrowLeftOutlined, ProjectOutlined } from '@ant-design/icons';
import { Button, Menu, Popover, Select, Tooltip, Typography } from 'antd';
import React, { useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useRequest } from 'umi';
import { organizationPage } from '@/services_v1/organizationService';
import {
  concreteProjectRoute,
  matchNavigationRoute,
  PROJECT_NAV_ROUTES,
} from '@/config/routes';
import styles from './index.less';

/** 同一全局侧栏中的项目区；这里只承载当前项目切换与图谱入口。 */
const SideMenu: React.FC<{
  collapsed: boolean;
  onNavigate?: () => void;
}> = ({ collapsed, onNavigate }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const organizationId = params.id || location.pathname.split('/')[2] || '';
  const [projectKeyword, setProjectKeyword] = useState('');
  const [rememberedProject, setRememberedProject] = useState<{
    value: string;
    label: string;
  }>();

  const { data: projects, loading } = useRequest(
    () => organizationPage({
      pageNum: 1,
      pageSize: 100,
      keyword: projectKeyword.trim() || undefined,
    }),
    {
      formatResult: (response) => response.data ?? [],
      refreshDeps: [projectKeyword],
      debounceInterval: 250,
    },
  );

  const projectOptions = useMemo(
    () => {
      const options = (projects ?? []).map((project) => ({
        value: String(project.id ?? ''),
        label: project.name ?? '未命名项目',
      }));
      if (rememberedProject?.value === organizationId
          && !options.some((option) => option.value === rememberedProject.value)) {
        options.unshift(rememberedProject);
      }
      return options;
    },
    [organizationId, projects, rememberedProject],
  );

  const activeRoute = matchNavigationRoute(location.pathname);
  const selectedKey = activeRoute?.path.startsWith('/manage/')
    ? concreteProjectRoute(activeRoute, organizationId)
    : '';
  const menuItems = PROJECT_NAV_ROUTES.map((route) => {
    const Icon = route.icon;
    return {
      key: concreteProjectRoute(route, organizationId),
      icon: <Icon />,
      label: route.name,
    };
  });

  const selectProject = (nextProjectId: string) => {
    if (!nextProjectId) return;
    // 远程搜索会替换候选项，单独记住当前项目名称以避免选择器显示空值。
    const selectedProject = projectOptions.find((option) => option.value === nextProjectId);
    if (selectedProject) setRememberedProject(selectedProject);
    const route = activeRoute?.path.startsWith('/manage/')
      ? activeRoute
      : PROJECT_NAV_ROUTES[0];
    navigate(concreteProjectRoute(route, nextProjectId));
    onNavigate?.();
  };

  const projectSelect = (
    <Select
      aria-label="切换项目"
      value={organizationId || undefined}
      onChange={selectProject}
      showSearch
      filterOption={false}
      onSearch={setProjectKeyword}
      options={projectOptions}
      loading={loading}
      placeholder="选择项目"
      notFoundContent="暂无项目"
      style={{ width: '100%' }}
    />
  );

  return (
    <section className={styles.projectSection} aria-label="当前项目工作区">
      {!collapsed ? (
        <Typography.Text className={styles.sectionTitle} type="secondary">
          项目工作区
        </Typography.Text>
      ) : null}
      <div className={styles.projectSelector}>
        {collapsed ? (
          <div className={styles.collapsedActions}>
            <Popover placement="right" content={projectSelect} trigger="click">
              <Button aria-label="切换项目" icon={<ProjectOutlined />} />
            </Popover>
            <Tooltip title="返回项目列表" placement="right">
              <Button
                type="text"
                aria-label="返回项目列表"
                icon={<ArrowLeftOutlined aria-hidden="true" />}
                onClick={() => {
                  navigate('/home');
                  onNavigate?.();
                }}
              />
            </Tooltip>
          </div>
        ) : (
          projectSelect
        )}
      </div>
      {!collapsed ? (
        <Button
          className={styles.backToProjects}
          type="link"
          size="small"
          icon={<ArrowLeftOutlined aria-hidden="true" />}
          onClick={() => {
            navigate('/home');
            onNavigate?.();
          }}
        >
          返回项目列表
        </Button>
      ) : null}
      <Menu
        className={styles.menu}
        mode="inline"
        inlineCollapsed={collapsed}
        selectedKeys={selectedKey ? [selectedKey] : []}
        items={menuItems}
        onClick={({ key }) => {
          if (!organizationId) return;
          navigate(key);
          onNavigate?.();
        }}
        disabled={!organizationId}
      />
    </section>
  );
};

export default SideMenu;
