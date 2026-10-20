import {
  LeftOutlined,
  SettingOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Row, Typography } from 'antd';
import React from 'react';

const { Text } = Typography;

interface AtlasButtonsProps {
  /** 图谱类型：'entity' | 'onto' */
  type: 'entity' | 'onto';
  /** 图谱名称 */
  atlasName?: string;
  /** 图谱按钮点击回调 */
  onAtlasClick: () => void;
  /** 设置按钮点击回调 */
  onSettingClick: () => void;
  /** 自定义样式类名 */
  className?: string;
  /** 标题样式类名 */
  titleClassName?: string;
  readonly?: boolean;
}

/** 图谱头部按钮组件 */
const AtlasButtons: React.FC<AtlasButtonsProps> = ({
  type,
  atlasName = '',
  onAtlasClick,
  onSettingClick,
  className,
  titleClassName,
  readonly = false,
}) => {
  /** 获取图谱类型对应的按钮文本 */
  const getAtlasButtonText = () => {
    return type === 'entity' ? '实体图谱' : '本体图谱';
  };

  /** 获取标题样式类名 */
  const getTitleClassName = () => {
    if (titleClassName) return titleClassName;
    return type === 'entity' ? 'entityAtlasTitle' : 'ontoAtlasTitle';
  };

  return (
    <Row className={className}>
      <Button
        type="default"
        icon={<LeftOutlined />}
        onClick={() => {
          history.back();
        }}
      />
      <Button type="default" icon={<ShareAltOutlined />} onClick={onAtlasClick}>
        {getAtlasButtonText()}
      </Button>
      <Button
        type="default"
        icon={<SettingOutlined />}
        onClick={onSettingClick}
        disabled={readonly}
      >
        设置
      </Button>
      <Text strong className={getTitleClassName()}>
        {atlasName}
      </Text>
    </Row>
  );
};

export default AtlasButtons;
