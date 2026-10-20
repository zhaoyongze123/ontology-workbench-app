import { editGraph } from '@/services_v1/graphService';
import { validateDescription, validateGraphName } from '@/utils/validator';
import { useRequest } from '@umijs/max';
import { App, Form, Input, Modal } from 'antd';
import React, { useEffect } from 'react';

/**
 * 通用设置模态框组件
 * @param visible 是否显示
 * @param onCancel 取消回调
 * @param atlasInfo 图谱信息
 * @param refreshAtlasInfo 刷新图谱信息回调
 * @param type 图谱类型，'entity'表示实体图谱，'onto'表示本体图谱
 * @param labelText 表单标签文本，默认为'图谱'
 * @param organizationId 项目ID
 */
const SetModalPop: React.FC<{
  visible: boolean;
  onCancel: () => void;
  atlasInfo?: {
    id?: string;
    name?: string;
    description?: string;
    type?: string;
  };
  refreshAtlasInfo?: () => void;
  type?: 'entity' | 'onto';
  labelText?: string;
  organizationId?: string;
  readonly?: boolean;
}> = ({
  visible,
  onCancel,
  atlasInfo,
  refreshAtlasInfo,
  type = 'entity',
  labelText = '图谱',
  organizationId,
  readonly = false,
}) => {
  const [form] = Form.useForm();
  const { message } = App.useApp();

  /** 设置图谱信息 */
  useEffect(() => {
    if (visible) {
      form.resetFields();
      if (atlasInfo) {
        form.setFieldsValue({
          name: atlasInfo.name,
          description: atlasInfo.description || '',
        });
      }
    }
  }, [visible, atlasInfo, form]);

  /** 编辑图谱信息 */
  const { run: editRun } = useRequest(editGraph, {
    manual: true,
    onSuccess: (res: any) => {
      if (res?.success === false) {
        message.error(res?.message || '保存失败');
      } else {
        message.success('保存成功');
        /** 调用刷新函数更新图谱信息 */
        if (refreshAtlasInfo) {
          refreshAtlasInfo();
        }
      }
      onCancel();
    },
  });

  /** 保存图谱信息 */
  const handleSave = async () => {
    const values = await form.validateFields();
    if (atlasInfo?.id) {
      await editRun({
        id: atlasInfo.id,
        name: values.name,
        description: values.description ?? '',
        /** 添加图谱类型参数 */
        graphType: type === 'onto' ? 'ONTOLOGY' : 'ENTITY',
        organizationId: organizationId,
      });
    }
  };

  return (
    <Modal
      title="设置"
      open={visible}
      onCancel={onCancel}
      onOk={handleSave}
      okButtonProps={{ disabled: readonly }}
      okText="保存"
      cancelText="取消"
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label={`${labelText}名称`}
          normalize={(value) => value?.trim()}
          rules={[
            { required: true, message: `请输入${labelText}名称` },
            /** 名称校验 */
            validateGraphName(),
          ]}
        >
          <Input placeholder={`请输入${labelText}名称`} maxLength={100} disabled={readonly} />
        </Form.Item>
        <Form.Item
          name="description"
          label={`${labelText}描述`}
          rules={[{ message: `请输入${labelText}描述` }, validateDescription()]}
        >
          <Input.TextArea
            rows={6}
            placeholder={`请输入${labelText}描述`}
            maxLength={500}
            disabled={readonly}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default SetModalPop;
