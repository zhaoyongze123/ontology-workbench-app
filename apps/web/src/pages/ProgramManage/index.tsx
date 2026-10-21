import {
  deleteOrganization,
  editOrganization,
  organizationPage,
  saveOrganization,
} from '@/services_v1/organizationService';
import ProjectRowActions from '@/features/projects/ProjectRowActions';
import {
  ApiResultListOrganizationVO,
  OrganizationVO,
} from '@/services_v1/typings';
import {
  PlusOutlined,
  ProjectOutlined,
} from '@ant-design/icons';
import { history, Link, useRequest } from '@umijs/max';
import { App, Button, Row, Space, Table, Typography } from 'antd';
import { useState } from 'react';
import styles from './index.less';
import ProgramPop from './ProgramPop';
import { normalizeApiError } from '@/features/errors/apiError';

function ProgramManage() {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentProgram, setCurrentProgram] = useState<OrganizationVO | null>(
    null,
  );
  const [current, setPageCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const { message, modal } = App.useApp();

  /** 获取项目列表 */
  const {
    data: programList,
    refresh: refreshProgramList,
    run: tableRun,
    loading: tableLoading,
  } = useRequest<ApiResultListOrganizationVO>(organizationPage, {
    formatResult: (res) => {
      return { ...res };
    },
    defaultParams: [
      {
        pageNum: current,
        pageSize: pageSize,
      },
    ],
  });
  /** 项目总数 */
  const total = programList?.addition?.total ? programList?.addition?.total : 0;

  /** 表格变化处理函数 */
  const handlePageChange = (pagination: any) => {
    const { current: newCurrent, pageSize: newPageSize } = pagination;
    setPageCurrent(newCurrent);
    setPageSize(newPageSize);
    tableRun({
      pageNum: newCurrent,
      pageSize: newPageSize,
    });
  };

  /**
   * 删除项目
   * @param record
   */
  function handleDelete(record: any) {
    modal.confirm({
      title: '确认删除',
      content: `确定要删除项目"${record.name}"吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk() {
        return new Promise((resolve) => {
          deleteOrganization(
            {
              organizationId: record.id as string,
            },
            { skipErrorHandler: true },
          )
            .then(() => {
              message.success('删除成功');
              resolve(null);
              refreshProgramList();
            })
            .catch((error) => {
              // 删除请求由确认弹窗负责提示，避免全局重复提示或失败时误报成功。
              message.error(normalizeApiError(error).message);
              resolve(null);
            });
        });
      },
    });
  }

  /** 打开编辑项目弹窗 */
  const handleEdit = (record: OrganizationVO) => {
    setCurrentProgram(record);
    setIsEditMode(true);
    setIsModalVisible(true);
  };

  /** 打开新建项目弹窗 */
  const handleCreate = () => {
    setCurrentProgram(null);
    setIsEditMode(false);
    setIsModalVisible(true);
  };

  /** 关闭弹窗 */
  const handleCancel = () => {
    setIsModalVisible(false);
    setCurrentProgram(null);
  };

  /** 提交表单 */
  const handleSubmit = (values: {
    name: string;
    description: string;
    id?: string;
  }) => {
    if (isEditMode && values.id) {
      /** 编辑项目 */
      editOrganization({
        id: values.id,
        name: values.name,
        description: values.description,
      })
        .then(() => {
          message.success('项目编辑成功');
          setIsModalVisible(false);
          refreshProgramList();
        })
        .catch((error) => {
          message.error(normalizeApiError(error).message || '编辑失败');
        });
    } else {
      /** 新建项目 */
      saveOrganization({
        name: values.name,
        description: values.description,
      })
        .then((response) => {
          message.success('项目创建成功');
          setIsModalVisible(false);
          /** 获取创建成功后返回的项目ID */
          const organizationId = response.data?.id;
          if (organizationId) {
            /** 直接跳转到对应的图谱页面 */
            history.push(`/manage/${organizationId}`);
          } else {
            /** 如果没有获取到ID，则刷新列表 */
            setPageCurrent(1);
            tableRun({
              pageNum: 1,
              pageSize: pageSize,
            });
          }
        })
        .catch((error) => {
          message.error(normalizeApiError(error).message || '项目创建失败');
        });
    }
  };

  /** 定义表格列 */
  const columns = [
    {
      title: '项目名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (_: string, record: OrganizationVO) => {
        return (
          <Link
            to={`/manage/${record.id}`}
            onClick={(e) => {
              e.preventDefault();
              history.push(`/manage/${record.id}`);
            }}
          >
            {_}
          </Link>
        );
      },
    },
    {
      title: '项目描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      ellipsis: true,
      render: (_: string) => {
        return _ ? new Date(_).toLocaleString() : '';
      },
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      ellipsis: true,
      render: (_: string) => {
        return _ ? new Date(_).toLocaleString() : '';
      },
    },
    {
      title: '本体图谱数量',
      key: 'ontologyGraphTotal',
      ellipsis: true,
      render: (_: any, record: OrganizationVO) => {
        return (
          <span className={`${styles.countBadge} ${styles.blueBadge}`}>
            {record.ontologyGraphTotal || 0}
          </span>
        );
      },
    },
    {
      title: '实体图谱数量',
      key: 'entityGraphTotal',
      ellipsis: true,
      render: (_: any, record: OrganizationVO) => {
        return (
          <span className={`${styles.countBadge} ${styles.greenBadge}`}>
            {record.entityGraphTotal || 0}
          </span>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 360,
      render: (_: any, record: OrganizationVO) => (
        <ProjectRowActions projectId={record.id as string}
          onEdit={() => handleEdit(record)} onDelete={() => handleDelete(record)}
          onLeave={refreshProgramList} />
      ),
    },
  ];

  return (
    <Row className={styles.programManage}>
      <Row className={styles.header}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          <Space>
            <ProjectOutlined />
            项目管理
          </Space>
        </Typography.Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreate}
          loading={tableLoading}
        >
          新建项目
        </Button>
      </Row>

      <Table
        size="small"
        columns={columns}
        loading={tableLoading}
        dataSource={programList?.data?.map((item: OrganizationVO) => ({
          ...item,
        }))}
        rowKey={(record: OrganizationVO) => record.id as string}
        pagination={{
          current: current,
          pageSize: pageSize,
          showSizeChanger: true,
          total: total,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={handlePageChange}
        sticky={{ offsetHeader: 0 }}
        scroll={{ y: 'calc(100vh - 292px)' }}
      />

      {/* 使用共用的项目弹窗组件 */}
      <ProgramPop
        open={isModalVisible}
        onCancel={handleCancel}
        onSubmit={handleSubmit}
        initialValues={currentProgram || undefined}
        isEdit={isEditMode}
      />
    </Row>
  );
}

export default ProgramManage;
