import { getNodeOptional } from '@/services_v1/nodeService';
import {
  editRelationship,
  getLinkOntologyRelationshipList,
  getRelationshipInfo,
  saveRelationship,
} from '@/services_v1/relationshipService';
import {
  ApiResultListOptionalVO,
  ApiResultRelationshipVO,
  OptionalVO,
  PropertySchema,
} from '@/services_v1/typings';
import { validateDescription, validatePropertyValue } from '@/utils/validator';
import { useRequest } from '@umijs/max';
import { App, Button, Form, Input, Modal, Select, Spin } from 'antd';
import React, { useEffect, useState } from 'react';
import PropertyTable from '../PropertyTable';
import './index.less';

/** 关系编辑弹窗 */
const RelationEditModal: React.FC<{
  visible: boolean;
  currentRelation: any;
  onCancel: () => void;
  atlasId: string;
  refreshRelationList: () => void;
}> = ({ visible, currentRelation, onCancel, atlasId, refreshRelationList }) => {
  const [form] = Form.useForm();
  const [properties, setProperties] = useState<any[]>([]);
  const [sourceEntityOptions, setSourceEntityOptions] = useState<any[]>([]);
  const [targetEntityOptions, setTargetEntityOptions] = useState<any[]>([]);
  const isEdit = !!currentRelation; /** 判断是否为编辑模式 */

  const { message } = App.useApp();

  /** 获取可选实体列表 */
  const {
    data: optionalEntitiesRes,
    loading: optionalEntitiesLoading,
    run: getOptionalEntitiesRun,
  } = useRequest<ApiResultListOptionalVO>(
    () =>
      getNodeOptional({
        graphId: atlasId,
        graphType: 'ENTITY',
      }),
    {
      manual: true,
      formatResult: (res) => res,
      onSuccess: (res) => {
        const data = res?.data || [];
        if (data && isEdit && currentRelation) {
          /** 在编辑模式下，找到对应的实体名称并设置选项 */
          const sourceEntity = data.find(
            (item: OptionalVO) => item.id === currentRelation.sourceNodeId,
          );
          const targetEntity = data.find(
            (item: OptionalVO) => item.id === currentRelation.targetNodeId,
          );
          if (sourceEntity) {
            setSourceEntityOptions([
              {
                value: sourceEntity.id,
                label: sourceEntity.name,
              },
            ]);
          }
          if (targetEntity) {
            setTargetEntityOptions([
              {
                value: targetEntity.id,
                label: targetEntity.name,
              },
            ]);
          }
        }
      },
    },
  );
  /** 可选实体列表数据 */
  const optionalEntities = optionalEntitiesRes?.data || [];

  /** 获取可选关系列表 */
  const {
    data: optionalRelationsRes,
    loading: optionalRelationsLoading,
    run: getOptionalRelationsRun,
  } = useRequest<ApiResultListOptionalVO>(
    (params: { sourceNodeId: string; targetNodeId: string }) =>
      getLinkOntologyRelationshipList(params),
    {
      manual: true,
      formatResult: (res) => res,
    },
  );
  /** 可选关系列表数据 */
  const optionalRelations = optionalRelationsRes?.data || [];

  /** 解析属性JSON，带错误处理 */
  const parsePropertyJson = (propertyJson: string): Record<string, any> => {
    try {
      return JSON.parse(propertyJson);
    } catch (error) {
      console.error('Failed to parse propertyJson:', error);
      message.error('属性数据格式错误');
      return {};
    }
  };

  /** 将属性Schema转换为属性列表 */
  const transformSchemaToProperties = (
    schemas: PropertySchema[],
    propertyValues?: Record<string, any>,
  ) => {
    return schemas.map((schema) => ({
      name: schema.name,
      type: schema.type,
      description: schema.description,
      required: schema.required,
      value:
        propertyValues && schema.name ? propertyValues[schema.name] ?? '' : '',
    }));
  };

  /** 回填表单字段 */
  const fillFormFields = (data: any) => {
    const isCurrentRelation =
      isEdit && currentRelation && data.id === currentRelation.id;
    if (isCurrentRelation) {
      /** 编辑当前关系：回填所有字段 */
      form.setFieldsValue({
        sourceId: data.sourceNodeId,
        targetId: data.targetNodeId,
        relationId: data.linkOntologyId,
        relationDesc: data.description,
      });
    } else if (isEdit) {
      /** 编辑模式切换关系：只更新关系描述，不更新relationId（用户已在onChange中设置） */
      form.setFieldsValue({
        relationDesc: data.description,
      });
    } else {
      /** 新建模式：只回填关系描述 */
      form.setFieldsValue({
        relationDesc: data.description ?? '',
      });
    }
  };

  /** 处理属性数据 */
  const handlePropertiesData = (data: any) => {
    if (!data.propertySchemaJson || data.propertySchemaJson.length === 0) {
      setProperties([]);
      return;
    }
    /** 只有在编辑模式下才回填属性值，新建模式下只回填属性Schema */
    const propertyValues =
      isEdit && data.propertyJson
        ? parsePropertyJson(data.propertyJson)
        : undefined;
    const transformedProperties = transformSchemaToProperties(
      data.propertySchemaJson,
      propertyValues,
    );
    setProperties(transformedProperties);
  };

  /** 获取关系信息 回填弹窗关系的属性 */
  const { run: getRelationshipInfoRun, loading: relationshipInfoLoading } =
    useRequest<ApiResultRelationshipVO>(
      (params: { graphType: 'ONTOLOGY' | 'ENTITY'; relationshipId: string }) =>
        getRelationshipInfo(params),
      {
        manual: true,
        formatResult: (res) => res,
        onSuccess: (res) => {
          const data = res?.data;
          if (!data) return;

          fillFormFields(data);
          handlePropertiesData(data);
        },
      },
    );

  /** 验证必填属性 */
  const validateRequiredProperties = () => {
    const missingProps = properties.filter((prop) => {
      // 只验证必填属性：required 为 'REQUIRED' 或 true
      if (!prop.name) return false;
      const isRequired = prop.required === 'REQUIRED' || prop.required === true;
      if (!isRequired) return false;

      const value = prop.value;
      // 空值判断：空字符串、undefined、null 都视为未填写，但 '0' 和 'false' 是有效值
      return value === '' || value === undefined || value === null;
    });

    if (missingProps.length > 0) {
      const propNames = missingProps.map((p) => p.name).join('、');
      message.error(`请填写所有必填属性：${propNames}`);
      return false;
    }
    return true;
  };

  /** 验证属性值类型 */
  const validatePropertyTypes = () => {
    const invalidProp = properties.find((prop) => {
      if (!prop.name || !prop.type) return false;
      const value = prop.value;
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
  };

  /** 构建属性Schema列表 */
  const buildPropertySchemaJson = (): PropertySchema[] => {
    return properties.map((prop) => ({
      name: prop.name ?? '',
      type: prop.type ?? 'STRING',
      description: prop.description ?? '',
      required: prop.required ? 'REQUIRED' : 'NOT_REQUIRED',
    }));
  };

  /** 转换属性值类型 */
  const convertPropertyValue = (value: any, type: string): any => {
    if (value === '' || value === undefined || value === null) {
      return value;
    }
    switch (type) {
      case 'NUMBER':
        return Number(value);
      case 'BOOLEAN':
        return value === 'true' || value === true;
      case 'OBJECT':
        try {
          return typeof value === 'string' ? JSON.parse(value) : value;
        } catch (e) {
          console.error('JSON parse error:', e);
          return value;
        }
      default:
        return value;
    }
  };

  /** 构建属性JSON字符串 */
  const buildPropertyJson = (): string => {
    const propertyJsonObj: Record<string, any> = {};
    properties.forEach((prop) => {
      if (prop.name) {
        propertyJsonObj[prop.name] = convertPropertyValue(
          prop.value ?? '',
          prop.type,
        );
      }
    });
    return JSON.stringify(propertyJsonObj);
  };

  /** 保存关系 */
  const { loading: saveLoading, run: runSaveRelationship } = useRequest(
    async (values: any) => {
      const relationName = optionalRelations.find(
        (item: OptionalVO) => item?.id === values.relationId,
      )?.name;
      const params = {
        linkOntologyRelationshipId: values.relationId ?? '',
        graphId: atlasId,
        name: relationName ?? '',
        description: values.relationDesc ?? '',
        propertySchemaJson: buildPropertySchemaJson(),
        propertyJson: buildPropertyJson(),
        graphType: 'ENTITY' as const,
      };
      if (isEdit && currentRelation?.id) {
        return await editRelationship({
          ...params,
          id: currentRelation.id,
          sourceNodeId: currentRelation.sourceNodeId ?? '',
          targetNodeId: currentRelation.targetNodeId ?? '',
        });
      } else {
        return await saveRelationship({
          ...params,
          sourceNodeId: values.sourceId ?? '',
          targetNodeId: values.targetId ?? '',
        });
      }
    },
    {
      manual: true,
      onSuccess: () => {
        message.success(isEdit ? '关系更新成功' : '关系创建成功');
        refreshRelationList();
        handleCancel();
      },
      onError: (error: any) => {
        // const defaultMsg = isEdit ? '关系更新失败' : '关系创建失败';
        // message.error(
        //   error?.response?.data?.message || error?.message || defaultMsg,
        // );
      },
    },
  );

  /** 当编辑模式且有currentRelation时，获取关系详情 */
  useEffect(() => {
    if (visible && isEdit && currentRelation) {
      /** 编辑模式下，调用getRelationshipInfo获取详细信息进行回填 */
      if (currentRelation.id) {
        getRelationshipInfoRun({
          graphType: 'ENTITY',
          relationshipId: currentRelation.id,
        });
        /** 编辑模式下获取可选关系列表 */
        getOptionalRelationsRun({
          sourceNodeId: currentRelation.sourceNodeId,
          targetNodeId: currentRelation.targetNodeId,
        });
        /** 编辑模式下获取实体列表，用于显示实体名称 */
        getOptionalEntitiesRun();
      }
    } else if (visible && !isEdit) {
      /** 如果是新建模式，重置表单 */
      form.resetFields();
      setProperties([]);
      setSourceEntityOptions([]);
      setTargetEntityOptions([]);
    }
  }, [visible, currentRelation, isEdit, form]);

  /** 关闭弹窗 */
  const handleCancel = () => {
    form.resetFields();
    setProperties([]);
    setSourceEntityOptions([]);
    setTargetEntityOptions([]);
    onCancel();
  };

  /**
   * 处理保存按钮点击
   */
  const handleSave = async () => {
    try {
      // 1. 验证表单字段
      const values = await form.validateFields();
      // 2. 验证必填属性
      if (!validateRequiredProperties()) return;
      // 3. 验证属性类型
      if (!validatePropertyTypes()) return;
      // 4. 执行保存
      await runSaveRelationship(values);
    } catch (error) {
      console.log('表单验证失败:', error);
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑实体关系' : '新建实体关系'}
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
        >
          保存
        </Button>,
      ]}
      width={800}
    >
      <Spin spinning={saveLoading || relationshipInfoLoading}>
        <Form form={form} layout="horizontal">
          <Form.Item
            name="sourceId"
            label="主实体"
            rules={[{ required: true, message: '请选择主实体' }]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Select
              placeholder="请选择主实体"
              showSearch
              filterOption={(input, option) =>
                String(option?.label ?? '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
              options={
                isEdit && sourceEntityOptions.length > 0
                  ? sourceEntityOptions
                  : optionalEntities.map((item: OptionalVO) => ({
                      value: item.id || '',
                      label: item.name || '',
                    }))
              }
              loading={optionalEntitiesLoading}
              disabled={isEdit}
              onOpenChange={(open) => {
                if (open) {
                  getOptionalEntitiesRun();
                }
              }}
              onChange={() => {
                /** 当主实体变化时，清空关系名称和属性 */
                form.setFieldsValue({ relationId: undefined });
                setProperties([]);
              }}
            />
          </Form.Item>
          <Form.Item
            name="targetId"
            label="目标实体"
            rules={[{ required: true, message: '请选择目标实体' }]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Select
              placeholder="请选择目标实体"
              showSearch
              filterOption={(input, option) =>
                String(option?.label ?? '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
              options={
                isEdit && targetEntityOptions.length > 0
                  ? targetEntityOptions
                  : optionalEntities.map((item: OptionalVO) => ({
                      value: item.id || '',
                      label: item.name || '',
                    }))
              }
              loading={optionalEntitiesLoading}
              disabled={isEdit}
              onOpenChange={(open) => {
                if (open) {
                  getOptionalEntitiesRun();
                }
              }}
              onChange={() => {
                /** 当目标实体变化时，清空关系名称和属性 */
                form.setFieldsValue({ relationId: undefined });
                setProperties([]);
              }}
            />
          </Form.Item>
          <Form.Item
            name="relationId"
            label="关系名称"
            rules={[{ required: true, message: '请输入关系名称' }]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Select
              placeholder="请选择关系名称"
              options={optionalRelations.map((item: OptionalVO) => ({
                value: item.id || '',
                label: item.name || '',
              }))}
              showSearch
              filterOption={(input, option) =>
                String(option?.label ?? '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
              onOpenChange={(open) => {
                if (open) {
                  const sourceNodeId = form.getFieldValue('sourceId');
                  const targetNodeId = form.getFieldValue('targetId');
                  if (sourceNodeId && targetNodeId) {
                    getOptionalRelationsRun({
                      sourceNodeId,
                      targetNodeId,
                    });
                  } else {
                    message.warning('请先选择主实体和目标实体');
                  }
                }
              }}
              onChange={(value) => {
                /** 当选择关系名称后，调用getRelationshipInfo获取关系属性信息 */
                const sourceId = form.getFieldValue('sourceId');
                const targetId = form.getFieldValue('targetId');
                if (sourceId && targetId && value) {
                  /** 选择关系名称时，使用ONTOLOGY类型获取关系模板信息 */
                  getRelationshipInfoRun({
                    graphType: 'ONTOLOGY',
                    relationshipId: value,
                  });
                }
              }}
              loading={optionalRelationsLoading}
            />
          </Form.Item>
          <Form.Item
            name="relationDesc"
            label="关系描述"
            rules={[
              { message: '请输入关系描述' },
              /** 描述限制 */
              validateDescription(),
            ]}
            labelCol={{ span: 7 }}
            wrapperCol={{ span: 12 }}
          >
            <Input.TextArea
              rows={2}
              placeholder="请输入关系描述"
              maxLength={500}
            />
          </Form.Item>
          {/* 属性表格 */}
          <PropertyTable
            properties={properties}
            setProperties={setProperties}
            showDescription={false}
          />
        </Form>
      </Spin>
    </Modal>
  );
};

export default RelationEditModal;
