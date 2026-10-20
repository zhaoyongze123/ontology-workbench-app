import { getNodeOptional } from '@/services_v1/nodeService';
import {
  editRelationship,
  getRelationshipInfo,
  saveRelationship,
} from '@/services_v1/relationshipService';
import { ApiResultRelationshipVO, RelationshipVO } from '@/services_v1/typings';
import {
  validateDescription,
  validateGraphName,
  validateRelationName,
} from '@/utils/validator';
import { PlusOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
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

const RelationEditModal: React.FC<{
  visible: boolean;
  currentRelation: RelationshipVO | null;
  onCancel: () => void;
  atlasId: string;
  refreshRelationList: () => void;
}> = ({ visible, currentRelation, onCancel, atlasId, refreshRelationList }) => {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const isEdit = !!currentRelation; /** 判断是否为编辑模式 */
  const [loading, setLoading] = useState(false);

  /** 获取本体选项列表 */
  const {
    data: ontologyData,
    loading: ontologyLoading,
    run: OntoOptionsRun,
  } = useRequest(getNodeOptional, {
    manual: true,
  });

  /** 获取本体关系详情 */
  const { run: getOntoRelateDetail, loading: relationInfoLoading } = useRequest(
    getRelationshipInfo,
    {
      manual: true,
      formatResult: (res) => res,
      onSuccess: (response: ApiResultRelationshipVO) => {
        if (response?.success && response?.data) {
          const data = response.data;
          form.setFieldsValue({
            sourceId: data.sourceNodeId ?? '',
            targetId: data.targetNodeId ?? '',
            relationName: data.name ?? '',
            relationDesc: data.description ?? '',
          });

          if (data.propertySchemaJson) {
            const formattedProperties = Array.isArray(data.propertySchemaJson)
              ? data.propertySchemaJson.map((prop: any) => ({
                  name: prop.name ?? '',
                  type: prop.type ?? '',
                  description: prop.description ?? '',
                  required: prop.required === 'REQUIRED',
                }))
              : [];
            form.setFieldsValue({
              properties: formattedProperties,
            });
          }
        }
      },
    },
  );

  /** 打开弹窗时请求本体关系详情 */
  useEffect(() => {
    /** 编辑时通过接口获取信息 */
    if (visible && isEdit && currentRelation) {
      /** 正确回填主本体和目标本体名称 */
      OntoOptionsRun({ graphId: atlasId, graphType: 'ONTOLOGY' });
      /** 获取本体关系详情 */
      if (currentRelation.id) {
        setLoading(true);
        getOntoRelateDetail({
          relationshipId: currentRelation.id ?? '',
          graphType: 'ONTOLOGY',
        }).finally(() => {
          setLoading(false);
        });
      }
    } else {
      /** 新建模式，重置表单 */
      form.resetFields();
      form.setFieldsValue({
        properties: [],
      });
    }
  }, [visible, currentRelation, isEdit, form]);

  /** 保存本体关系 */
  const handleSave = () => {
    form.validateFields().then(async (values) => {
      setLoading(true);
      try {
        const propertySchemaJson = values.properties
          ? values.properties.map((prop: any) => ({
              name: prop.name ?? '',
              type: prop.type ?? '',
              description: prop.description ?? '',
              required: prop.required ? 'REQUIRED' : 'NOT_REQUIRED',
            }))
          : [];
        const relationData: any = {
          graphId: atlasId,
          sourceNodeId: values.sourceId ?? '',
          targetNodeId: values.targetId ?? '',
          name: values.relationName ?? '',
          description: values.relationDesc ?? '',
          propertySchemaJson,
          graphType: 'ONTOLOGY',
        };
        if (isEdit) {
          relationData.id = currentRelation?.id ?? '';
        }
        const api = isEdit ? editRelationship : saveRelationship;
        const res = await api(relationData);
        if (res?.success) {
          message.success(`关系${isEdit ? '编辑' : '创建'}成功`);
          refreshRelationList();
          form.resetFields();
          onCancel();
        }
      } finally {
        setLoading(false);
      }
    });
  };

  return (
    <Modal
      title={isEdit ? '编辑本体关系' : '新建本体关系'}
      open={visible}
      onCancel={() => {
        form.resetFields();
        onCancel();
      }}
      footer={[
        <Button
          key="cancel"
          onClick={() => {
            form.resetFields();
            onCancel();
          }}
        >
          取消
        </Button>,
        <Button
          key="save"
          type="primary"
          onClick={handleSave}
          loading={loading}
        >
          保存
        </Button>,
      ]}
      width={600}
    >
      <Spin spinning={relationInfoLoading}>
        <Form form={form} layout="horizontal">
          <Form.Item
            name="sourceId"
            label="主本体名称"
            rules={[{ required: true, message: '请选择主本体名称' }]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Select
              placeholder="请选择主本体名称"
              options={ontologyData?.map((item: any) => ({
                value: item.id,
                label: item.name,
              }))}
              loading={ontologyLoading}
              showSearch
              filterOption={(input, option) =>
                (option?.label ?? '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
              onOpenChange={(open) => {
                if (open && atlasId) {
                  OntoOptionsRun({ graphId: atlasId, graphType: 'ONTOLOGY' });
                }
              }}
            />
          </Form.Item>

          <Form.Item
            name="targetId"
            label="目标本体名称"
            rules={[{ required: true, message: '请选择目标本体名称' }]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Select
              placeholder="请选择目标本体名称"
              options={ontologyData?.map((item: any) => ({
                value: item.id,
                label: item.name,
              }))}
              loading={ontologyLoading}
              showSearch
              filterOption={(input, option) =>
                (option?.label ?? '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
              onOpenChange={(open) => {
                if (open && atlasId) {
                  OntoOptionsRun({ graphId: atlasId, graphType: 'ONTOLOGY' });
                }
              }}
            />
          </Form.Item>

          <Form.Item
            name="relationName"
            label="本体关系名称"
            rules={[
              { required: true, message: '请输入本体关系名称' },
              validateRelationName(),
            ]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Input placeholder="请输入本体关系名称" maxLength={100} />
          </Form.Item>

          <Form.Item
            name="relationDesc"
            label="本体关系描述"
            rules={[
              { message: '请输入本体关系描述' },
              /** 描述限制 */
              validateDescription(),
            ]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Input.TextArea
              rows={3}
              placeholder="请输入本体关系描述"
              maxLength={500}
            />
          </Form.Item>

          <Row style={{ marginBottom: 8, marginLeft: 24 }}>
            <Col span={5}>
              <div style={{ marginLeft: 16, fontWeight: 'bold' }}>属性名称</div>
            </Col>
            <Col span={5}>
              <div style={{ marginLeft: 16, fontWeight: 'bold' }}>属性类型</div>
            </Col>
            <Col span={6}>
              <div style={{ marginLeft: 16, fontWeight: 'bold' }}>属性描述</div>
            </Col>
          </Row>

          <Form.List name="properties">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Row key={key} gutter={8} style={{ marginLeft: 28 }}>
                    <Col span={5}>
                      <Form.Item
                        {...restField}
                        name={[name, 'name']}
                        normalize={(value) => value?.trim()}
                        rules={[
                          { required: true, message: '请输入属性名称' },
                          /** 名称校验 */
                          validateGraphName(),
                          {
                            validator: (_, value) => {
                              if (value.toLowerCase() === 'id') {
                                return Promise.reject('名称禁用"id"');
                              }
                              return Promise.resolve();
                            },
                          },
                        ]}
                      >
                        <Input placeholder="属性名称" maxLength={100} />
                      </Form.Item>
                    </Col>
                    <Col span={5}>
                      <Form.Item
                        {...restField}
                        name={[name, 'type']}
                        rules={[{ required: true, message: '请选择属性类型' }]}
                      >
                        <Select placeholder="选择类型">
                          <Select.Option value="STRING">字符串</Select.Option>
                          <Select.Option value="NUMBER">数字</Select.Option>
                          <Select.Option value="BOOLEAN">布尔</Select.Option>
                          <Select.Option value="OBJECT">对象</Select.Option>
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
                        <Input placeholder="属性描述" maxLength={500} />
                      </Form.Item>
                    </Col>
                    <Col span={4} style={{ textAlign: 'center', marginTop: 6 }}>
                      <Form.Item
                        {...restField}
                        name={[name, 'required']}
                        valuePropName="checked"
                        initialValue={true}
                        noStyle
                      >
                        <Checkbox />
                      </Form.Item>
                      <span style={{ marginLeft: 6 }}>必填</span>
                    </Col>
                    <Col span={2} style={{ textAlign: 'center' }}>
                      <Button
                        type="text"
                        onClick={() => remove(name)}
                        style={{ color: 'red' }}
                      >
                        删除
                      </Button>
                    </Col>
                  </Row>
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

export default RelationEditModal;
