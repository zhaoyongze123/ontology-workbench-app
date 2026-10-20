import { validateGraphName } from '@/utils/validator';
import { CloseOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
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
  deleteRelationship,
  editRelationship,
  getRelationshipInfo,
  getRelationshipOptional,
} from '../../../../services_v1/relationshipService';
import { OptionalVO } from '../../../../services_v1/typings';
import styles from './index.less';

/** 边卡片组件 */
const EdgeDrawer: React.FC<{
  visible: boolean;
  selectedEdge: any | null;
  onClose: () => void;
  graphType?: 'ONTOLOGY' | 'ENTITY';
  graphId?: string;
  onEdgeDeleted?: (edgeId: string) => void;
  onDataChange?: () => void;
  readonly?: boolean;
}> = ({
  visible,
  selectedEdge,
  onClose,
  graphType,
  graphId,
  onEdgeDeleted,
  onDataChange,
  readonly,
}) => {
  const [form] = Form.useForm();
  const [properties, setProperties] = useState<Record<string, any>>({});
  const { modal, message } = App.useApp();
  const [deleteLoading, setDeleteLoading] = useState(false);

  /** 获取关系可选列表 */
  const {
    data: relationshipOptions,
    loading: optionsLoading,
    run: fetchRelationshipOptions,
  } = useRequest(
    async () => {
      if (graphId && graphType) {
        return await getRelationshipOptional({ graphId, graphType });
      }
      return Promise.resolve({ data: [] });
    },
    {
      manual: true,
      formatResult: (res) => res.data || [],
    },
  );

  /** 获取关系详细信息 */
  const {
    data: relationshipInfo,
    loading,
    run: fetchRelationshipInfo,
  } = useRequest(getRelationshipInfo, {
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

  const { run: runEditRelationship, loading: saveLoading } = useRequest(
    editRelationship,
    {
      manual: true,
      onSuccess: () => {
        message.success('保存成功');
        // 刷新数据
        if (graphType) {
          fetchRelationshipInfo({
            relationshipId: String(selectedEdge?.data?.properties?._id),
            graphType: graphType!,
          });
        }
        onDataChange?.();
        onClose();
      },
    },
  );

  /** 监听选中关系变化，触发请求 */
  useEffect(() => {
    if (visible && selectedEdge?.data?.properties?._id && graphType) {
      fetchRelationshipInfo({
        relationshipId: String(selectedEdge?.data?.properties._id),
        graphType,
      });
    } else {
      setProperties({});
    }
  }, [visible, selectedEdge, graphType, fetchRelationshipInfo]);

  /** 获取属性字段配置 */
  const getPropertyFieldConfig = useCallback((key: string) => {
    /** 对于id字段，禁用编辑 */
    return { label: key, disabled: key === 'id' };
  }, []);

  /** 获取所有属性字段 */
  const propertyFields = useMemo(() => {
    return Object.keys(properties);
  }, [properties]);

  const propertySchemaMap = useMemo(() => {
    if (!relationshipInfo?.propertySchemaJson) return {};
    try {
      const schema =
        typeof relationshipInfo.propertySchemaJson === 'string'
          ? JSON.parse(relationshipInfo.propertySchemaJson)
          : relationshipInfo.propertySchemaJson;

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
  }, [relationshipInfo]);

  const propertySchemaList = useMemo(() => {
    if (!relationshipInfo?.propertySchemaJson) return [];
    try {
      const schema =
        typeof relationshipInfo.propertySchemaJson === 'string'
          ? JSON.parse(relationshipInfo.propertySchemaJson)
          : relationshipInfo.propertySchemaJson;
      return Array.isArray(schema) ? schema : [];
    } catch (error) {
      return [];
    }
  }, [relationshipInfo]);

  /** 组件挂载时设置表单值 */
  useEffect(() => {
    if (relationshipInfo) {
      form.setFieldsValue({
        id: relationshipInfo.id ?? '',
        labels: relationshipInfo.name ? [relationshipInfo.name] : [],
        label: relationshipInfo.name ?? '',
        description: relationshipInfo.description ?? '',
        properties: properties,
        propertySchemaList: propertySchemaList,
      });
    }
  }, [relationshipInfo, properties, form, propertySchemaList]);

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

  /** 保存关系 */
  const handleSave = useCallback(async () => {
    if (!selectedEdge?.data?.properties?._id || !graphType) {
      message.error('无法保存：缺少必要信息');
      return;
    }

    try {
      const values = await form.validateFields();
      const { description, label, propertySchemaList } = values;

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

      const payload: any = {
        id: String(selectedEdge.data.properties._id),
        graphType,
        graphId,
        name: label,
        description,
        sourceNodeId: relationshipInfo?.sourceNodeId,
        targetNodeId: relationshipInfo?.targetNodeId,
        linkOntologyRelationshipId: relationshipInfo?.linkOntologyId,
      };

      if (graphType === 'ENTITY') {
        payload.propertyJson = JSON.stringify(newProperties);
        payload.propertySchemaJson = relationshipInfo?.propertySchemaJson;
      } else {
        payload.propertySchemaJson = propertySchemaList
          ? propertySchemaList
          : relationshipInfo?.propertySchemaJson;
      }

      runEditRelationship(payload);
    } catch (error) {
      console.error(error);
    }
  }, [
    selectedEdge,
    graphType,
    graphId,
    form,
    message,
    runEditRelationship,
    relationshipInfo,
  ]);

  /** 删除关系 */
  const handleDelete = useCallback(() => {
    if (!selectedEdge?.data?.properties?._id || !graphType) {
      message.error('无法删除：缺少必要信息');
      return;
    }

    modal.confirm({
      title: '确认删除',
      content: '确定要删除该关系吗？此操作不可恢复。',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          setDeleteLoading(true);
          await deleteRelationship({
            idList: [String(selectedEdge.data.properties._id)],
            graphType,
            graphId,
          });
          message.success('删除成功');
          onEdgeDeleted?.(selectedEdge.id);
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
    selectedEdge,
    graphType,
    graphId,
    modal,
    message,
    onEdgeDeleted,
    onClose,
    onDataChange,
  ]);

  if (!visible || !selectedEdge) {
    return null;
  }

  return (
    <Card
      title={
        <Space>
          <span>Relationship properties</span>
          {graphType && (
            <Tag color={graphType === 'ONTOLOGY' ? 'blue' : 'green'}>
              {graphType === 'ONTOLOGY' ? '本体' : '实体'}
            </Tag>
          )}
        </Space>
      }
      size="small"
      className={styles.card}
      extra={<Button type="text" icon={<CloseOutlined />} onClick={onClose} />}
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
              {(relationshipInfo?.name
                ? [relationshipInfo.name]
                : selectedEdge?.data?.labels ?? [selectedEdge.label]
              )
                .filter(Boolean)
                .map((label: string) => (
                  <Tooltip key={label} title={label}>
                    <Tag className={styles.labelButton}>{label}</Tag>
                  </Tooltip>
                ))}
            </Space>
          </Form.Item>
          <Form.Item name="id" label="id" style={{ fontWeight: 'bold' }}>
            <Input disabled size="small" />
          </Form.Item>
          <Form.Item
            name="label"
            label="关系名称"
            style={{ fontWeight: 'bold' }}
          >
            {graphType === 'ENTITY' ? (
              <Select
                size="small"
                disabled={readonly}
                loading={optionsLoading}
                onOpenChange={(open) => {
                  if (open) {
                    fetchRelationshipOptions();
                  }
                }}
                options={relationshipOptions?.map((item: OptionalVO) => ({
                  label: item.name,
                  value: item.name,
                }))}
              />
            ) : (
              <Input size="small" disabled={readonly} />
            )}
          </Form.Item>
          <Form.Item
            name="description"
            label="关系描述"
            style={{ fontWeight: 'bold' }}
          >
            <Input.TextArea
              size="small"
              disabled={readonly}
              autoSize={{ minRows: 2, maxRows: 6 }}
              maxLength={500}
            />
          </Form.Item>

          {/* 动态渲染属性字段 */}
          {graphType === 'ENTITY' &&
            propertyFields.map((key) => {
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
                        { label: 'true', value: true },
                        { label: 'false', value: false },
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
                    // const fieldName = fieldValues?.name;

                    // 判断是否禁用
                    // const isNameOrDesc =
                    //   fieldName === 'name' || fieldName === 'description';
                    // const isName = fieldName === 'name';

                    return (
                      <Row key={key} gutter={8} style={{ marginBottom: 8 }}>
                        <Col span={6}>
                          {/* <Form.Item
                            {...restField}
                            name={[name, 'required']}
                            hidden
                            initialValue="NOT_REQUIRED"
                          >
                            <Input />
                          </Form.Item> */}
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
                          >
                            <Select
                              size="small"
                              // disabled={readonly || isNameOrDesc}
                              disabled={readonly}
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
                              // disabled={readonly || isName}
                              disabled={readonly}
                              maxLength={500}
                              placeholder="属性描述"
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
                            <Checkbox
                              // disabled={readonly || isNameOrDesc}
                              disabled={readonly}
                            >
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
            disabled={readonly}
          >
            保存
          </Button>
        </Space>
      </div>
    </Card>
  );
};

export default React.memo(EdgeDrawer);
