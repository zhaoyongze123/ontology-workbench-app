import { CopyOutlined } from '@ant-design/icons';
import { Button, Tooltip, Typography } from 'antd';
import React from 'react';
import styles from './CopyableValue.less';

type CopyableValueProps = {
  value: string;
  label: string;
  onCopyError: (message: string) => void;
  onCopySuccess: (message: string) => void;
};

/** 长值在表格里省略；复制始终使用原始值，详情页提供手动保存路径。 */
const CopyableValue: React.FC<CopyableValueProps> = ({
  value,
  label,
  onCopyError,
  onCopySuccess,
}) => {
  const copy = async () => {
    try {
      if (!navigator.clipboard?.writeText)
        throw new Error('clipboard unavailable');
      await navigator.clipboard.writeText(value);
      onCopySuccess(`${label}已复制。`);
    } catch {
      onCopyError(`复制${label}失败，请打开详情并手动选择完整值。`);
    }
  };

  return (
    <div className={styles.value}>
      <Tooltip title={value} placement="topLeft">
        <Typography.Text className={styles.text} code ellipsis>
          {value}
        </Typography.Text>
      </Tooltip>
      <Button
        type="text"
        className={styles.copyButton}
        icon={<CopyOutlined aria-hidden="true" />}
        aria-label={`复制${label}`}
        onClick={() => void copy()}
      />
    </div>
  );
};

export default CopyableValue;
