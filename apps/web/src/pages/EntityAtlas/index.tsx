import ImportGraphPop from '@/components/ImportGraphPop';
import { useProjectAccess } from '@/features/projects/useProjectAccess';
import {
  deleteGraph,
  exportGraph,
  pageGraph,
} from '@/services_v1/graphService';
import { ApiResultListGraphVO, GraphVO } from '@/services_v1/typings';
import { GraphType } from '@/utils/typeEnum';
import { downloadJson } from '@/utils/util';
import { ExportOutlined, PlusOutlined } from '@ant-design/icons';
import { history, Link, useParams, useRequest } from '@umijs/max';
import { Alert, App, Button, Row, Space, Spin, Table } from 'antd';
import { useState } from 'react';
import EntityGraphAdd from './EntityGraphAdd';
import PageShell from '@/features/layout/PageShell';
import ListPagination from '@/features/lists/ListPagination';

/** 实体图谱管理 */
function GraphExample() {
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [isImportModalVisible, setIsImportModalVisible] = useState(false);
  const [pageSize, setPageSize] = useState(20);
  const [current, setCurrent] = useState(1);
  const { message, modal } = App.useApp();
  const params = useParams<{ id: string }>();
  const organizationId = params.id || '';
  const { can } = useProjectAccess(organizationId);
  const canEdit = can('EDIT');
  const canDeleteGraph = can('DELETE_GRAPH');
  /** 获取实体图谱列表 */
  const {
    data: entityList,
    run: tableRun,
    loading: tableLoading,
    refresh: refreshEntityList,
    error: tableError,
  } = useRequest<ApiResultListGraphVO>(pageGraph, {
    formatResult: (res) => {
      return { ...res };
    },
    defaultParams: [
      {
        graphType: 'ENTITY',
        pageNum: current,
        pageSize: pageSize,
        organizationId: organizationId,
      },
    ],
  });

  /** 实体图谱总数 */
  const total = entityList?.addition?.total ? entityList?.addition?.total : 0;

  /** 表格变化处理函数 */
  const handleTableChange = (newCurrent: number, newPageSize: number) => {
    const nextPage = newPageSize === pageSize ? newCurrent : 1;
    setCurrent(nextPage);
    setPageSize(newPageSize);
    tableRun({
      graphType: 'ENTITY',
      pageNum: nextPage,
      pageSize: newPageSize,
      organizationId: organizationId,
    });
  };

  /** 处理删除后的分页逻辑 */
  const handleAfterDelete = () => {
    const currentPageDataLength = entityList?.data?.length || 0;
    const shouldGoToPrevPage = currentPageDataLength === 1 && current > 1;
    if (shouldGoToPrevPage) {
      const prevPage = current - 1;
      setCurrent(prevPage);
      tableRun({
        graphType: 'ENTITY',
        pageNum: prevPage,
        pageSize: pageSize,
        organizationId: organizationId,
      });
    } else {
      refreshEntityList();
    }
  };

  /** 删除实体图谱 */
  const handleDelEntity = (record: GraphVO) => {
    if (!record.id) {
      message.warning('图谱ID不存在，无法删除');
      return;
    }
    const graphId = record.id;
    const graphName = record.name || '该图谱';
    modal.confirm({
      title: '删除实体图谱',
      content: `确定要删除"${graphName}"吗？`,
      okText: '删除',
      cancelText: '取消',
      onOk: async () => {
        const response = await deleteGraph({
          graphId,
          graphType: 'ENTITY',
          organizationId: organizationId,
        });
        if (response?.success) {
          message.success('实体图谱删除成功');
          handleAfterDelete();
        }
      },
    });
  };

  /** 导出实体图谱 */
  const handleExport = async (record: GraphVO) => {
    if (!record.id) {
      message.warning('图谱ID不存在，无法导出');
      return;
    }
    const graphName = record.name || '实体图谱';
    const loadingKey = 'exportLoading';
    message.loading({
      content: '正在导出实体图谱...',
      key: loadingKey,
      duration: 0,
    });
    try {
      const response = await exportGraph({
        graphId: record.id,
        graphType: 'ENTITY',
      });
      if (response?.success && response.data) {
        await downloadJson(response.data, `${graphName}.json`);
        message.success({ content: '实体图谱导出成功', key: loadingKey });
      } else {
        message.destroy(loadingKey);
      }
    } catch (error) {
      message.destroy(loadingKey);
    }
  };

  /** 表格列 */
  const columns = [
    {
      title: '实体图谱名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (_: string, record: GraphVO) => {
        return (
          <Link
            to={`/manage/${organizationId}/entity/editEntityGraph/${record.id}`}
            onClick={(event) => {
              event.preventDefault();
              history.push(`/manage/${organizationId}/entity/editEntityGraph/${record.id}`, {
                readonly: !canEdit,
              });
            }}
          >
            {_}
          </Link>
        );
      },
    },
    {
      title: '关联本体图谱的名称',
      dataIndex: 'linkGraphName',
      key: 'linkGraphName',
      ellipsis: true,
      render: (_: string) => {
        return _ ? _ : '';
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (_: string) => {
        return <span>{_}</span>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      ellipsis: true,
      render: (_: string) => {
        return <span>{_ ? new Date(_).toLocaleString() : ''}</span>;
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
      title: '操作',
      key: 'action',
      width: 260,
      render: (_: any, record: GraphVO) => (
        <Row className="tableAction">
          <Button
            type="link"
            onClick={() => {
              history.push(
                `/manage/${organizationId}/entity/editEntityGraph/${record.id}`,
                { readonly: !canEdit },
              );
            }}
          >
            {canEdit ? '编辑' : '查看'}
          </Button>
          <Button type="link" onClick={() => handleExport(record)}>
            导出
          </Button>
          {canDeleteGraph ? <Button type="link" onClick={() => handleDelEntity(record)}>删除</Button> : null}
        </Row>
      ),
    },
  ];

  /** 处理导入实例表单提交 */
  const handleImportSubmit = () => {
    setIsImportModalVisible(false);
    refreshEntityList();
  };

  /** 新建成功后的回调 */
  const handleCreateSuccess = () => {
    /** 新建成功后回到第一页 */
    setCurrent(1);
    tableRun({
      graphType: 'ENTITY',
      pageNum: 1,
      pageSize: pageSize,
      organizationId: organizationId,
    });
  };

  return (
    <PageShell
      title="实体图谱"
      actions={
        <Space wrap>
          {canEdit ? <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setIsCreateModalVisible(true)}
          >
            新建实体图谱
          </Button> : null}
          {canEdit ? <Button
            onClick={() => setIsImportModalVisible(true)}
            icon={<ExportOutlined />}
          >
            导入实体图谱
          </Button> : null}
        </Space>
      }
    >
        {tableError ? (
          <Alert
            style={{ marginBottom: 12 }}
            type="error"
            showIcon
            message="实体图谱列表读取失败"
            action={<Button onClick={() => void refreshEntityList()}>重试</Button>}
          />
        ) : null}
        <Spin spinning={tableLoading} tip="数据加载中...">
          <Table
            size="small"
            columns={columns}
            dataSource={entityList?.data?.map((item: GraphVO) => ({
              ...item,
            }))}
            rowKey={(record: any) => record.id}
            pagination={false}
            locale={{ emptyText: tableError ? '列表读取失败' : '暂无实体图谱' }}
            scroll={{ y: 'calc(100vh - 292px)' }}
          />
        </Spin>
        <ListPagination
          current={current}
          pageSize={pageSize}
          total={total}
          loading={tableLoading || Boolean(tableError)}
          onChange={handleTableChange}
        />

        {/* 新建实体图谱弹窗 */}
        <EntityGraphAdd
          isVisible={isCreateModalVisible}
          onCancel={() => setIsCreateModalVisible(false)}
          onSuccess={handleCreateSuccess}
          organizationId={organizationId}
        />

        {/* 导入实体图谱弹窗 */}
        <ImportGraphPop
          isVisible={isImportModalVisible}
          onCancel={() => setIsImportModalVisible(false)}
          onSubmit={handleImportSubmit}
          title="导入实体图谱"
          organizationId={organizationId}
          graphType={GraphType.ENTITY}
        />
    </PageShell>
  );
}

export default GraphExample;
