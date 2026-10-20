import { App, Button, Form, Input, Modal } from 'antd';
import { validateDescription, validateGraphName } from '@/utils/validator';
import { useEffect } from 'react';

/**
 * 项目弹窗
 * @param open 是否显示
 * @param onCancel 取消回调
 * @param onSubmit 提交回调
 * @param initialValues 初始值
 * @param isEdit 是否编辑
 * @returns 
 */
const ProgramPop: React.FC<{
  open: boolean;
  onCancel: () => void;
  onSubmit: (values: { name: string; description: string; id?: string }) => void;
  initialValues?: {
    id?: string;
    name?: string;
    description?: string;
  };
  isEdit?: boolean;
}> = ({ 
  open, 
  onCancel, 
  onSubmit, 
  initialValues, 
  isEdit = false 
}) => {
  const [form] = Form.useForm();
  const { message } = App.useApp();

  /** 如果弹窗显示，并且有初始值，则设置表单值 */
  useEffect(() => {
    if (open && initialValues) {
      form.setFieldsValue(initialValues);
    }
  }, [open, initialValues, form]);

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      /** 如果是编辑模式，需要传递id */
      if (isEdit && initialValues?.id) {
        onSubmit({
          ...values,
          id: initialValues.id,
        });
      } else {
        onSubmit(values);
      }
      form.resetFields();
    }).catch(err => {
      message.error('表单验证失败，请检查输入');
    });
  };

  return (
    <Modal
      title={isEdit ? "编辑项目" : "新建项目"}
      open={open}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          取消
        </Button>,
        <Button key="submit" type="primary" onClick={handleSubmit}>
          确定
        </Button>,
      ]}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="项目名称"
          rules={[
            {
              required: true,
              message: '请输入项目名称',
            },
            validateGraphName(),
          ]}
        >
          <Input placeholder="请输入项目名称" maxLength={100} />
        </Form.Item>
        <Form.Item
          name="description"
          label="项目描述"
          rules={[validateDescription()]}
        >
          <Input.TextArea
            rows={6}
            placeholder="请输入项目描述"
            maxLength={500}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ProgramPop; 