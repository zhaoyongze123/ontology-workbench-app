import {
  FileTextOutlined,
  LeftOutlined,
  LinkOutlined,
  NodeIndexOutlined,
} from '@ant-design/icons';
import { Button, Card, Collapse, Flex, Space, Tree, Typography } from 'antd';
import { useState } from 'react';
import styles from './index.less';

const { Text } = Typography;

/** 数据库结构组件 */
function DataStructure({
  collapsed,
  onCollapse,
}: {
  collapsed: boolean;
  onCollapse: () => void;
}) {
  const [expandedKeys, setExpandedKeys] = useState<string[]>([
    'node-types',
    'properties',
    'relation-types',
  ]);

  /** 模拟数据结构 */
  const nodeTypes = [
    {
      key: 'Person',
      title: 'Person',
      icon: (
        <Button
          size="small"
          shape="circle"
          className={styles.nodeIcon}
          style={{ backgroundColor: '#52c41a', border: 'none' }}
        />
      ),
    },
    {
      key: 'Movie',
      title: 'Movie',
      icon: (
        <Button
          size="small"
          shape="circle"
          className={styles.nodeIcon}
          style={{ backgroundColor: '#1890ff', border: 'none' }}
        />
      ),
    },
    {
      key: 'Director',
      title: 'Director',
      icon: (
        <Button
          size="small"
          shape="circle"
          className={styles.nodeIcon}
          style={{ backgroundColor: '#722ed1', border: 'none' }}
        />
      ),
    },
  ];

  /** 属性键 */
  const properties = [
    { key: 'name', title: 'name' },
    { key: 'title', title: 'title' },
    { key: 'year', title: 'year' },
  ];

  /** 关系类型 */
  const relationTypes = [
    { key: 'ACTED_IN', title: 'ACTED_IN' },
    { key: 'DIRECTED', title: 'DIRECTED' },
    { key: 'FOLLOWS', title: 'FOLLOWS' },
  ];

  const renderTreeData = (data: any[], type: string) => {
    return data.map((item) => ({
      title: (
        <Space size={8} className={styles.treeNode}>
          {item.icon && item.icon}
          <Text className={styles.nodeText}>{item.title}</Text>
        </Space>
      ),
      key: item.key,
      isLeaf: true,
    }));
  };

  /** 定义 Collapse 的 items */
  const collapseItems = [
    {
      key: 'node-types',
      label: (
        <Space size={8} className={styles.panelTitle}>
          <NodeIndexOutlined />
          <Text>节点类型</Text>
        </Space>
      ),
      children: (
        <Tree
          showIcon={false}
          treeData={renderTreeData(nodeTypes, 'node')}
          className={styles.structureTree}
        />
      ),
    },
    // {
    //   key: 'properties',
    //   label: (
    //     <Space size={8} className={styles.panelTitle}>
    //       <FileTextOutlined />
    //       <Text>属性键</Text>
    //     </Space>
    //   ),
    //   children: (
    //     <Tree
    //       showIcon={false}
    //       treeData={renderTreeData(properties, 'property')}
    //       className={styles.structureTree}
    //     />
    //   ),
    // },
    // {
    //   key: 'relation-types',
    //   label: (
    //     <Space size={8} className={styles.panelTitle}>
    //       <LinkOutlined />
    //       <Text>关系类型</Text>
    //     </Space>
    //   ),
    //   children: (
    //     <Tree
    //       showIcon={false}
    //       treeData={renderTreeData(relationTypes, 'relation')}
    //       className={styles.structureTree}
    //     />
    //   ),
    // },
  ];

  if (collapsed) {
    return null;
  }

  return (
    <Card
      className={styles.dataStructurePanel}
      styles={{
        body: {
          padding: 0,
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
        },
      }}
      variant="borderless"
    >
      <Flex
        justify="space-between"
        align="center"
        className={styles.panelHeader}
      >
        <Text strong>数据库结构</Text>
        <Button
          type="text"
          size="middle"
          icon={<LeftOutlined />}
          onClick={onCollapse}
          className={styles.collapseButton}
        />
      </Flex>
      <Space direction="vertical" size={0} className={styles.panelContent}>
        <Collapse
          ghost
          expandIconPosition="end"
          defaultActiveKey={expandedKeys}
          className={styles.dataStructure}
          items={collapseItems}
        />
      </Space>
    </Card>
  );
}

export default DataStructure;
