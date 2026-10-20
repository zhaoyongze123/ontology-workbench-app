import type { NodeProps } from '@xyflow/react';
import { Handle, Position } from '@xyflow/react';
import React from 'react';
import type { NodeData } from '../../type';
import styles from './index.less';

const CustomNode: React.FC<NodeProps> = ({ data }) => {
  const nodeData = data as NodeData;

  // 获取显示名称，优先使用properties中的name或Name，如果都没有则使用第一个label
  const getDisplayName = () => {
    return (
      nodeData.properties?.name ||
      nodeData.properties?.Name ||
      nodeData.labels?.[0] ||
      nodeData.identity
    );
  };

  return (
    <div className={styles.customNode}>
      <Handle
        type="target"
        position={Position.Top}
        className={styles.handle}
      />
      <div className={styles.label}>{getDisplayName()}</div>
      <Handle
        type="source"
        position={Position.Bottom}
        className={styles.handle}
      />
    </div>
  );
};

export default React.memo(CustomNode);
