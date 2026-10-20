import BatchActionBtn from '@/components/BatchActionBtn';
import TabsPageTop from '@/components/TableAction';
import { getLinkOntologyNodeList } from '@/services_v1/graphService';
import { deleteNode, pageNode } from '@/services_v1/nodeService';
import { ApiResultListNodeVO, NodeVO } from '@/services_v1/typings';
import { useRequest } from '@umijs/max';
import {
  App,
  Button,
  Layout,
  Row,
  Space,
  Table,
  Tooltip,
  Typography,
} from 'antd';
import React, { forwardRef, useImperativeHandle, useState } from 'react';
import EntityEditModal from '../EntityEditModal';
import styles from './index.less';

const { Link } = Typography;

/** 实体对象列表组件 */
const EntityObjList = forwardRef<
  { refreshEntityData: (nodeIdList?: string[]) => void },
  {
    atlasId: string;
    readonly?: boolean;
  }
>(({ atlasId, readonly = false }, ref) => {
  const [pageSize, setPageSize] = useState(10);
  const [current, setPageCurrent] = useState(1);
  const [entitySelectedRowKeys, setEntitySelectedRowKeys] = useState<
    React.Key[]
  >([]);
  const [exportDropdownOpen, setExportDropdownOpen] = useState(false);
  const [entityEditModalVisible, setEntityEditModalVisible] = useState(false);
  const [currentEntity, setCurrentEntity] = useState<NodeVO | null>(null);
  const [selectedOntologyNames, setSelectedOntologyNames] = useState<string[]>(
    [],
  );
  const { message, modal } = App.useApp();

  /** 获取关联本体节点列表用于筛选 */
  const {
    data: ontologyNodeListRes,
    run: fetchOntologyNodeList,
    loading: ontologyNodeListLoading,
  } = useRequest(() => getLinkOntologyNodeList({ entityGraphId: atlasId }), {
    manual: true,
    formatResult: (res) => res,
  });

  /** 获取实体对象数据列表 */
  const {
    data: entityRes,
    refresh: refreshEntityData,
    run: entityTableRun,
    loading: entityTableLoading,
  } = useRequest<ApiResultListNodeVO>(pageNode, {
    formatResult: (res) => {
      return { ...res };
    },
    defaultParams: [
      {
        graphId: atlasId,
        graphType: 'ENTITY',
        pageNum: 1,
        pageSize,
      },
    ],
    ready: !!atlasId,
  });

  /** 暴露刷新方法给父组件 */
  useImperativeHandle(ref, () => ({
    refreshEntityData: (nodeIdList?: string[]) => {
      if (nodeIdList && nodeIdList.length > 0) {
        // 如果传入了nodeIdList，则根据ID列表查询
        entityTableRun({
          graphId: atlasId,
          graphType: 'ENTITY',
          pageNum: 1,
          pageSize,
          nodeIdList,
        });
        setPageCurrent(1); // 重置到第一页
      } else {
        // 否则执行正常刷新，保持当前筛选条件
        const linkOntologyNodeIdList =
          selectedOntologyNames.length > 0
            ? ontologyNodeListRes?.data
                ?.filter((item: any) =>
                  selectedOntologyNames.includes(item.name),
                )
                .map((item: any) => item.id)
            : undefined;
        entityTableRun({
          graphId: atlasId,
          graphType: 'ENTITY',
          pageNum: current,
          pageSize,
          linkOntologyNodeIdList,
        });
      }
    },
  }));

  /** 实体对象总条数 */
  const entityTotal = entityRes?.addition?.total
    ? entityRes?.addition?.total
    : 0;

  /** 实体对象表格分页和筛选 */
  const handleEntityTableChange = (pagination: any, filters: any) => {
    const { current: newCurrent, pageSize: newPageSize } = pagination;
    setPageCurrent(newCurrent);
    setPageSize(newPageSize);
    // 处理关联本体筛选
    const ontologyFilter = filters?.linkOntologyName || [];
    setSelectedOntologyNames(ontologyFilter);
    // 根据筛选的本体名称获取对应的ID列表
    const linkOntologyNodeIdList =
      ontologyFilter.length > 0
        ? ontologyNodeListRes?.data
            ?.filter((item: any) => ontologyFilter.includes(item.name))
            .map((item: any) => item.id)
        : undefined;
    entityTableRun({
      graphId: atlasId,
      graphType: 'ENTITY',
      pageNum: newCurrent,
      pageSize: newPageSize,
      linkOntologyNodeIdList,
    });
  };

  /** 新建对象 */
  function handleAddEntity() {
    setCurrentEntity(null);
    setEntityEditModalVisible(true);
  }

  /** 编辑实体对象 */
  function handleEditEntity(record: NodeVO) {
    setCurrentEntity(record);
    setEntityEditModalVisible(true);
  }

  /** 处理删除后的分页逻辑 */
  const handleAfterDelete = (deletedCount: number) => {
    const currentPageDataLength = entityRes?.data?.length || 0;
    const shouldGoToPrevPage =
      deletedCount >= currentPageDataLength && current > 1;

    // 获取当前筛选条件
    const linkOntologyNodeIdList =
      selectedOntologyNames.length > 0
        ? ontologyNodeListRes?.data
            ?.filter((item: any) => selectedOntologyNames.includes(item.name))
            .map((item: any) => item.id)
        : undefined;

    if (shouldGoToPrevPage) {
      const prevPage = current - 1;
      setPageCurrent(prevPage);
      entityTableRun({
        graphId: atlasId,
        graphType: 'ENTITY',
        pageNum: prevPage,
        pageSize: pageSize,
        linkOntologyNodeIdList,
      });
    } else {
      entityTableRun({
        graphId: atlasId,
        graphType: 'ENTITY',
        pageNum: current,
        pageSize,
        linkOntologyNodeIdList,
      });
    }
  };

  /** 执行删除操作的核心逻辑 */
  const executeDelete = async (
    idList: string[],
    successMessage: string,
    onSuccess?: () => void,
  ) => {
    const res = await deleteNode({
      idList,
      graphId: atlasId,
      graphType: 'ENTITY',
    });
    if (res?.success) {
      message.success(successMessage);
      handleAfterDelete(idList.length);
      onSuccess?.();
    } else {
      throw new Error(res?.message || '删除失败');
    }
  };

  /** 删除实体对象 */
  function handleDelEntity(record: NodeVO) {
    if (!record.id) {
      message.warning('实体对象ID不存在，无法删除');
      return;
    }
    const nodeId = record.id;
    modal.confirm({
      title: '删除实体对象',
      content: '确定删除该实体对象吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete([nodeId], '删除成功');
      },
    });
  }

  /** 批量删除处理函数 */
  const handleBatchDelete = (keys: React.Key[]) => {
    if (!keys.length) {
      message.warning('请先选择要删除的实体对象');
      return;
    }
    const idList = keys.filter((key): key is string => typeof key === 'string');
    if (idList.length !== keys.length) {
      message.error('选中的实体对象ID格式不正确');
      return;
    }
    modal.confirm({
      title: '批量删除实体对象',
      content: `确定删除选中的 ${idList.length} 个实体对象吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete(
          idList,
          `成功删除 ${idList.length} 个实体对象`,
          () => setEntitySelectedRowKeys([]),
        );
      },
    });
  };

  /** 导出选项 */
  const exportOptions = [
    { key: '1', label: '导出所选对象' },
    { key: '2', label: '导出所有对象' },
    { key: '3', label: '导出视觉表格' },
  ];

  /** 实体对象表格列 */
  const entityColumns = [
    {
      title: '对象名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (name: string) => <span>{name}</span>,
      // filters: Array.from(
      //   new Set(
      //     entityRes?.data?.atlasData?.map((item: any) => item.name) || [],
      //   ),
      // ).map((name) => ({
      //   text: name as string,
      //   value: name as string,
      // })),
      // onFilter: (value: any, record: any) => record.name === value,
    },
    {
      title: '对象描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: string) => <span>{description}</span>,
    },
    {
      title: '关联本体',
      dataIndex: 'linkOntologyName',
      key: 'linkOntologyName',
      ellipsis: true,
      render: (linkOntologyName: string) => <span>{linkOntologyName}</span>,
      filters: Array.from(
        new Set(ontologyNodeListRes?.data?.map((item: any) => item.name) || []),
      ).map((name) => ({
        text: name as string,
        value: name as string,
      })),
      filteredValue: selectedOntologyNames,
      filterDropdownProps: {
        overlayStyle: {
          width: 300, // 设置筛选框宽度
          wordBreak: 'break-all',
          whiteSpace: 'pre-wrap',
        },
        onOpenChange: (open: boolean) => {
          if (open && !ontologyNodeListRes && atlasId) {
            fetchOntologyNodeList();
          }
        },
      },
    },
    {
      title: '对象属性',
      dataIndex: 'propertyJson',
      key: 'propertyJson',
      ellipsis: true,
      render: (propertyJson: string) => {
        try {
          const properties = propertyJson ? JSON.parse(propertyJson) : {};
          const propertyEntries = Object.entries(properties);
          if (propertyEntries.length === 0) {
            return null;
          }
          return (
            <Tooltip
              placement="topLeft"
              title={JSON.stringify(properties, null, 2)}
              styles={{
                body: {
                  maxHeight: '240px',
                  maxWidth: '300px',
                  overflowY: 'auto',
                  overflowX: 'auto',
                  wordBreak: 'break-all',
                  whiteSpace: 'pre-wrap',
                },
              }}
            >
              <Space>
                {propertyEntries.map(([key, value]) => {
                  const displayValue =
                    typeof value === 'object' && value !== null
                      ? JSON.stringify(value)
                      : String(value);
                  return (
                    <Button
                      key={key}
                      size="small"
                      style={{ cursor: 'default', pointerEvents: 'none' }}
                    >
                      {key} : {displayValue}
                    </Button>
                  );
                })}
              </Space>
            </Tooltip>
          );
        } catch (error) {
          return null;
        }
      },
    },
    {
      title: '操作',
      key: 'actions',
      ellipsis: true,
      width: 100,
      render: (_: any, record: NodeVO) => (
        <Space size="middle">
          <Link aria-disabled={readonly} onClick={() => { if (!readonly) handleEditEntity(record); }}>编辑</Link>
          <Link aria-disabled={readonly} onClick={() => { if (!readonly) handleDelEntity(record); }}>删除</Link>
        </Space>
      ),
    },
  ];

  /** 行选择 */
  const rowSelection = {
    selectedRowKeys: entitySelectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      setEntitySelectedRowKeys(newSelectedRowKeys);
    },
  };

  return (
    <>
      <Layout className={styles.entityObjContainer}>
        <TabsPageTop
          type="entity"
          itemLabel="实体对象"
          exportDropdownOpen={exportDropdownOpen}
          setExportDropdownOpen={setExportDropdownOpen}
          exportOptions={exportOptions}
          onAdd={handleAddEntity}
          readonly={readonly}
          showImport={true}
          onImport={() => {
            message.info('实体对象导入功能待实现');
          }}
        />
        <Table
          size="small"
          columns={entityColumns as any}
          loading={entityTableLoading}
          dataSource={entityRes?.data?.map((item: NodeVO) => ({
            ...item,
          }))}
          rowKey={(record: NodeVO) => record.id as string}
          rowSelection={readonly ? undefined : {
            type: 'checkbox',
            columnWidth: 30,
            ...rowSelection,
          }}
          pagination={{
            current: current,
            pageSize: pageSize,
            total: entityTotal,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
          scroll={{ y: 'calc(100vh - 292px)' }}
          onChange={handleEntityTableChange}
        />
        {!readonly && entityRes?.data?.length > 0 && (
          <Row className={styles.batchActions}>
            <BatchActionBtn
              activeTab="entity"
              selectedRowKeys={entitySelectedRowKeys}
              onBatchDelete={handleBatchDelete}
            />
          </Row>
        )}
      </Layout>

      {/* 实体对象编辑与新建弹窗 */}
      <EntityEditModal
        visible={entityEditModalVisible}
        onCancel={() => {
          setCurrentEntity(null);
          setEntityEditModalVisible(false);
        }}
        atlasId={atlasId}
        currentEntity={currentEntity}
        refreshEntityList={refreshEntityData}
      />
    </>
  );
});

export default EntityObjList;
