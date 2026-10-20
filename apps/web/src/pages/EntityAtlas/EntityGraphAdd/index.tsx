import { pageGraph, saveGraph } from '@/services_v1/graphService';
import { ApiResultListGraphVO } from '@/services_v1/typings';
import { validateDescription, validateGraphName } from '@/utils/validator';
import { history, useRequest } from '@umijs/max';
import { App, Button, Form, Input, Modal, Select } from 'antd';
import { FC } from 'react';

/**
 * 新建实体图谱弹窗组件
 * @param isVisible 是否显示
 * @param onCancel 取消回调
 * @param onSuccess 成功回调
 * @param organizationId 组织ID
 */
const EntityGraphAdd: FC<{
  isVisible: boolean;
  onCancel: () => void;
  onSuccess?: () => void;
  organizationId: string;
}> = ({ isVisible, onCancel, onSuccess, organizationId }) => {
  const [form] = Form.useForm();
  const { message } = App.useApp();

  /** 获取可选本体图谱列表 */
  const {
    data: ontologyOptions,
    run: fetchOntologyOptions,
    loading: fetchingOntologyOptions,
  } = useRequest<ApiResultListGraphVO>(
    () =>
      pageGraph({
        graphType: 'ONTOLOGY',
        organizationId: organizationId,
        pageNum: 1,
        pageSize: 1000,
      }),
    {
      manual: true,
      formatResult: (res) => res,
    },
  );

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  /** 创建实体图谱 */
  const { run: createEntityGraphRun, loading } = useRequest(saveGraph, {
    manual: true,
    onSuccess: (response) => {
      message.success('实体图谱创建成功');
      form.resetFields();
      onCancel();
      const graphId = response?.id;
      if (graphId) {
        /** 跳转到实体图谱编辑页面 */
        history.push(
          `/manage/${organizationId}/entity/editEntityGraph/${graphId}`,
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
          graphType: 'ENTITY' as const,
          linkGraphId: values.linkGraphId ?? '',
          organizationId: organizationId,
        };
        createEntityGraphRun(formData);
      })
      .catch((error) => {
        console.error('表单验证失败:', error);
      });
  };

  return (
    <Modal
      title="新建实体图谱"
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
      <Form form={form} layout="vertical" name="createEntityGraphForm">
        <Form.Item
          name="name"
          label="实体图谱名称"
          rules={[
            {
              required: true,
              message: '请输入实体图谱名称',
            },
            /** 名称校验 */
            validateGraphName(),
          ]}
          normalize={(value) => value?.trim()}
        >
          <Input placeholder="请输入实体图谱名称" maxLength={100} />
        </Form.Item>

        <Form.Item
          name="linkGraphId"
          label="关联本体图谱"
          rules={[{ required: true, message: '请选择关联本体图谱' }]}
        >
          <Select
            placeholder="请选择关联本体图谱"
            loading={fetchingOntologyOptions}
            showSearch
            filterOption={(input, option) =>
              String(option?.label ?? '')
                .toLowerCase()
                .includes(input.toLowerCase())
            }
            options={ontologyOptions?.data?.map((item: any) => ({
              label: item.name,
              value: item.id,
            }))}
            onOpenChange={(open) => {
              if (open) {
                fetchOntologyOptions();
              }
            }}
          />
        </Form.Item>

        <Form.Item
          name="description"
          label="实体图谱描述"
          rules={[
            {
              message: '请输入实体图谱描述',
            },
            /** 描述限制 */
            validateDescription(),
          ]}
        >
          <Input.TextArea
            rows={6}
            placeholder="请输入实体图谱描述"
            maxLength={500}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default EntityGraphAdd;
