import { editNode, getNodeInfo, saveNode } from '@/services_v1/nodeService';
import { NodeVO, PropertySchema } from '@/services_v1/typings';
import { validateDescription, validateGraphName } from '@/utils/validator';
import { PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Spin,
} from 'antd';
import React, { useEffect, useState } from 'react';

/** 属性类型选项 */
const PROPERTY_TYPES = [
  { label: '字符串', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: '对象', value: 'OBJECT' },
] as const;

/** 属性列表表头配置 */
const PROPERTY_HEADER_COLUMNS = [
  { span: 5, label: '属性名称', offset: 16 },
  { span: 5, label: '属性类型', offset: 16 },
  { span: 6, label: '属性描述', offset: 16 },
] as const;

/**
 * 编辑本体对象
 * @param visible 是否显示
 * @param onCancel 取消回调
 * @param atlasId 图谱id
 * @param currentOnto 当前本体对象
 * @param refreshOntoList 刷新本体对象列表
 */
const EditModal: React.FC<{
  visible: boolean;
  onCancel: () => void;
  atlasId: string;
  currentOnto: NodeVO | null;
  refreshOntoList: () => void;
}> = ({ visible, onCancel, atlasId, currentOnto, refreshOntoList }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState<boolean>(false);
  const [ontologyInfoLoading, setOntologyInfoLoading] =
    useState<boolean>(false);
  /** 判断是否为编辑模式 */
  const isEdit = !!currentOnto;

  /** 同步表单字段值到属性描述（基于索引位置而非属性名称） */
  const syncFormFieldToPropertyDescription = (
    fieldName: 'name' | 'description',
    fieldValue: string,
  ) => {
    const properties = form.getFieldValue('properties') || [];
    // 索引0对应name，索引1对应description
    const targetIndex = fieldName === 'name' ? 0 : 1;
    if (properties.length > targetIndex) {
      const newProperties = [...properties];
      newProperties[targetIndex].description = fieldValue ?? '';
      form.setFieldValue('properties', newProperties);
    }
  };

  /** 反向同步：从属性描述同步到表单字段（基于索引位置而非属性名称） */
  const syncPropertyDescriptionToFormField = (
    propertyIndex: number,
    propertyValue: string,
  ) => {
    // 索引0对应name，索引1对应description
    if (propertyIndex === 0) {
      form.setFieldValue('entityName', propertyValue);
    } else if (propertyIndex === 1) {
      form.setFieldValue('entityDesc', propertyValue);
    }
  };

  /** 加载本体对象详细信息 */
  const loadOntologyInfo = async (nodeId: string) => {
    setOntologyInfoLoading(true);
    try {
      const response = await getNodeInfo({
        nodeId,
        graphType: 'ONTOLOGY',
      });
      if (response?.success && response?.data) {
        const nodeData = response.data;
        // 转换属性数据格式
        const properties = (nodeData.propertySchemaJson || []).map(
          (prop: any) => ({
            name: prop.name,
            type: prop.type,
            description: prop.description || '',
            required: prop.required === 'REQUIRED',
          }),
        );
        // 回填表单数据
        form.setFieldsValue({
          entityId: nodeData.id,
          entityName: nodeData.name,
          entityDesc: nodeData.description || '',
          properties,
        });
      }
    } catch (error: any) {
      // 错误由全局错误处理器处理
    } finally {
      setOntologyInfoLoading(false);
    }
  };

  /** 创建属性名称重复校验器 */
  const createDuplicateNameValidator = (currentIndex: number) => ({
    validator: (_: any, value: string) => {
      if (!value) return Promise.resolve();

      const properties = form.getFieldValue('properties') || [];
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
  });

  useEffect(() => {
    if (visible) {
      form.resetFields();
      if (isEdit && currentOnto && currentOnto.id) {
        /** 编辑模式：从接口获取数据并回填 */
        loadOntologyInfo(currentOnto.id);
      } else {
        /** 新建模式，初始化默认属性 */
        form.setFieldsValue({
          properties: [
            {
              name: 'name',
              type: 'STRING',
              description: '',
              required: true,
            },
            {
              name: 'description',
              type: 'STRING',
              description: '',
              required: false,
            },
          ],
        });
      }
    }
  }, [currentOnto, visible, form, isEdit]);

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  /** 属性名称变更处理 */
  const handleEntityNameChange = (e: any) => {
    const value = e.target.value?.trim() ?? '';
    syncFormFieldToPropertyDescription('name', value);
  };

  /** 属性描述变更处理 */
  const handleEntityDescChange = (e: any) => {
    const value = e.target.value?.trim() ?? '';
    syncFormFieldToPropertyDescription('description', value);
  };

  /** 属性描述变更处理（属性列表） */
  const handlePropertyDescChange = (propertyIndex: number) => (e: any) => {
    const value = e.target.value?.trim() ?? '';
    // 前两个属性（索引0和1）是默认属性，需要反向同步到对应的表单字段
    if (propertyIndex === 0 || propertyIndex === 1) {
      syncPropertyDescriptionToFormField(propertyIndex, value);
    }
  };

  /** 保存本体对象 */
  const handleSave = () => {
    form.validateFields().then(async (values) => {
      setLoading(true);
      try {
        // 转换属性数据格式
        const propertySchemaJson: PropertySchema[] = (
          values.properties || []
        ).map((prop: any) => ({
          name: prop.name,
          type: prop.type,
          description: prop.description || '',
          required: prop.required ? 'REQUIRED' : 'NOT_REQUIRED',
        }));

        let response;
        if (isEdit) {
          // 编辑模式：调用编辑接口
          response = await editNode({
            id: values.entityId,
            name: values.entityName,
            description: values.entityDesc || '',
            propertySchemaJson,
            graphId: atlasId,
            graphType: 'ONTOLOGY',
          });
        } else {
          // 新建模式：调用新建接口
          response = await saveNode({
            name: values.entityName,
            description: values.entityDesc || '',
            propertySchemaJson,
            graphId: atlasId,
            graphType: 'ONTOLOGY',
          });
        }
        if (response?.success) {
          message.success(isEdit ? '本体对象编辑成功' : '本体对象创建成功');
          handleCancel();
          refreshOntoList();
        }
      } catch (error: any) {
        // 错误由全局错误处理器处理
      } finally {
        setLoading(false);
      }
    });
  };

  return (
    <Modal
      title={isEdit ? '编辑本体对象' : '新建本体对象'}
      open={visible}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          取消
        </Button>,
        <Button
          key="save"
          type="primary"
          onClick={handleSave}
          loading={loading}
          disabled={ontologyInfoLoading}
        >
          保存
        </Button>,
      ]}
      width={600}
    >
      <Spin spinning={ontologyInfoLoading} tip="加载中...">
        <Form form={form} layout="horizontal">
          <Form.Item name="entityId" hidden>
            <Input />
          </Form.Item>

          <Form.Item
            name="entityName"
            label="本体对象名称"
            rules={[
              { required: true, message: '请输入本体对象名称' },
              /** 名称校验 */
              validateGraphName(),
            ]}
            labelCol={{ span: 8 }}
            wrapperCol={{ span: 12 }}
            normalize={(value) => value?.trim()}
          >
            <Input
              placeholder="请输入本体对象名称"
              maxLength={100}
              onChange={handleEntityNameChange}
              disabled={isEdit}
            />
          </Form.Item>

          <Form.Item
            name="entityDesc"
            label="本体对象描述"
            rules={[{ message: '请输入本体对象描述' }, validateDescription()]}
            labelCol={{ span: 8 }}
            wrapperCol={{ span: 12 }}
          >
            <Input.TextArea
              placeholder="请输入本体对象描述"
              maxLength={500}
              onChange={handleEntityDescChange}
            />
          </Form.Item>

          {/* 属性列表表头 */}
          <Row style={{ marginBottom: 8, marginLeft: 24 }}>
            {PROPERTY_HEADER_COLUMNS.map(({ span, label, offset }) => (
              <Col key={label} span={span}>
                <div style={{ marginLeft: offset, fontWeight: 'bold' }}>
                  {label}
                </div>
              </Col>
            ))}
          </Row>

          {/* 属性列表 */}
          <Form.List name="properties">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Form.Item
                    key={key}
                    noStyle
                    shouldUpdate={(prevValues, curValues) =>
                      prevValues.properties !== curValues.properties
                    }
                  >
                    {() => {
                      const currentProperty = form.getFieldValue([
                        'properties',
                        name,
                      ]);
                      // 前两个属性（name和description）是默认属性，无论新建还是编辑模式都需要禁用
                      // 仅通过索引位置判断，确保即使属性名称被清空也保持禁用状态
                      const isDefaultProperty = name < 2;

                      return (
                        <Row gutter={8} style={{ marginLeft: 28 }}>
                          <Col span={5}>
                            <Form.Item
                              {...restField}
                              name={[name, 'name']}
                              rules={[
                                { required: true, message: '请输入属性名称' },
                                /** 名称校验 */
                                validateGraphName(),
                                {
                                  validator: (_, value) => {
                                    if (
                                      value &&
                                      value.toLowerCase() === '_id'
                                    ) {
                                      return Promise.reject('名称禁用"_id"');
                                    }
                                    return Promise.resolve();
                                  },
                                },
                                /** 属性名称重复校验 */
                                createDuplicateNameValidator(name),
                              ]}
                              normalize={(value) => value?.trim()}
                              validateFirst
                            >
                              <Input
                                placeholder="属性名称"
                                maxLength={100}
                                // disabled={isDefaultProperty}
                              />
                            </Form.Item>
                          </Col>
                          <Col span={5}>
                            <Form.Item
                              {...restField}
                              name={[name, 'type']}
                              rules={[
                                { required: true, message: '请选择属性类型' },
                              ]}
                            >
                              <Select
                                placeholder="选择类型"
                                disabled={isDefaultProperty}
                              >
                                {PROPERTY_TYPES.map(({ label, value }) => (
                                  <Select.Option key={value} value={value}>
                                    {label}
                                  </Select.Option>
                                ))}
                              </Select>
                            </Form.Item>
                          </Col>
                          <Col span={6}>
                            <Form.Item
                              {...restField}
                              name={[name, 'description']}
                              rules={[
                                { message: '请输入属性描述' },
                                validateDescription(),
                              ]}
                            >
                              <Input
                                placeholder="属性描述"
                                maxLength={500}
                                onChange={handlePropertyDescChange(name)}
                                disabled={isEdit && name === 0}
                              />
                            </Form.Item>
                          </Col>
                          <Col
                            span={4}
                            style={{ textAlign: 'center', marginTop: 6 }}
                          >
                            <Form.Item
                              {...restField}
                              name={[name, 'required']}
                              valuePropName="checked"
                              initialValue={false}
                              noStyle
                            >
                              <Checkbox disabled={isDefaultProperty} />
                            </Form.Item>
                            <span style={{ marginLeft: 6 }}>必填</span>
                          </Col>
                          <Col span={2} style={{ textAlign: 'center' }}>
                            <Button
                              type="text"
                              onClick={() => remove(name)}
                              style={{
                                color: isDefaultProperty ? undefined : 'red',
                              }}
                              disabled={isDefaultProperty}
                            >
                              删除
                            </Button>
                          </Col>
                        </Row>
                      );
                    }}
                  </Form.Item>
                ))}
                <Form.Item style={{ marginTop: -10, marginLeft: 20 }}>
                  <Button
                    type="link"
                    onClick={() =>
                      add({
                        name: '',
                        type: 'STRING',
                        description: '',
                        required: false,
                      })
                    }
                    icon={<PlusOutlined />}
                  >
                    新建属性
                  </Button>
                </Form.Item>
              </>
            )}
          </Form.List>
        </Form>
      </Spin>
    </Modal>
  );
};

export default EditModal;
