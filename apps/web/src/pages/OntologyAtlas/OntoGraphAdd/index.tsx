import { saveGraph } from '@/services_v1/graphService';
import { validateDescription, validateGraphName } from '@/utils/validator';
import { history, useRequest } from '@umijs/max';
import { App, Button, Form, Input, Modal } from 'antd';
import { FC } from 'react';

/**
 * 新建本体图谱弹窗组件
 * @param isVisible 是否显示
 * @param onCancel 取消回调
 * @param onSuccess 成功回调
 * @param organizationId 组织ID
 */
const OntoGraphAdd: FC<{
  isVisible: boolean;
  onCancel: () => void;
  onSuccess?: () => void;
  organizationId: string;
}> = ({ isVisible, onCancel, onSuccess, organizationId }) => {
  const [form] = Form.useForm();
  const { message } = App.useApp();

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  /** 创建本体图谱 */
  const { run: createOntologyRun, loading } = useRequest(saveGraph, {
    manual: true,
    onSuccess: (response) => {
      message.success('本体图谱创建成功');
      form.resetFields();
      onCancel();
      const graphId = response?.id;
      if (graphId) {
        /** 跳转到本体图谱编辑页面 */
        history.push(
          `/manage/${organizationId}/ontology/editOntologyGraph/${graphId}`,
        );
      }
      /** 调用成功回调 */
      onSuccess?.();
    },
  });

  /** 提交表单 */
  const handleSubmit = () => {
    form
      .validateFields()
      .then((values) => {
        const formData = {
          name: values.name ?? '',
          description: values.description ?? '',
          graphType: 'ONTOLOGY' as const,
          organizationId: organizationId,
        };
        createOntologyRun(formData);
      })
      .catch((error) => {
        console.error('表单验证失败:', error);
      });
  };

  return (
    <Modal
      title="新建本体图谱"
      open={isVisible}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          取消
        </Button>,
        <Button
          key="submit"
          type="primary"
          onClick={handleSubmit}
          loading={loading}
        >
          新建
        </Button>,
      ]}
    >
      <Form form={form} layout="vertical" name="createOntologyForm">
        <Form.Item
          name="name"
          label="本体图谱名称"
          rules={[
            {
              required: true,
              message: '请输入本体图谱名称',
            },
            /** 名称校验 */
            validateGraphName(),
          ]}
          normalize={(value) => value?.trim()}
        >
          <Input placeholder="请输入本体图谱名称" maxLength={100} />
        </Form.Item>

        <Form.Item
          name="description"
          label="本体图谱描述"
          rules={[
            {
              message: '请输入本体图谱描述',
            },
            /** 描述限制 */
            validateDescription(),
          ]}
        >
          <Input.TextArea
            rows={6}
            placeholder="请输入本体图谱描述"
            maxLength={500}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default OntoGraphAdd;
