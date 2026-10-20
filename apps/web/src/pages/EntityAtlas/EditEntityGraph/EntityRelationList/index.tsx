import BatchActionBtn from '@/components/BatchActionBtn';
import TabsPageTop from '@/components/TableAction';
import {
  deleteRelationship,
  getRelationshipOptional,
  pageRelationship,
} from '@/services_v1/relationshipService';
import {
  ApiResultListRelationshipVO,
  RelationshipVO,
} from '@/services_v1/typings';
import { useRequest } from '@umijs/max';
import { App, Button, Layout, Row, Space, Table, Tooltip } from 'antd';
import React, { forwardRef, useImperativeHandle, useState } from 'react';
import EntityRelationModal from '../EntityRelationModal';
import styles from './index.less';

const EntityRelationList = forwardRef<
  { refreshEntityRelationData: (relationshipIdList?: string[]) => void },
  {
    atlasId: string;
    readonly?: boolean;
  }
>(({ atlasId, readonly = false }, ref) => {
  const [pageSize, setPageSize] = useState(10);
  const [currentRelationList, setCurrentRelationList] = useState(1);
  const [relationSelectedRowKeys, setRelationSelectedRowKeys] = useState<
    React.Key[]
  >([]);
  const [exportDropdownOpen, setExportDropdownOpen] = useState(false);
  const [relationEditModalVisible, setRelationEditModalVisible] =
    useState(false);
  const [currentRelation, setCurrentRelation] = useState<RelationshipVO | null>(
    null,
  );
  const [selectedRelationNames, setSelectedRelationNames] = useState<string[]>(
    [],
  );
  const { modal, message } = App.useApp();

  /** 获取关系名称列表用于筛选 */
  const { data: relationshipOptionalRes, run: fetchRelationshipOptional } =
    useRequest(
      () => getRelationshipOptional({ graphId: atlasId, graphType: 'ENTITY' }),
      {
        manual: true,
        formatResult: (res) => res,
      },
    );

  /** 获取实体关系数据列表 */
  const {
    data: EntityRelationRes,
    refresh: refreshEntityRelationData,
    run: entityRelationTableRun,
    loading: entityRelationTableLoading,
  } = useRequest<ApiResultListRelationshipVO>(pageRelationship, {
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
    refreshEntityRelationData: (relationshipIdList?: string[]) => {
      if (relationshipIdList && relationshipIdList.length > 0) {
        // 如果传入了relationshipIdList，则根据ID列表查询
        entityRelationTableRun({
          graphId: atlasId,
          graphType: 'ENTITY',
          pageNum: 1,
          pageSize,
          relationshipIdList,
        });
        setCurrentRelationList(1); // 重置到第一页
      } else {
        // 否则执行正常刷新，保持当前筛选条件
        const filteredRelationshipIdList =
          selectedRelationNames.length > 0
            ? relationshipOptionalRes?.data
                ?.filter((item: any) =>
                  selectedRelationNames.includes(item.name),
                )
                .map((item: any) => item.id)
            : undefined;
        entityRelationTableRun({
          graphId: atlasId,
          graphType: 'ENTITY',
          pageNum: currentRelationList,
          pageSize,
          relationshipIdList: filteredRelationshipIdList,
        });
      }
    },
  }));

  /** 实体关系总条数 */
  const relationTotal = EntityRelationRes?.addition?.total
    ? EntityRelationRes?.addition?.total
    : 0;

  /** 实体关系表格分页和筛选 */
  const handleRelationTableChange = (pagination: any, filters: any) => {
    const { current, pageSize } = pagination;
    setCurrentRelationList(current);
    setPageSize(pageSize);
    // 处理关系名称筛选
    const relationNameFilter = filters?.name || [];
    setSelectedRelationNames(relationNameFilter);
    // 根据筛选的关系名称获取对应的ID列表
    const filteredRelationshipIdList =
      relationNameFilter.length > 0
        ? relationshipOptionalRes?.data
            ?.filter((item: any) => relationNameFilter.includes(item.name))
            .map((item: any) => item.id)
        : undefined;
    entityRelationTableRun({
      graphId: atlasId,
      graphType: 'ENTITY',
      pageNum: current,
      pageSize: pageSize,
      relationshipIdList: filteredRelationshipIdList,
    });
  };

  /** 处理删除后的分页逻辑 */
  const handleAfterDelete = (deletedCount: number) => {
    const currentPageDataLength = EntityRelationRes?.data?.length || 0;
    const shouldGoToPrevPage =
      deletedCount >= currentPageDataLength && currentRelationList > 1;

    // 获取当前筛选条件
    const filteredRelationshipIdList =
      selectedRelationNames.length > 0
        ? relationshipOptionalRes?.data
            ?.filter((item: any) => selectedRelationNames.includes(item.name))
            .map((item: any) => item.id)
        : undefined;

    if (shouldGoToPrevPage) {
      const newPage = currentRelationList - 1;
      setCurrentRelationList(newPage);
      entityRelationTableRun({
        graphId: atlasId,
        graphType: 'ENTITY',
        pageNum: newPage,
        pageSize: pageSize,
        relationshipIdList: filteredRelationshipIdList,
      });
    } else {
      entityRelationTableRun({
        graphId: atlasId,
        graphType: 'ENTITY',
        pageNum: currentRelationList,
        pageSize,
        relationshipIdList: filteredRelationshipIdList,
      });
    }
  };

  /**
   * 执行删除操作的核心逻辑
   * @param idList 要删除的ID列表
   * @param successMessage 成功提示信息
   * @param onSuccess 删除成功后的回调
   */
  const executeDelete = async (
    idList: string[],
    successMessage: string,
    onSuccess?: () => void,
  ) => {
    const res = await deleteRelationship({
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

  /** 删除单个实体关系 */
  const handleDelRelation = (record: RelationshipVO) => {
    if (!record.id) {
      message.warning('关系ID不存在，无法删除');
      return;
    }
    const relationId = record.id;
    modal.confirm({
      title: '删除实体关系',
      content: '确定删除该实体关系吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete([relationId], '删除成功');
      },
    });
  };

  /** 编辑实体关系 */
  function handleRelationEdit(record: RelationshipVO) {
    setCurrentRelation(record);
    setRelationEditModalVisible(true);
  }

  /** 新建实体关系 */
  function handleAddEntityRelation() {
    /** 清空当前关系，表示新建 */
    setCurrentRelation(null);
    setRelationEditModalVisible(true);
  }

  /** 批量删除实体关系 */
  const handleBatchDelete = (keys: React.Key[]) => {
    if (!keys.length) {
      message.warning('请先选择要删除的实体关系');
      return;
    }
    const idList = keys.filter((key): key is string => typeof key === 'string');
    if (idList.length !== keys.length) {
      message.error('选中的实体关系ID格式不正确');
      return;
    }
    modal.confirm({
      title: '批量删除实体关系',
      content: `确定删除选中的 ${idList.length} 个实体关系吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete(
          idList,
          `成功删除 ${idList.length} 个实体关系`,
          () => setRelationSelectedRowKeys([]),
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

  /** 实体关系表格列定义 */
  const EntityRelationColumns = [
    {
      title: '主实体',
      dataIndex: 'sourceNodeName',
      key: 'sourceNodeName',
      ellipsis: true,
      render: (sourceNodeName: string) => <span>{sourceNodeName}</span>,
    },
    {
      title: '关系名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (name: string) => <span>{name}</span>,
      filters: Array.from(
        new Set(
          relationshipOptionalRes?.data?.map((item: any) => item.name) || [],
        ),
      ).map((name) => ({
        text: name as string,
        value: name as string,
      })),
      filteredValue: selectedRelationNames,
      filterDropdownProps: {
        overlayStyle: {
          width: 300, // 设置筛选框宽度
          wordBreak: 'break-all',
          whiteSpace: 'pre-wrap',
        },
        onOpenChange: (open: boolean) => {
          if (open && !relationshipOptionalRes && atlasId) {
            fetchRelationshipOptional();
          }
        },
      },
    },
    {
      title: '目标实体',
      dataIndex: 'targetNodeName',
      key: 'targetNodeName',
      ellipsis: true,
      render: (targetNodeName: string) => <span>{targetNodeName}</span>,
    },
    {
      title: '关系描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: string) => <span>{description}</span>,
    },
    {
      title: '关系属性',
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
      width: 150,
      render: (_: any, record: RelationshipVO) => (
        <Space size="small">
          <Button type="link" disabled={readonly} onClick={() => handleRelationEdit(record)}>
            编辑
          </Button>
          <Button type="link" disabled={readonly} onClick={() => handleDelRelation(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  /** 行选择 */
  const rowSelection = {
    selectedRowKeys: relationSelectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      setRelationSelectedRowKeys(newSelectedRowKeys);
    },
  };

  return (
    <>
      <Layout className={styles.entityRelationContainer}>
        <TabsPageTop
          type="relation"
          itemLabel="实体关系"
          exportDropdownOpen={exportDropdownOpen}
          setExportDropdownOpen={setExportDropdownOpen}
          exportOptions={exportOptions}
          onAdd={handleAddEntityRelation}
          readonly={readonly}
          showImport={true}
          disableImport={false}
          onImport={() => {
            /** 实现导入功能 */
            message.info('实体关系导入功能待实现');
          }}
        />
        <Table
          size="small"
          columns={EntityRelationColumns as any}
          loading={entityRelationTableLoading}
          dataSource={EntityRelationRes?.data?.map((item: RelationshipVO) => ({
            ...item,
          }))}
          rowKey={(record: RelationshipVO) => record.id as string}
          rowSelection={readonly ? undefined : {
            type: 'checkbox',
            columnWidth: 30,
            ...rowSelection,
          }}
          pagination={{
            current: currentRelationList,
            pageSize: pageSize,
            total: relationTotal,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
          scroll={{ y: 'calc(100vh - 292px)' }}
          onChange={handleRelationTableChange}
        />
        {!readonly && EntityRelationRes?.data && EntityRelationRes.data.length > 0 && (
          <Row className={styles.batchActions}>
            <BatchActionBtn
              activeTab="relation"
              selectedRowKeys={relationSelectedRowKeys}
              onBatchDelete={handleBatchDelete}
            />
          </Row>
        )}
      </Layout>

      {/* 实体关系编辑与新建弹窗 */}
      <EntityRelationModal
        visible={relationEditModalVisible}
        onCancel={() => {
          setCurrentRelation(null);
          setRelationEditModalVisible(false);
        }}
        atlasId={atlasId}
        currentRelation={currentRelation}
        refreshRelationList={refreshEntityRelationData}
      />
    </>
  );
});

export default EntityRelationList;
