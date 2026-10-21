import {
  concreteProjectRoute,
  matchNavigationRoute,
  PROJECT_NAV_ROUTES,
} from '@/config/routes';
import { organizationPage } from '@/services_v1/organizationService';
import { ArrowLeftOutlined, ProjectOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Menu,
  Popover,
  Select,
  Tooltip,
  Typography,
} from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'umi';
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
  const [projects, setProjects] = useState<
    Array<{ id?: string | number; name?: string }>
  >([]);
  const [projectTotal, setProjectTotal] = useState(0);
  const [projectPage, setProjectPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [projectLoadError, setProjectLoadError] = useState('');
  const requestSequence = useRef(0);
  const requestLoading = useRef(false);

  const loadProjects = async (
    keyword: string,
    page: number,
    append: boolean,
  ) => {
    if (requestLoading.current) return;
    const sequence = ++requestSequence.current;
    requestLoading.current = true;
    setLoading(true);
    try {
      const response = await organizationPage({
        pageNum: page,
        pageSize: 100,
        keyword: keyword.trim() || undefined,
      });
      if (sequence !== requestSequence.current) return;
      const nextProjects = (response.data || []).map((project) => ({
        id: project.id,
        name: project.name,
      }));
      setProjects((current) =>
        append
          ? [
              ...current,
              ...nextProjects.filter(
                (project) => !current.some((item) => item.id === project.id),
              ),
            ]
          : nextProjects,
      );
      setProjectTotal(response.addition?.total || 0);
      setProjectPage(page);
      setProjectLoadError('');
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      setProjectLoadError((error as Error)?.message || '项目列表加载失败');
      if (!append) {
        setProjects([]);
        setProjectTotal(0);
      }
    } finally {
      if (sequence === requestSequence.current) {
        requestLoading.current = false;
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    const timer = window.setTimeout(
      () => {
        requestLoading.current = false;
        void loadProjects(projectKeyword, 1, false);
      },
      projectKeyword ? 250 : 0,
    );
    return () => {
      window.clearTimeout(timer);
      requestSequence.current += 1;
      requestLoading.current = false;
    };
  }, [projectKeyword]);

  useEffect(() => {
    if (!organizationId || rememberedProject?.value === organizationId) return;
    let active = true;
    // 刷新或深链进入项目时只用内部 ID 找回名称，界面始终展示项目名称而非 ID。
    void organizationPage({ pageNum: 1, pageSize: 1, keyword: organizationId })
      .then((response) => {
        const current = response.data?.find(
          (project) => String(project.id) === organizationId,
        );
        if (active && current) {
          setRememberedProject({
            value: organizationId,
            label: current.name || '未命名项目',
          });
        }
      })
      .catch(() => {
        // 候选列表仍可使用；项目项保留“当前项目”标签，不向用户暴露内部 ID。
      });
    return () => {
      active = false;
    };
  }, [organizationId, rememberedProject]);

  const projectOptions = useMemo(() => {
    const options = (projects ?? []).map((project) => ({
      value: String(project.id ?? ''),
      label: project.name ?? '未命名项目',
    }));
    if (
      rememberedProject?.value === organizationId &&
      !options.some((option) => option.value === rememberedProject.value)
    ) {
      options.unshift(rememberedProject);
    }
    if (
      organizationId &&
      !options.some((option) => option.value === organizationId)
    ) {
      options.unshift({ value: organizationId, label: '当前项目' });
    }
    return options;
  }, [organizationId, projects, rememberedProject]);

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
    const selectedProject = projectOptions.find(
      (option) => option.value === nextProjectId,
    );
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
      onSearch={(keyword) => {
        requestSequence.current += 1;
        requestLoading.current = false;
        setProjectKeyword(keyword);
        setProjects([]);
        setProjectTotal(0);
        setProjectLoadError('');
      }}
      onPopupScroll={(event) => {
        const element = event.target as HTMLDivElement;
        if (
          element.scrollTop + element.clientHeight >=
            element.scrollHeight - 24 &&
          projects.length < projectTotal &&
          !loading
        ) {
          void loadProjects(projectKeyword, projectPage + 1, true);
        }
      }}
      options={projectOptions}
      loading={loading}
      placeholder="选择项目"
      notFoundContent={
        projectLoadError ? (
          <Button
            type="link"
            onClick={() => void loadProjects(projectKeyword, 1, false)}
          >
            项目列表加载失败，点击重试
          </Button>
        ) : loading ? (
          '正在加载项目…'
        ) : projectKeyword.trim() ? (
          '未找到匹配项目'
        ) : (
          '暂无可访问项目'
        )
      }
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
        projectLoadError ? (
          <Alert
            type="error"
            showIcon
            message="项目列表加载失败"
            action={
              <Button
                disabled={loading}
                onClick={() => void loadProjects(projectKeyword, 1, false)}
              >
                重试
              </Button>
            }
            style={{ margin: '4px 8px 8px' }}
          />
        ) : null
      ) : projectLoadError ? (
        <Tooltip title="项目列表加载失败，点击重试" placement="right">
          <Button
            type="text"
            danger
            size="small"
            aria-label="重试加载项目列表"
            onClick={() => void loadProjects(projectKeyword, 1, false)}
          >
            !
          </Button>
        </Tooltip>
      ) : null}
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
