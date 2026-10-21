import { RightOutlined as RightArrowOutlined } from '@ant-design/icons';
import { Button, Card, Col, Row, Typography } from 'antd';
import styles from './index.less';

const { Text } = Typography;

/** 属性详情面板组件 */
function PropertyDetail({
  collapsed,
  onCollapse,
}: {
  collapsed: boolean;
  onCollapse: () => void;
}) {
  if (collapsed) {
    return null;
  }

  return (
    <Card
      className={styles.rightPanel}
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
      <Row className={styles.panelHeader}>
        <Button
          type="text"
          size="middle"
          icon={<RightArrowOutlined />}
          onClick={onCollapse}
          className={styles.collapseButton}
        />
        <Text strong>属性详情</Text>
      </Row>

      <Row className={styles.panelContent}>
        <Col span={24} className={styles.propertySection}>
          <Row className={styles.propertyItem}>
            <Text strong>节点名称</Text>
          </Row>

          <Row className={styles.propertyItem}>
            <Text strong>节点类型</Text>
          </Row>
        </Col>
      </Row>
    </Card>
  );
}

export default PropertyDetail;
