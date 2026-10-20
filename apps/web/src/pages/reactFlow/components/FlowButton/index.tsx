import {
  CloudDownloadOutlined,
  PlusOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { Button, Space, Tooltip } from 'antd';
import React from 'react';

/** 按钮组件 */
const FlowButton: React.FC<{
  onAddNode: () => void;
  onSaveGraph: () => void;
  onLoadFromApi: () => void;
  loading?: boolean;
}> = React.memo(
  ({ onAddNode, onSaveGraph, onLoadFromApi, loading = false }) => {
    return (
      <Space>
        <Tooltip title="从API加载节点数据">
          <Button
            type="primary"
            icon={<CloudDownloadOutlined />}
            onClick={onLoadFromApi}
            loading={loading}
          >
            加载数据
          </Button>
        </Tooltip>
        <Tooltip title="添加新节点">
          <Button type="primary" icon={<PlusOutlined />} onClick={onAddNode}>
            添加节点
          </Button>
        </Tooltip>
        <Tooltip title="保存画布内容">
          <Button type="primary" icon={<SaveOutlined />} onClick={onSaveGraph}>
            保存
          </Button>
        </Tooltip>
      </Space>
    );
  },
);

export default FlowButton;
