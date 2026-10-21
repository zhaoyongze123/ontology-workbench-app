import ImportGraphPop from '@/components/ImportGraphPop';
import PageShell from '@/features/layout/PageShell';
import ListPagination from '@/features/lists/ListPagination';
import { useProjectAccess } from '@/features/projects/useProjectAccess';
import {
  deleteGraph,
  exportGraph,
  pageGraph,
} from '@/services_v1/graphService';
import { ApiResultListGraphVO, GraphVO } from '@/services_v1/typings';
import { GraphType } from '@/utils/typeEnum';
import { downloadJson } from '@/utils/util';
import { ImportOutlined, PlusOutlined } from '@ant-design/icons';
import { history, Link, useParams, useRequest } from '@umijs/max';
import { Alert, App, Button, Row, Space, Spin, Table } from 'antd';
import { useState } from 'react';
import OntoGraphAdd from './OntoGraphAdd';

/** 本体图谱管理 */
function OntologyManage() {
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [isImportModalVisible, setIsImportModalVisible] = useState(false);
  const [pageSize, setPageSize] = useState(20);
  const [current, setPageCurrent] = useState(1);
  const params = useParams<{ id: string }>();
  const organizationId = params.id || '';
  const { can } = useProjectAccess(organizationId);
  const canEdit = can('EDIT');
  const canDeleteGraph = can('DELETE_GRAPH');
  const { modal, message } = App.useApp();

  /** 获取本体图谱列表 */
  const {
    data: ontologyList,
    refresh: refreshOntologyList,
    run: tableRun,
    loading: tableLoading,
    error: tableError,
  } = useRequest<ApiResultListGraphVO>(pageGraph, {
    formatResult: (res) => {
      return { ...res };
    },
    defaultParams: [
      {
        graphType: 'ONTOLOGY',
        pageNum: current,
        pageSize: pageSize,
        organizationId: organizationId,
      },
    ],
  });

  /** 本体图谱总数 */
  const total = ontologyList?.addition?.total
    ? ontologyList?.addition?.total
    : 0;

  /** 表格变化处理函数 */
  const handlePageChange = (newCurrent: number, newPageSize: number) => {
    const nextPage = newPageSize === pageSize ? newCurrent : 1;
    setPageCurrent(nextPage);
    setPageSize(newPageSize);
    tableRun({
      graphType: 'ONTOLOGY',
      pageNum: nextPage,
      pageSize: newPageSize,
      organizationId: organizationId,
    });
  };

  /** 处理删除后的分页逻辑 */
  const handleAfterDelete = () => {
    const currentPageDataLength = ontologyList?.data?.length || 0;
    const shouldGoToPrevPage = currentPageDataLength === 1 && current > 1;
    if (shouldGoToPrevPage) {
      const prevPage = current - 1;
      setPageCurrent(prevPage);
      tableRun({
        graphType: 'ONTOLOGY',
        pageNum: prevPage,
        pageSize: pageSize,
        organizationId: organizationId,
      });
    } else {
      refreshOntologyList();
    }
  };

  /** 删除本体图谱 */
  const handleDelete = (record: GraphVO) => {
    if (!record.id) {
      message.warning('无法识别此图谱，请刷新列表后重试。');
      return;
    }
    const graphId = record.id;
    const graphName = record.name || '该图谱';
    modal.confirm({
      title: '删除本体图谱',
      content: `确定要删除"${graphName}"吗？`,
      okText: '删除',
      cancelText: '取消',
      onOk: async () => {
        const response = await deleteGraph({
          graphId,
          graphType: 'ONTOLOGY',
          organizationId: organizationId,
        });
        if (response?.success) {
          message.success('本体图谱删除成功');
          handleAfterDelete();
        }
      },
    });
  };

  /** 导入本体图谱 */
  const handleImportSubmit = () => {
    setIsImportModalVisible(false);
    refreshOntologyList();
  };

  /** 新建成功后的回调 */
  const handleCreateSuccess = () => {
    /** 新建成功后回到第一页 */
    setPageCurrent(1);
    tableRun({
      graphType: 'ONTOLOGY',
      pageNum: 1,
      pageSize: pageSize,
      organizationId: organizationId,
    });
  };

  /** 导出本体图谱 */
  const handleExport = async (record: GraphVO) => {
    if (!record.id) {
      message.warning('无法识别此图谱，请刷新列表后重试。');
      return;
    }
    const graphName = record.name || '本体图谱';
    const loadingKey = 'exportLoading';
    message.loading({
      content: '正在导出本体图谱...',
      key: loadingKey,
      duration: 0,
    });
    try {
      const response = await exportGraph({
        graphId: record.id,
        graphType: 'ONTOLOGY',
      });
      if (response?.success && response.data) {
        await downloadJson(response.data, `${graphName}.json`);
        message.success({ content: '本体图谱导出成功', key: loadingKey });
      } else {
        message.destroy(loadingKey);
      }
    } catch (error) {
      message.destroy(loadingKey);
    }
  };

  /** 定义表格列 */
  const columns = [
    {
      title: '本体图谱名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (_: string, record: GraphVO) => {
        return (
          <Link
            to={`/manage/${organizationId}/ontology/editOntologyGraph/${record.id}`}
            onClick={(e) => {
              e.preventDefault();
              if (record.id) {
                history.push(
                  `/manage/${organizationId}/ontology/editOntologyGraph/${record.id}`,
                  {
                    readonly: record.hasEntity || !canEdit,
                  },
                );
              }
            }}
          >
            {_}
          </Link>
        );
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
      title: '是否关联实体图谱',
      dataIndex: 'hasEntity',
      key: 'hasEntity',
      ellipsis: true,
      render: (_: boolean) => {
        return _ ? <span>是</span> : <span>否</span>;
      },
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
      title: '操作',
      key: 'action',
      width: 260,
      render: (_: any, record: GraphVO) => (
        <Row className="tableAction">
          <Button
            type="link"
            onClick={() => {
              if (record.id) {
                history.push(
                  `/manage/${organizationId}/ontology/editOntologyGraph/${record.id}`,
                  {
                    readonly: record.hasEntity || !canEdit,
                  },
                );
              }
            }}
          >
            {record.hasEntity || !canEdit ? '查看' : '编辑'}
          </Button>
          {/* <Button type="link" onClick={() => {}}>
            复制
          </Button> */}
          <Button type="link" onClick={() => handleExport(record)}>
            导出
          </Button>
          {canDeleteGraph ? (
            <Button type="link" onClick={() => handleDelete(record)}>
              删除
            </Button>
          ) : null}
        </Row>
      ),
    },
  ];

  return (
    <PageShell
      title="本体图谱"
      actions={
        <Space wrap>
          {canEdit ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setIsCreateModalVisible(true)}
            >
              新建本体图谱
            </Button>
          ) : null}
          {canEdit ? (
            <Button
              icon={<ImportOutlined />}
              onClick={() => setIsImportModalVisible(true)}
            >
              导入本体图谱
            </Button>
          ) : null}
        </Space>
      }
    >
      {tableError ? (
        <Alert
          style={{ marginBottom: 12 }}
          type="error"
          showIcon
          message="本体图谱列表读取失败"
          action={
            <Button onClick={() => void refreshOntologyList()}>重试</Button>
          }
        />
      ) : null}
      <Spin spinning={tableLoading} tip="数据加载中...">
        <Table
          size="small"
          columns={columns}
          dataSource={ontologyList?.data?.map((item: GraphVO) => ({
            ...item,
          }))}
          rowKey={(record: GraphVO) => record.id as string}
          pagination={false}
          locale={{ emptyText: tableError ? '列表读取失败' : '暂无本体图谱' }}
          scroll={{ y: 'calc(100vh - 292px)' }}
        />
      </Spin>
      <ListPagination
        current={current}
        pageSize={pageSize}
        total={total}
        loading={tableLoading || Boolean(tableError)}
        onChange={handlePageChange}
      />

      {/* 新建本体图谱弹窗组件 */}
      <OntoGraphAdd
        isVisible={isCreateModalVisible}
        onCancel={() => setIsCreateModalVisible(false)}
        onSuccess={handleCreateSuccess}
        organizationId={organizationId}
      />

      {/* 导入本体图谱弹窗组件 */}
      <ImportGraphPop
        isVisible={isImportModalVisible}
        onCancel={() => setIsImportModalVisible(false)}
        onSubmit={handleImportSubmit}
        title="导入本体图谱"
        organizationId={organizationId}
        graphType={GraphType.ONTOLOGY}
      />
    </PageShell>
  );
}

export default OntologyManage;
