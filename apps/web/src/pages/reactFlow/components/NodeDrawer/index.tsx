import { validateGraphName } from '@/utils/validator';
import { CloseOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
import { type Node } from '@xyflow/react';
import {
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Form,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  deleteNode,
  editNode,
  getNodeInfo,
} from '../../../../services_v1/nodeService';
import type { NodeData } from '../../type';
import styles from './index.less';

/** 定义节点卡片组件 */
const NodeDrawer: React.FC<{
  visible: boolean;
  selectedNode: Node<NodeData> | null;
  onClose: () => void;
  graphType?: 'ONTOLOGY' | 'ENTITY';
  graphId?: string;
  onNodeDeleted?: (nodeId: string) => void;
  onDataChange?: () => void;
  readonly?: boolean;
}> = ({
  visible,
  selectedNode,
  onClose,
  graphType,
  graphId,
  onNodeDeleted,
  onDataChange,
  readonly,
}) => {
  const [form] = Form.useForm();
  const [properties, setProperties] = useState<Record<string, any>>({});
  const { modal, message } = App.useApp();
  const [deleteLoading, setDeleteLoading] = useState(false);

  /** 获取节点详细信息 */
  const {
    data: nodeInfo,
    loading,
    run: fetchNodeInfo,
  } = useRequest(getNodeInfo, {
    manual: true,
    onSuccess: (res) => {
      if (res) {
        try {
          const props = res.propertyJson ? JSON.parse(res.propertyJson) : {};
          setProperties(props);
        } catch (error) {
          setProperties({});
        }
      }
    },
    formatResult: (res) => res.data,
  });

  const { run: runEditNode, loading: saveLoading } = useRequest(editNode, {
    manual: true,
    onSuccess: () => {
      message.success('保存成功');
      // 刷新数据
      if (graphType) {
        fetchNodeInfo({
          nodeId: String(selectedNode?.data?.properties?._id),
          graphType: graphType!,
        });
      }
      onDataChange?.();
      onClose();
    },
  });

  /** 监听选中节点变化，触发请求 */
  useEffect(() => {
    if (visible && selectedNode?.data?.properties._id && graphType) {
      fetchNodeInfo({
        nodeId: String(selectedNode.data.properties._id),
        graphType: graphType!,
      });
    } else {
      setProperties({});
    }
  }, [visible, selectedNode, graphType, fetchNodeInfo]);

  /** 获取属性字段配置 */
  const getPropertyFieldConfig = useCallback((key: string) => {
    /** 对于id字段，禁用编辑 */
    return { label: key, disabled: key === 'id' };
  }, []);

  /** 获取所有属性字段 */
  const propertyFields = useMemo(() => {
    return Object.keys(properties);
  }, [properties]);

  const labels = useMemo(() => {
    if (!nodeInfo) return [];
    if (graphType === 'ENTITY') {
      return nodeInfo.linkOntologyName ? [nodeInfo.linkOntologyName] : [];
    }
    return nodeInfo.name ? [nodeInfo.name] : [];
  }, [nodeInfo, graphType]);

  const propertySchemaMap = useMemo(() => {
    if (!nodeInfo?.propertySchemaJson) return {};
    try {
      const schema =
        typeof nodeInfo.propertySchemaJson === 'string'
          ? JSON.parse(nodeInfo.propertySchemaJson)
          : nodeInfo.propertySchemaJson;

      if (Array.isArray(schema)) {
        return schema.reduce((acc, item) => {
          if (item?.name) {
            acc[item.name] = item;
          }
          return acc;
        }, {} as Record<string, any>);
      }
      return {};
    } catch (error) {
      return {};
    }
  }, [nodeInfo]);

  const propertySchemaList = useMemo(() => {
    if (!nodeInfo?.propertySchemaJson) return [];
    try {
      const schema =
        typeof nodeInfo.propertySchemaJson === 'string'
          ? JSON.parse(nodeInfo.propertySchemaJson)
          : nodeInfo.propertySchemaJson;
      return Array.isArray(schema) ? schema : [];
    } catch (error) {
      return [];
    }
  }, [nodeInfo]);

  /** 组件挂载时设置表单值 */
  useEffect(() => {
    if (nodeInfo) {
      form.setFieldsValue({
        name: nodeInfo.name ?? '',
        description: nodeInfo.description ?? '',
        labels: labels,
        properties: properties,
        propertySchemaList: propertySchemaList,
      });
    }
  }, [nodeInfo, properties, form, graphType, labels, propertySchemaList]);

  const formatPropertyValue = useCallback((value: unknown) => {
    if (value === null || value === undefined) return '';
    if (typeof value === 'object') {
      try {
        return JSON.stringify(value, null, 2);
      } catch (error) {
        return String(value);
      }
    }
    return String(value);
  }, []);

  /** 创建属性名称重复校验器 */
  const createDuplicateNameValidator = useCallback(
    (currentIndex: number) => ({
      validator: (_: any, value: string) => {
        if (!value) return Promise.resolve();

        const properties = form.getFieldValue('propertySchemaList') || [];
        const duplicateCount = properties.filter(
          (prop: any, index: number) =>
            index !== currentIndex &&
            prop?.name?.toLowerCase() === value.toLowerCase(),
        ).length;

        if (duplicateCount > 0) {
          return Promise.reject('属性名已存在');
        }
        return Promise.resolve();
      },
    }),
    [form],
  );

  /** 保存节点信息 */
  const handleSave = useCallback(async () => {
    if (!selectedNode?.data?.properties?._id || !graphType) {
      message.error('无法保存：缺少必要信息');
      return;
    }

    try {
      const values = await form.validateFields();

      const { name, description, propertySchemaList } = values;

      const newProperties: Record<string, any> = {};
      const currentProps = values.properties || {};

      Object.keys(currentProps).forEach((key) => {
        const value = currentProps[key];
        const schemaItem = propertySchemaMap[key];
        // 如果是数字类型，尝试转换为数字
        if (schemaItem?.type === 'NUMBER') {
          const numValue = Number(value);
          if (!isNaN(numValue)) {
            newProperties[key] = numValue;
            return;
          }
        }
        // 尝试解析JSON字符串，如果是对象或数组则保存为对象，否则保存为字符串
        if (typeof value === 'string') {
          try {
            const parsed = JSON.parse(value);
            if (typeof parsed === 'object' && parsed !== null) {
              newProperties[key] = parsed;
            } else {
              newProperties[key] = value;
            }
          } catch {
            newProperties[key] = value;
          }
        } else {
          newProperties[key] = value;
        }
      });

      // 同步更新 name 和 description 到 propertyJson
      newProperties.name = name;
      newProperties.description = description;

      const payload: any = {
        id: String(selectedNode.data.properties._id),
        graphType,
        graphId,
        name,
        description,
      };

      if (graphType === 'ENTITY') {
        payload.propertyJson = JSON.stringify(newProperties);
        payload.propertySchemaJson = nodeInfo?.propertySchemaJson;
        payload.linkOntologyId = nodeInfo?.linkOntologyId;
      } else {
        payload.propertySchemaJson = propertySchemaList
          ? propertySchemaList
          : nodeInfo?.propertySchemaJson;
      }

      runEditNode(payload);
    } catch (error) {
      console.error(error);
    }
  }, [selectedNode, graphType, graphId, form, message, runEditNode, nodeInfo]);

  /** 删除节点 */
  const handleDelete = useCallback(() => {
    if (!selectedNode?.data?.properties?._id || !graphType) {
      message.error('无法删除：缺少必要信息');
      return;
    }

    modal.confirm({
      title: '确认删除',
      content:
        '将删除当前节点。若它仍有关联关系，系统会拒绝删除；请先单独删除这些关系。删除后不可恢复。',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          setDeleteLoading(true);
          await deleteNode({
            idList: [String(selectedNode.data.properties._id)],
            graphType,
            graphId,
          });
          message.success('删除成功');
          onNodeDeleted?.(selectedNode.id);
          onDataChange?.();
          onClose();
        } catch (error) {
          // console.error(error);
        } finally {
          setDeleteLoading(false);
        }
      },
    });
  }, [
    selectedNode,
    graphType,
    graphId,
    modal,
    message,
    onNodeDeleted,
    onClose,
  ]);

  if (!visible || !selectedNode) {
    return null;
  }
  return (
    <Card
      title={
        <Space>
          <span>节点属性</span>
          {graphType && (
            <Tag color={graphType === 'ONTOLOGY' ? 'blue' : 'green'}>
              {graphType === 'ONTOLOGY' ? '本体' : '实体'}
            </Tag>
          )}
        </Space>
      }
      size="small"
      className={styles.card}
      extra={
        <Button
          type="text"
          aria-label="关闭节点属性"
          icon={<CloseOutlined aria-hidden="true" />}
          onClick={onClose}
        />
      }
    >
      <Spin spinning={loading}>
        <Form
          form={form}
          layout="vertical"
          size="small"
          className={styles.form}
        >
          <Form.Item name="labels">
            <Space wrap size="small">
              {labels.map((label) => (
                <Tooltip key={label} title={label}>
                  <Tag className={styles.labelButton}>{label}</Tag>
                </Tooltip>
              ))}
            </Space>
          </Form.Item>
          <Form.Item name="name" label="名称" style={{ fontWeight: 'bold' }}>
            <Input size="small" disabled={graphType === 'ONTOLOGY'} />
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
            style={{ fontWeight: 'bold' }}
          >
            <Input.TextArea
              size="small"
              disabled={readonly}
              autoSize={{ minRows: 2, maxRows: 6 }}
              maxLength={500}
            />
          </Form.Item>

          {/* 系统标识只保存在表单数据中，不要求用户查看或编辑。 */}
          {Object.prototype.hasOwnProperty.call(properties, 'id') ? (
            <Form.Item name={['properties', 'id']} hidden>
              <Input />
            </Form.Item>
          ) : null}
          {Object.prototype.hasOwnProperty.call(properties, '_id') ? (
            <Form.Item name={['properties', '_id']} hidden>
              <Input />
            </Form.Item>
          ) : null}
          {/* 动态渲染属性字段 */}
          {graphType === 'ENTITY' &&
            propertyFields
              .filter(
                (key) => !['name', 'description', 'id', '_id'].includes(key),
              )
              .map((key) => {
                const config = getPropertyFieldConfig(key);
                const schemaItem = propertySchemaMap[key];
                const isBoolean = schemaItem?.type === 'BOOLEAN';
                return (
                  <Form.Item
                    key={key}
                    style={{ fontWeight: 'bold' }}
                    name={['properties', key]}
                    label={config.label}
                    getValueProps={(value) => {
                      if (isBoolean) {
                        if (value === 'true') return { value: true };
                        if (value === 'false') return { value: false };
                        return { value };
                      }
                      return {
                        value: formatPropertyValue(value),
                      };
                    }}
                  >
                    {isBoolean ? (
                      <Select
                        size="small"
                        disabled={config.disabled || readonly}
                        options={[
                          { label: '是', value: true },
                          { label: '否', value: false },
                        ]}
                      />
                    ) : (
                      <Input.TextArea
                        size="small"
                        disabled={config.disabled || readonly}
                        autoSize={{ minRows: 1, maxRows: 6 }}
                        maxLength={500}
                      />
                    )}
                  </Form.Item>
                );
              })}

          {graphType === 'ONTOLOGY' && propertySchemaList.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Row gutter={8} style={{ fontWeight: 'bold', marginBottom: 8 }}>
                <Col span={6}>属性名称</Col>
                <Col span={6}>属性类型</Col>
                <Col span={8}>属性描述</Col>
                <Col span={4}> </Col>
              </Row>
              <Form.List name="propertySchemaList">
                {(fields) =>
                  fields.map(({ key, name, ...restField }) => {
                    // 获取当前字段的值
                    const fieldValues = form.getFieldValue([
                      'propertySchemaList',
                      name,
                    ]);
                    const fieldName = fieldValues?.name;

                    // 判断是否禁用
                    const isNameOrDesc =
                      fieldName === 'name' || fieldName === 'description';
                    const isName = fieldName === 'name';

                    return (
                      <Row key={key} gutter={8} style={{ marginBottom: 8 }}>
                        <Col span={6}>
                          <Form.Item
                            {...restField}
                            name={[name, 'name']}
                            style={{ marginBottom: 0 }}
                            rules={[
                              { required: true, message: '请输入属性名称' },
                              /** 名称校验 */
                              validateGraphName(),
                              {
                                validator: (_, value) => {
                                  if (value && value.toLowerCase() === '_id') {
                                    return Promise.reject('名称禁用"_id"');
                                  }
                                  return Promise.resolve();
                                },
                              },
                              /** 属性名称重复校验 */
                              createDuplicateNameValidator(name),
                            ]}
                          >
                            <Input
                              size="small"
                              disabled={readonly}
                              maxLength={500}
                              placeholder="属性名称"
                            />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item
                            {...restField}
                            name={[name, 'type']}
                            style={{ marginBottom: 0 }}
                            rules={[
                              { required: true, message: '请选择属性类型' },
                            ]}
                          >
                            <Select
                              size="small"
                              disabled={readonly || isNameOrDesc}
                              style={{ width: '100%' }}
                              options={[
                                { label: '字符串', value: 'STRING' },
                                { label: '数字', value: 'NUMBER' },
                                { label: '布尔', value: 'BOOLEAN' },
                                { label: '对象', value: 'OBJECT' },
                              ]}
                              placeholder="属性类型"
                            />
                          </Form.Item>
                        </Col>
                        <Col span={8}>
                          <Form.Item
                            {...restField}
                            name={[name, 'description']}
                            style={{ marginBottom: 0 }}
                          >
                            <Input
                              size="small"
                              disabled={readonly || isName}
                              placeholder="属性描述"
                              maxLength={500}
                            />
                          </Form.Item>
                        </Col>
                        <Col
                          span={4}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Form.Item
                            {...restField}
                            name={[name, 'required']}
                            valuePropName="checked"
                            style={{ marginBottom: 0 }}
                            getValueProps={(value) => ({
                              checked: value === 'REQUIRED',
                            })}
                            normalize={(value) =>
                              value ? 'REQUIRED' : 'NOT_REQUIRED'
                            }
                          >
                            <Checkbox disabled={readonly || isNameOrDesc}>
                              必填
                            </Checkbox>
                          </Form.Item>
                        </Col>
                      </Row>
                    );
                  })
                }
              </Form.List>
            </div>
          )}
        </Form>
      </Spin>
      {!readonly ? (
        <div className={styles.footer}>
          <Space>
            <Button
              danger
              size="small"
              onClick={handleDelete}
              loading={deleteLoading}
            >
              删除
            </Button>
            <Button
              type="primary"
              size="small"
              onClick={handleSave}
              loading={saveLoading}
            >
              保存
            </Button>
          </Space>
        </div>
      ) : null}
    </Card>
  );
};

export default React.memo(NodeDrawer);
