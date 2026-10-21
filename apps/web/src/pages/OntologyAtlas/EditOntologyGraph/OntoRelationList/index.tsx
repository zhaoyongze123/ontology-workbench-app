import BatchActionBtn from '@/components/BatchActionBtn';
import TableAction from '@/components/TableAction';
import ListPagination from '@/features/lists/ListPagination';
import {
  deleteRelationship,
  pageRelationship,
} from '@/services_v1/relationshipService';
import {
  ApiResultListRelationshipVO,
  PropertySchema,
  RelationshipVO,
} from '@/services_v1/typings';
import { useRequest } from '@umijs/max';
import { Alert, App, Button, Layout, Row, Space, Table, Tooltip } from 'antd';
import React, { forwardRef, useImperativeHandle, useState } from 'react';
import OntoRelationModal from '../OntoRelationModal';
import styles from './index.less';

/** 本体关系列表 */
const OntoRelationList = forwardRef<
  { refreshRelationData: (relationshipIdList?: string[]) => void },
  {
    atlasId: string;
    readonly?: boolean;
    canMutate?: boolean;
  }
>(({ atlasId, readonly = false, canMutate = true }, ref) => {
  const [pageSize, setPageSize] = useState(20);
  const [currentRelationList, setCurrentRelationList] = useState(1);
  const [relationExportDropdownOpen, setRelationExportDropdownOpen] =
    useState(false);
  const [relationSelectedRowKeys, setRelationSelectedRowKeys] = useState<
    React.Key[]
  >([]);
  const [relationEditModalVisible, setRelationEditModalVisible] =
    useState(false);
  const [currentRelation, setCurrentRelation] = useState<RelationshipVO | null>(
    null,
  );
  const { modal, message } = App.useApp();

  /** 获取本体关系数据列表 */
  const {
    data: OntoRelationData,
    refresh: refreshRelationData,
    run: OntoRelationTableRun,
    loading: OntoRelationTableLoading,
    error: OntoRelationTableError,
  } = useRequest<ApiResultListRelationshipVO>(pageRelationship, {
    formatResult: (res) => {
      return { ...res };
    },
    defaultParams: [
      {
        graphId: atlasId,
        graphType: 'ONTOLOGY',
        pageNum: 1,
        pageSize,
      },
    ],
    ready: !!atlasId,
  });

  /** 暴露刷新方法给父组件 */
  useImperativeHandle(ref, () => ({
    refreshRelationData: (relationshipIdList?: string[]) => {
      if (relationshipIdList && relationshipIdList.length > 0) {
        // 如果传入了relationshipIdList，则根据ID列表查询
        OntoRelationTableRun({
          graphId: atlasId,
          graphType: 'ONTOLOGY',
          pageNum: 1,
          pageSize,
          relationshipIdList,
        });
        setCurrentRelationList(1); // 重置到第一页
      } else {
        // 否则执行正常刷新
        refreshRelationData();
      }
    },
  }));

  /** 本体关系总数 */
  const ontoRelationTotal = OntoRelationData?.addition?.total
    ? OntoRelationData?.addition?.total
    : 0;

  /** 本体关系表格分页 */
  const handleRelationPageChange = (
    nextCurrent: number,
    nextPageSize: number,
  ) => {
    const currentPage = nextPageSize === pageSize ? nextCurrent : 1;
    setCurrentRelationList(currentPage);
    setPageSize(nextPageSize);
    OntoRelationTableRun({
      graphId: atlasId,
      graphType: 'ONTOLOGY',
      pageNum: currentPage,
      pageSize: nextPageSize,
    });
  };

  /** 新建本体关系 */
  function handleAddOntoRelation() {
    setCurrentRelation(null);
    setRelationEditModalVisible(true);
  }

  /** 编辑本体关系 */
  function handleRelationEdit(record: RelationshipVO) {
    setCurrentRelation(record);
    setRelationEditModalVisible(true);
  }

  /** 处理删除后的分页逻辑 */
  const handleAfterDelete = (deletedCount: number) => {
    const currentPageDataLength = OntoRelationData?.data?.length || 0;
    const shouldGoToPrevPage =
      deletedCount >= currentPageDataLength && currentRelationList > 1;
    if (shouldGoToPrevPage) {
      const newPage = currentRelationList - 1;
      setCurrentRelationList(newPage);
      OntoRelationTableRun({
        graphId: atlasId,
        graphType: 'ONTOLOGY',
        pageNum: newPage,
        pageSize: pageSize,
      });
    } else {
      refreshRelationData();
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
      graphType: 'ONTOLOGY',
    });
    if (res?.success) {
      message.success(successMessage);
      handleAfterDelete(idList.length);
      onSuccess?.();
    } else {
      throw new Error(res?.message || '删除失败');
    }
  };

  /** 删除单个本体关系 */
  const handleDelRelation = (record: RelationshipVO) => {
    if (!record.id) {
      message.warning('无法识别此本体关系，请刷新列表后重试。');
      return;
    }
    const relationId = record.id; // 提取 ID，确保类型安全
    modal.confirm({
      title: '删除本体关系',
      content: '确定删除该本体关系吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete([relationId], '删除成功');
      },
    });
  };

  /** 批量删除本体关系 */
  const handleBatchDelete = (keys: React.Key[]) => {
    if (!keys.length) {
      message.warning('请先选择要删除的关系');
      return;
    }
    const idList = keys.filter((key): key is string => typeof key === 'string');
    if (idList.length !== keys.length) {
      message.error('所选本体关系信息无效，请刷新列表后重试。');
      return;
    }
    modal.confirm({
      title: '批量删除本体关系',
      content: `确定删除选中的 ${idList.length} 个本体关系吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete(
          idList,
          `成功删除 ${idList.length} 个本体关系`,
          () => setRelationSelectedRowKeys([]), // 删除成功后清空选中状态
        );
      },
    });
  };

  /** 导出选项 */
  const exportOptions = [
    { key: '1', label: '导出所选关系', onClick: () => {} },
    { key: '2', label: '导出所有关系', onClick: () => {} },
    { key: '3', label: '导出视觉表格', onClick: () => {} },
  ];

  /** 本体关系表格列定义 */
  const OntoRelationColumns = [
    {
      title: '主本体',
      dataIndex: 'sourceNodeName',
      key: 'sourceNodeName',
      ellipsis: true,
      render: (sourceNodeName: string) => {
        return <span>{sourceNodeName}</span>;
      },
    },
    {
      title: '关系名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (name: string) => {
        return <span>{name}</span>;
      },
    },
    {
      title: '目标本体',
      dataIndex: 'targetNodeName',
      key: 'targetNodeName',
      ellipsis: true,
      render: (targetNodeName: string) => {
        return <span>{targetNodeName}</span>;
      },
    },
    {
      title: '关系描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: string) => {
        return <span>{description}</span>;
      },
    },
    {
      title: '关系属性',
      dataIndex: 'propertySchemaJson',
      key: 'propertySchemaJson',
      ellipsis: true,
      render: (propertySchemaJson: PropertySchema[]) => {
        if (!propertySchemaJson || propertySchemaJson.length === 0) {
          return null;
        }
        return (
          <Space>
            {propertySchemaJson.map((prop, index) => (
              <Tooltip
                key={`${prop.name}-${index}`}
                placement="topLeft"
                title={JSON.stringify(propertySchemaJson, null, 2)}
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
                <Button
                  size="small"
                  style={{ cursor: 'default', pointerEvents: 'none' }}
                >
                  {prop.name}
                </Button>
              </Tooltip>
            ))}
          </Space>
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      ellipsis: true,
      width: 150,
      render: (_: any, record: RelationshipVO) => (
        <Space size="small">
          <Button
            type="link"
            onClick={() => handleRelationEdit(record)}
            disabled={readonly || !canMutate}
          >
            编辑
          </Button>
          <Button
            type="link"
            onClick={() => handleDelRelation(record)}
            disabled={!canMutate}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  /** 行选择器 */
  const rowSelection = {
    selectedRowKeys: relationSelectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      setRelationSelectedRowKeys(newSelectedRowKeys);
    },
  };

  return (
    <>
      <Layout className={styles.ontoRelationContainer}>
        <TableAction
          type="relation"
          itemLabel="本体关系"
          exportDropdownOpen={relationExportDropdownOpen}
          setExportDropdownOpen={setRelationExportDropdownOpen}
          exportOptions={exportOptions}
          onAdd={handleAddOntoRelation}
          readonly={!canMutate}
          showImport={true}
          disableImport={false}
          onImport={() => {
            message.info('本体关系导入功能待实现');
          }}
        />
        {OntoRelationTableError ? (
          <Alert
            type="error"
            showIcon
            message="本体关系列表读取失败"
            action={
              <Button onClick={() => void refreshRelationData()}>重试</Button>
            }
          />
        ) : null}
        <Table
          size="small"
          columns={OntoRelationColumns}
          loading={OntoRelationTableLoading}
          dataSource={OntoRelationData?.data?.map((item: RelationshipVO) => ({
            ...item,
          }))}
          pagination={false}
          scroll={{ y: 'calc(100vh - 292px)' }}
          rowKey={(record: RelationshipVO) => record.id as string}
          rowSelection={
            !canMutate
              ? undefined
              : {
                  type: 'checkbox',
                  columnWidth: 30,
                  ...rowSelection,
                }
          }
          locale={{
            emptyText: OntoRelationTableError ? '列表读取失败' : '暂无本体关系',
          }}
        />
        {canMutate && OntoRelationData?.data?.length > 0 && (
          <Row className={styles.batchActions}>
            <BatchActionBtn
              activeTab="relation"
              selectedRowKeys={relationSelectedRowKeys}
              onBatchDelete={handleBatchDelete}
            />
          </Row>
        )}
        <ListPagination
          current={currentRelationList}
          pageSize={pageSize}
          total={ontoRelationTotal}
          loading={OntoRelationTableLoading || Boolean(OntoRelationTableError)}
          onChange={handleRelationPageChange}
        />
      </Layout>

      {/* 本体关系编辑与新建弹窗 */}
      {relationEditModalVisible && (
        <OntoRelationModal
          visible={relationEditModalVisible}
          onCancel={() => {
            setCurrentRelation(null);
            setRelationEditModalVisible(false);
          }}
          atlasId={atlasId}
          currentRelation={currentRelation}
          refreshRelationList={refreshRelationData}
        />
      )}
    </>
  );
});

export default OntoRelationList;
