import { getLinkOntologyNodeList } from '@/services_v1/graphService';
import { editNode, getNodeInfo, saveNode } from '@/services_v1/nodeService';
import { NodeVO, PropertySchema } from '@/services_v1/typings';
import { GraphType } from '@/utils/typeEnum';
import {
  validateDescription,
  validateGraphName,
  validatePropertyValue,
} from '@/utils/validator';
import { useRequest } from '@umijs/max';
import { App, Button, Form, Input, Modal, Select, Spin } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import PropertyTable from '../PropertyTable';

/** 编辑实体对象弹窗 */
const EditModal: React.FC<{
  visible: boolean;
  onCancel: () => void;
  atlasId: string;
  currentEntity: NodeVO | null;
  refreshEntityList: () => void;
}> = ({ visible, onCancel, atlasId, currentEntity, refreshEntityList }) => {
  const [properties, setProperties] = useState<PropertySchema[]>([]);
  // 使用 state 管理 linkOntologyId 和 propertyJson
  const [linkOntologyId, setLinkOntologyId] = useState<string>('');
  const [propertyJson, setPropertyJson] = useState<Record<string, any>>({});

  const [form] = Form.useForm();
  const { message } = App.useApp();

  /** 判断是否为编辑模式 */
  const isEdit = useMemo(() => !!currentEntity, [currentEntity]);

  /** 同步表单字段值到属性JSON */
  const syncFormFieldToPropertyJson = useCallback(
    (fieldName: string, fieldValue: string) => {
      setPropertyJson((prev) => ({
        ...prev,
        [fieldName]: fieldValue,
      }));
    },
    [],
  );

  /** 反向同步：从属性JSON同步到表单字段 */
  const syncPropertyJsonToFormField = useCallback(
    (propertyName: string, propertyValue: string) => {
      if (propertyName === 'name') {
        form.setFieldValue('entityName', propertyValue);
      } else if (propertyName === 'description') {
        form.setFieldValue('entityDesc', propertyValue);
      }
    },
    [form],
  );

  /** 获取关联本体节点列表 */
  const {
    data: ontologyNodeList,
    loading: ontologyNodeListLoading,
    run: loadOntologyNodeList,
  } = useRequest(() => getLinkOntologyNodeList({ entityGraphId: atlasId }), {
    manual: true,
    onError: (error) => {
      message.error('获取关联本体列表失败');
    },
  });

  /** 本体选项列表 */
  const ontologyOptions = useMemo(() => {
    if (ontologyNodeList && Array.isArray(ontologyNodeList)) {
      return ontologyNodeList
        .filter((item: NodeVO) => item.id && item.name)
        .map((item: NodeVO) => ({
          label: item.name!,
          value: item.id!,
        }));
    }
    return [];
  }, [ontologyNodeList]);

  /** 获取实体节点详情数据 */
  const { loading: entityDetailLoading, run: loadEntityDetail } = useRequest(
    (nodeId: string) =>
      getNodeInfo({
        nodeId,
        graphType: GraphType.ENTITY,
      }),
    {
      manual: true,
      onSuccess: (nodeData) => {
        if (nodeData) {
          const parsedPropertyJson = nodeData.propertyJson
            ? JSON.parse(nodeData.propertyJson)
            : {};
          setProperties(nodeData.propertySchemaJson || []);
          setLinkOntologyId(nodeData.linkOntologyId || '');
          setPropertyJson(parsedPropertyJson);
          form.setFieldsValue({
            entityName: parsedPropertyJson.name || nodeData.name || '',
            entityDesc:
              parsedPropertyJson.description || nodeData.description || '',
            ontoName: nodeData.linkOntologyId,
          });
        }
      },
      onError: (error) => {
        message.error('获取实体对象信息失败');
      },
    },
  );

  /** 获取本体节点详情（获取属性模板） */
  const { loading: ontologyInfoLoading, run: loadOntologyNodeInfo } =
    useRequest(
      (nodeId: string) =>
        getNodeInfo({
          nodeId,
          graphType: GraphType.ONTOLOGY,
        }),
      {
        manual: true,
        onSuccess: (nodeData) => {
          if (nodeData) {
            const propertySchemaJson = nodeData.propertySchemaJson || [];
            setProperties(propertySchemaJson);
            setLinkOntologyId(nodeData.id || '');
            // 初始化propertyJson（仅在新建模式下）
            if (!isEdit) {
              const initialPropertyJson = propertySchemaJson.reduce(
                (acc: Record<string, any>, prop: PropertySchema) => {
                  if (prop.name) acc[prop.name] = '';
                  return acc;
                },
                {},
              );
              setPropertyJson(initialPropertyJson);
            }
          }
        },
        onError: (error) => {
          message.error('获取本体对象信息失败');
        },
      },
    );

  /** 打开弹窗时请求实体详情 */
  useEffect(() => {
    if (visible) {
      form.resetFields();
      setProperties([]);
      setLinkOntologyId('');
      setPropertyJson({});
      // 加载本体节点列表（编辑模式下需要显示本体名称）
      if (atlasId) loadOntologyNodeList();
      // 编辑模式下加载实体详情
      if (isEdit && currentEntity?.id) loadEntityDetail(currentEntity.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, isEdit, currentEntity?.id, atlasId]);

  const handleCancel = useCallback(() => {
    form.resetFields();
    onCancel();
  }, [form, onCancel]);

  /** 验证必填属性 */
  const validateRequiredProperties = useCallback(() => {
    const missingProps = properties.filter((prop) => {
      if (!prop.name || prop.required !== 'REQUIRED') return false;
      const value = propertyJson[prop.name];
      // 空值判断：空字符串、undefined、null 都视为未填写  但 '0' 和 'false' 是有效值
      return value === '' || value === undefined || value === null;
    });
    if (missingProps.length > 0) {
      const propNames = missingProps.map((p) => p.name).join('、');
      message.error(`请填写所有必填属性：${propNames}`);
      return false;
    }
    return true;
  }, [properties, propertyJson, message]);

  /** 验证属性值类型 */
  const validatePropertyTypes = useCallback(() => {
    const invalidProp = properties.find((prop) => {
      if (!prop.name || !prop.type) return false;
      const value = propertyJson[prop.name];
      // 空值不需要验证类型
      if (value === '' || value === undefined || value === null) return false;
      // 对于 OBJECT 类型，如果值是对象则先序列化
      let valueStr: string;
      if (prop.type === 'OBJECT' && typeof value === 'object') {
        try {
          valueStr = JSON.stringify(value);
        } catch (e) {
          return true; // 序列化失败，视为无效
        }
      } else {
        valueStr = String(value);
      }
      // 验证类型
      return !validatePropertyValue(valueStr, prop.type);
    });
    if (invalidProp) {
      const errorMsg =
        invalidProp.type === 'OBJECT'
          ? `属性 "${invalidProp.name}" 的JSON格式不正确，请检查`
          : `属性 "${invalidProp.name}" 的值类型不匹配，应为 ${invalidProp.type}`;
      message.error(errorMsg);
      return false;
    }
    return true;
  }, [properties, propertyJson, message]);

  /** 转换属性值为正确的类型 */
  const convertPropertyJsonTypes = useCallback(
    (propertyJson: Record<string, any>): Record<string, any> => {
      const converted: Record<string, any> = {};
      properties.forEach((prop) => {
        if (!prop.name) return;
        const value = propertyJson[prop.name];
        // 空值直接保留
        if (value === '' || value === undefined || value === null) {
          converted[prop.name] = value;
          return;
        }
        // 根据类型转换
        switch (prop.type) {
          case 'NUMBER':
            converted[prop.name] = Number(value);
            break;
          case 'BOOLEAN':
            converted[prop.name] = value === 'true' || value === true;
            break;
          case 'OBJECT':
            try {
              // 如果已经是对象，直接使用；否则尝试解析
              converted[prop.name] =
                typeof value === 'object' ? value : JSON.parse(value);
            } catch {
              // 解析失败保留原值（验证阶段会捕获错误）
              converted[prop.name] = value;
            }
            break;
          default:
            // STRING 类型直接保留
            converted[prop.name] = value;
        }
      });
      return converted;
    },
    [properties],
  );

  /** 保存实体对象 */
  const { loading: saveLoading, run: runSave } = useRequest(
    async (values: any) => {
      // 转换属性值为正确的类型
      const convertedPropertyJson = convertPropertyJsonTypes(propertyJson);

      const params = {
        name: values.entityName,
        description: values.entityDesc || '',
        linkOntologyId,
        propertySchemaJson: properties,
        propertyJson: JSON.stringify(convertedPropertyJson),
        graphId: atlasId,
        graphType: GraphType.ENTITY,
      };
      return isEdit && currentEntity?.id
        ? editNode({ ...params, id: currentEntity.id })
        : saveNode(params);
    },
    {
      manual: true,
      onSuccess: () => {
        message.success(isEdit ? '实体对象编辑成功' : '实体对象创建成功');
        refreshEntityList();
        handleCancel();
      },
      onError: (error: any) => {
        // const defaultMsg = isEdit ? '编辑失败' : '创建失败';
        // message.error(error?.message || defaultMsg);
      },
    },
  );

  /** 处理保存按钮点击 */
  const handleSave = useCallback(async () => {
    try {
      // 1. 验证表单字段
      const values = await form.validateFields();
      // 2. 验证必填属性
      if (!validateRequiredProperties()) return;
      // 3. 验证属性类型
      if (!validatePropertyTypes()) return;
      // 4. 执行保存
      await runSave(values);
    } catch (error) {
      console.log('表单验证失败:', error);
    }
  }, [form, validateRequiredProperties, validatePropertyTypes, runSave]);

  return (
    <Modal
      title={isEdit ? '编辑实体对象' : '新建实体对象'}
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
          loading={saveLoading}
          disabled={entityDetailLoading}
        >
          保存
        </Button>,
      ]}
      width={1200}
    >
      <Spin spinning={entityDetailLoading} tip="加载中...">
        <Form form={form} layout="horizontal">
          <Form.Item
            name="ontoName"
            label="关联本体名称"
            rules={[{ required: true, message: '请选择本体名称' }]}
            labelCol={{ span: 9 }}
            wrapperCol={{ span: 8 }}
          >
            <Select
              allowClear
              placeholder="请选择本体名称"
              options={ontologyOptions}
              loading={ontologyNodeListLoading}
              showSearch
              filterOption={(input, option) =>
                (option?.label ?? '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
              onOpenChange={(open) => {
                if (open && atlasId) {
                  loadOntologyNodeList();
                }
              }}
              onChange={(value) => {
                if (value) {
                  loadOntologyNodeInfo(value);
                }
              }}
              disabled={isEdit}
            />
          </Form.Item>
          <Form.Item
            name="entityName"
            label="实体对象名称"
            normalize={(value) => value?.trim()}
            rules={[
              { required: true, message: '请输入实体对象名称' },
              validateGraphName(),
            ]}
            labelCol={{ span: 9 }}
            wrapperCol={{ span: 8 }}
          >
            <Input
              placeholder="请输入实体对象名称"
              maxLength={100}
              onChange={(e) => {
                const value = e.target.value?.trim() || '';
                syncFormFieldToPropertyJson('name', value);
              }}
            />
          </Form.Item>
          <Form.Item
            name="entityDesc"
            label="实体对象描述"
            rules={[validateDescription()]}
            labelCol={{ span: 9 }}
            wrapperCol={{ span: 8 }}
          >
            <Input.TextArea
              rows={2}
              placeholder="请输入实体对象描述"
              maxLength={500}
              onChange={(e) => {
                const value = e.target.value?.trim() || '';
                syncFormFieldToPropertyJson('description', value);
              }}
            />
          </Form.Item>
          {/* 属性表格 */}
          <PropertyTable
            properties={properties}
            propertyJson={propertyJson}
            setPropertyJson={setPropertyJson}
            loading={ontologyInfoLoading}
            showDescription={true}
            onPropertyChange={syncPropertyJsonToFormField}
          />
        </Form>
      </Spin>
    </Modal>
  );
};

export default EditModal;
