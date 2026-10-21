import { Alert, Button, Input, Modal, Space, Typography } from 'antd';
import React, { useState } from 'react';

export type OneTimeValue = {
  key: string;
  label: string;
  value: string;
  sensitive?: boolean;
};

type OneTimeValuesModalProps = {
  title: string;
  description: string;
  values: OneTimeValue[];
  open: boolean;
  onClose: () => void;
};

/** 一次性链接/Secret 只保存在当前页面内存；遮罩、Escape 和关闭图标不能误丢结果。 */
const OneTimeValuesModal: React.FC<OneTimeValuesModalProps> = ({
  title,
  description,
  values,
  open,
  onClose,
}) => {
  const [copyError, setCopyError] = useState('');
  const [copySuccess, setCopySuccess] = useState('');

  const copy = async (value: OneTimeValue) => {
    setCopyError('');
    setCopySuccess('');
    try {
      if (!navigator.clipboard?.writeText)
        throw new Error('clipboard unavailable');
      await navigator.clipboard.writeText(value.value);
      setCopySuccess(`${value.label}已复制。请确认已保存在受控位置。`);
    } catch {
      setCopyError(
        `复制${value.label}失败。请在下方手动选择并保存，关闭后本页不会保留该值。`,
      );
    }
  };

  return (
    <Modal
      title={title}
      open={open}
      width="min(560px, calc(100vw - 24px))"
      closable={false}
      keyboard={false}
      maskClosable={false}
      destroyOnClose
      footer={
        <Button type="primary" onClick={onClose}>
          已保存，关闭
        </Button>
      }
    >
      <Typography.Paragraph>{description}</Typography.Paragraph>
      {copyError ? (
        <Alert role="alert" type="error" showIcon message={copyError} />
      ) : null}
      {copySuccess ? (
        <Typography.Text role="status" aria-live="polite" type="success">
          {copySuccess}
        </Typography.Text>
      ) : null}
      <Space
        direction="vertical"
        size={16}
        style={{ width: '100%', marginTop: 16 }}
      >
        {values.map((item) => (
          <div key={item.key}>
            <Typography.Text strong>{item.label}</Typography.Text>
            {item.sensitive ? (
              <Input.Password
                aria-label={item.label}
                value={item.value}
                readOnly
                visibilityToggle
                autoComplete="off"
                style={{ marginTop: 6 }}
              />
            ) : (
              <Input.TextArea
                aria-label={item.label}
                value={item.value}
                readOnly
                autoSize={{ minRows: 2, maxRows: 5 }}
                onFocus={(event) => event.currentTarget.select()}
                style={{ marginTop: 6, overflowWrap: 'anywhere' }}
              />
            )}
            <Button style={{ marginTop: 8 }} onClick={() => void copy(item)}>
              复制{item.label}
            </Button>
          </div>
        ))}
      </Space>
    </Modal>
  );
};

export default OneTimeValuesModal;
