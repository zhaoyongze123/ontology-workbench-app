import BatchActionBtn from '@/components/BatchActionBtn';
import TableAction from '@/components/TableAction';
import UploadJsonFile from '@/components/UploadJsonFiles';
import ListPagination from '@/features/lists/ListPagination';
import { exportOntology, importOntology } from '@/services/ontologyService';
import { deleteNode, pageNode } from '@/services_v1/nodeService';
import {
  ApiResultListNodeVO,
  NodeVO,
  PropertySchema,
} from '@/services_v1/typings';
import { downloadJson } from '@/utils/util';
import { useRequest } from '@umijs/max';
import { Alert, App, Button, Layout, Row, Space, Table, Tooltip } from 'antd';
import React, { forwardRef, useImperativeHandle, useState } from 'react';
import OntoEditModal from '../OntoEditModal';
import styles from './index.less';

/** 本体对象列表组件 */
const OntoObjList = forwardRef<
  { refreshOntoData: (nodeIdList?: string[]) => void },
  {
    atlasId: string;
    readonly?: boolean;
    canMutate?: boolean;
  }
>(({ atlasId, readonly = false, canMutate = true }, ref) => {
  const [pageSize, setPageSize] = useState(20);
  const [current, setPageCurrent] = useState(1);
  const [exportDropdownOpen, setExportDropdownOpen] = useState(false);
  const [entitySelectedRowKeys, setEntitySelectedRowKeys] = useState([]);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [currentOnto, setCurrentOnto] = useState<NodeVO | null>(null);
  const [ontoEditModalVisible, setOntoEditModalVisible] = useState(false);
  const { message, modal } = App.useApp();

  /** 获取本体对象数据列表 */
  const {
    data: OntoData,
    refresh: refreshOntoData,
    run: OntoTableRun,
    loading: OntoTableLoading,
    error: OntoTableError,
  } = useRequest<ApiResultListNodeVO>(pageNode, {
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
    refreshOntoData: (nodeIdList?: string[]) => {
      if (nodeIdList && nodeIdList.length > 0) {
        // 如果传入了nodeIdList，则根据ID列表查询
        OntoTableRun({
          graphId: atlasId,
          graphType: 'ONTOLOGY',
          pageNum: 1,
          pageSize,
          nodeIdList,
        });
        setPageCurrent(1); // 重置到第一页
      } else {
        // 否则执行正常刷新
        refreshOntoData();
      }
    },
  }));

  /** 本体对象总数 */
  const OntoTotal = OntoData?.addition?.total ? OntoData?.addition?.total : 0;

  /** 表格变化处理函数 */
  const handlePageChange = (newCurrent: number, newPageSize: number) => {
    const nextPage = newPageSize === pageSize ? newCurrent : 1;
    setPageCurrent(nextPage);
    setPageSize(newPageSize);
    OntoTableRun({
      graphId: atlasId,
      graphType: 'ONTOLOGY',
      pageNum: nextPage,
      pageSize: newPageSize,
    });
  };

  /** 新建本体对象 */
  function handleAddOnto() {
    setCurrentOnto(null);
    setOntoEditModalVisible(true);
  }

  /** 编辑本体对象 */
  function handleOntoEdit(record: NodeVO) {
    setCurrentOnto(record);
    setOntoEditModalVisible(true);
  }

  /** 处理删除后的分页逻辑 */
  const handleAfterDelete = (deletedCount: number) => {
    const currentPageDataLength = OntoData?.data?.length || 0;
    const shouldGoToPrevPage =
      deletedCount >= currentPageDataLength && current > 1;

    if (shouldGoToPrevPage) {
      const prevPage = current - 1;
      setPageCurrent(prevPage);
      OntoTableRun({
        graphId: atlasId,
        graphType: 'ONTOLOGY',
        pageNum: prevPage,
        pageSize: pageSize,
      });
    } else {
      refreshOntoData();
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
    const res = await deleteNode({
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

  /** 删除单个本体对象 */
  const handleDeleteOnto = (record: NodeVO) => {
    if (!record.id) {
      message.warning('无法识别此本体对象，请刷新列表后重试。');
      return;
    }
    const nodeId = record.id;
    modal.confirm({
      title: '删除本体对象',
      content: '确定删除该本体对象吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete([nodeId], '删除成功');
      },
    });
  };

  /** 批量删除本体对象 */
  const handleBatchDelete = (keys: React.Key[]) => {
    if (!keys.length) {
      message.warning('请先选择要删除的本体对象');
      return;
    }
    const idList = keys.filter((key): key is string => typeof key === 'string');
    if (idList.length !== keys.length) {
      message.error('所选本体对象信息无效，请刷新列表后重试。');
      return;
    }
    modal.confirm({
      title: '批量删除本体对象',
      content: `确定删除选中的 ${idList.length} 个本体对象吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await executeDelete(
          idList,
          `成功删除 ${idList.length} 个本体对象`,
          () => setEntitySelectedRowKeys([]),
        );
      },
    });
  };

  /** 打开导入本体对象弹窗 */
  const handleImport = () => {
    setImportModalVisible(true);
  };

  /** 处理本体对象导入 */
  const handleImportOntology = async (fileList: any) => {
    if (!fileList) {
      message.error('请选择要导入的文件');
      return Promise.reject(new Error('未选择文件'));
    }
    const loadingKey = `import-${Date.now()}`;
    message.loading({
      content: `正在导入文件: ${fileList.name}`,
      key: loadingKey,
      duration: 0,
    });
    try {
      const res = await importOntology({
        file: fileList,
        atlasId: atlasId,
      });
      if (res.success) {
        message.destroy(loadingKey);
        await refreshOntoData();
        return Promise.resolve();
      }
    } catch (error: any) {
      message.destroy(loadingKey);
      return Promise.reject(error);
    }
  };

  /** 处理导出本体对象 */
  const handleExportOntology = async (type: string) => {
    try {
      let idList: string[] = [];
      /** 根据类型确定导出的ID列表 */
      if (type === '1') {
        /** 导出所选对象 */
        if (entitySelectedRowKeys.length === 0) {
          message.warning('请先选择要导出的本体对象');
          return;
        }
        idList = entitySelectedRowKeys as string[];
      } else if (type === '2') {
        /** 导出所有对象，传空数组表示导出全部 */
        /** 检查是否有本体对象 */
        if (OntoTotal === 0) {
          message.warning('没有本体对象可以导出');
          return;
        }
        idList = [];
      } else if (type === '3') {
        /** 导出视觉表格 */
        message.info('导出视觉表格功能待实现');
        return;
      }
      const res = await exportOntology({
        idList: idList,
        atlasId: atlasId,
      });
      if (res.success && res.data && res.data.length > 0) {
        /** 使用工具函数下载JSON文件 */
        const fileName = `ontology_export_${new Date().getTime()}.json`;
        await downloadJson(res.data, fileName);
        message.success('导出成功');
      }
    } catch (error: any) {
      if (error === false || !error.response) {
        message.info('已取消导出');
        /** 阻止错误继续传播到全局错误处理 */
        return;
      }
    }
  };

  /** 导出选项 */
  const exportOptions = [
    {
      key: '1',
      label: '导出所选对象',
      onClick: () => handleExportOntology('1'),
    },
    {
      key: '2',
      label: '导出所有对象',
      onClick: () => handleExportOntology('2'),
    },
    {
      key: '3',
      label: '导出视觉表格',
      onClick: () => handleExportOntology('3'),
    },
  ];

  /** 本体对象表格列定义 */
  const ontoColumns = [
    {
      title: '对象名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      // filters: OntoData?.data?.map((item: OntologyVO) => ({
      //   text: item.name,
      //   value: item.name,
      // })),
      // onFilter: (value: any, record: OntologyVO) => record.name === value,
      render: (name: string) => {
        return <span>{name}</span>;
      },
    },
    {
      title: '对象描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: string) => {
        return <span>{description}</span>;
      },
    },
    {
      title: '对象属性',
      dataIndex: 'propertySchemaJson',
      key: 'propertySchemaJson',
      ellipsis: true,
      render: (propertySchemaJson: PropertySchema[]) => (
        <Tooltip
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
          <Space>
            {propertySchemaJson && propertySchemaJson.length > 0
              ? propertySchemaJson.map((prop, index) => (
                  <Button
                    key={`${prop.name}-${index}`}
                    size="small"
                    style={{ cursor: 'default', pointerEvents: 'none' }}
                  >
                    {prop.name}
                  </Button>
                ))
              : null}
          </Space>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      ellipsis: true,
      render: (_: any, record: NodeVO) => (
        <Space size="small">
          <Button
            type="link"
            onClick={() => handleOntoEdit(record)}
            disabled={readonly || !canMutate}
          >
            编辑
          </Button>
          <Button
            type="link"
            onClick={() => handleDeleteOnto(record)}
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
    selectedRowKeys: entitySelectedRowKeys,
    onChange: (newSelectedRowKeys: any) => {
      setEntitySelectedRowKeys(newSelectedRowKeys);
    },
  };

  return (
    <>
      <Layout className={styles.ontoObjContainer}>
        <TableAction
          type="entity"
          itemLabel="本体对象"
          exportDropdownOpen={exportDropdownOpen}
          setExportDropdownOpen={setExportDropdownOpen}
          exportOptions={exportOptions}
          onAdd={handleAddOnto}
          onImport={handleImport}
          readonly={!canMutate}
          showImport={true}
        />
        {OntoTableError ? (
          <Alert
            type="error"
            showIcon
            message="本体对象读取失败"
            action={
              <Button onClick={() => void refreshOntoData()}>重试</Button>
            }
          />
        ) : null}
        <Table
          size="small"
          columns={ontoColumns}
          loading={OntoTableLoading}
          dataSource={OntoData?.data?.map((item: NodeVO) => ({
            ...item,
          }))}
          rowKey={(record: NodeVO) => record.id as string}
          rowSelection={
            !canMutate
              ? undefined
              : {
                  type: 'checkbox',
                  columnWidth: 30,
                  ...rowSelection,
                }
          }
          pagination={false}
          scroll={{ y: 'calc(100vh - 292px)' }}
          locale={{
            emptyText: OntoTableError ? '列表读取失败' : '暂无本体对象',
          }}
        />
        {canMutate && OntoData?.data?.length > 0 && (
          <Row className={styles.batchActions}>
            <BatchActionBtn
              activeTab="entity"
              selectedRowKeys={entitySelectedRowKeys}
              onBatchDelete={handleBatchDelete}
            />
          </Row>
        )}
        <ListPagination
          current={current}
          pageSize={pageSize}
          total={OntoTotal}
          loading={OntoTableLoading || Boolean(OntoTableError)}
          onChange={handlePageChange}
        />
      </Layout>

      {/* 本体对象编辑与新建弹窗 */}
      <OntoEditModal
        visible={ontoEditModalVisible}
        onCancel={() => {
          setCurrentOnto(null);
          setOntoEditModalVisible(false);
        }}
        atlasId={atlasId}
        currentOnto={currentOnto}
        refreshOntoList={refreshOntoData}
      />

      {/* 导入本体对象弹窗 */}
      <UploadJsonFile
        open={importModalVisible}
        closeFun={(text) => {
          setImportModalVisible(false);
          if (text === 'success') {
            message.success('导入成功');
            refreshOntoData();
          }
        }}
        onOk={handleImportOntology}
      />
    </>
  );
});

export default OntoObjList;
