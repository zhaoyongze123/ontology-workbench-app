import ReactFlow from '@/pages/reactFlow';
import { query } from '@/services_v1/cypherService';
import { extractNodesAndRelationships } from '@/utils/util';
import { PlayCircleOutlined, TableOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
import { App, Button, Card, Col, Empty, Input, Layout, Row } from 'antd';
import { useState } from 'react';
import styles from './index.less';

const { Content } = Layout;

/** Cypher查询面板 */
function CypherBoard({
  databaseId,
  graphType,
  onQuerySuccess,
  readonly,
}: {
  databaseId?: string;
  graphType?: 'ONTOLOGY' | 'ENTITY';
  onQuerySuccess?: (data: {
    nodeIds: string[];
    relationshipIds: string[];
  }) => void;
  readonly?: boolean;
}) {
  const [leftPanelCollapsed, setLeftPanelCollapsed] = useState(true);
  const [rightPanelCollapsed, setRightPanelCollapsed] = useState(true);
  const [cypherQuery, setCypherQuery] = useState(
    'MATCH (n) RETURN n LIMIT 100',
  );
  const [showFlow, setShowFlow] = useState(false);
  const [queryResultData, setQueryResultData] = useState<any>(null);
  const [canRenderFlow, setCanRenderFlow] = useState(false);

  const { message } = App.useApp();

  /** 执行Cypher查询 */
  const { loading, run: runQuery } = useRequest(
    (cypher: string, graphId: string, type: 'ONTOLOGY' | 'ENTITY') =>
      query(
        {
          cypher,
          graphId,
          graphType: type,
        },
      ),
    {
      manual: true,
      onSuccess: (result) => {
        if (!result) return;
        // message.success('查询执行成功');
        setQueryResultData(result);
        // 使用 Set 直接去重，避免后续转换
        const nodeIds = new Set<string>();
        const relationshipIds = new Set<string>();
        // 递归提取节点和关系 ID
        const extractIds = (data: any): void => {
          if (!data || typeof data !== 'object') return;
          // 判断是否为节点对象（有 labels、identity、properties._id）
          if (
            Array.isArray(data.labels) &&
            'identity' in data &&
            data.properties?._id
          ) {
            nodeIds.add(data.properties._id);
            return;
          }
          // 判断是否为关系对象（有 start、end、type、properties._id）
          if (
            'start' in data &&
            'end' in data &&
            'type' in data &&
            data.properties?._id
          ) {
            relationshipIds.add(data.properties._id);
            return;
          }
          // 递归遍历数组或对象
          const values = Array.isArray(data) ? data : Object.values(data);
          values.forEach(extractIds);
        };
        extractIds(result);
        // 通知父组件刷新左侧列表
        onQuerySuccess?.({
          nodeIds: Array.from(nodeIds),
          relationshipIds: Array.from(relationshipIds),
        });
        // 判断是否支持图形化展示
        const isValidArray = Array.isArray(result) && result.length > 0;
        if (isValidArray) {
          const { nodes, relationships } = extractNodesAndRelationships(result);
          const hasGraphData =
            (nodes?.length ?? 0) > 0 || (relationships?.length ?? 0) > 0;
          setCanRenderFlow(hasGraphData);
        } else {
          setCanRenderFlow(false);
        }
        setShowFlow(true);
      },
    },
  );

  const handleRunQuery = () => {
    if (!cypherQuery.trim()) {
      message.warning('请输入Cypher查询语句');
      return;
    }
    if (!databaseId) {
      message.error('未获取到图谱ID');
      return;
    }
    if (!graphType) {
      message.error('未获取到图谱类型');
      return;
    }
    runQuery(cypherQuery.trim(), databaseId, graphType);
  };

  return (
    <Layout className={styles.cypherBoard}>
      {/* 顶部查询区域 */}
      <Row className={styles.queryHeader}>
        <Col span={20} className={styles.queryContainer}>
          <Input
            className={styles.queryInput}
            // placeholder="MATCH (n) RETURN n LIMIT 1000"
            value={cypherQuery}
            onChange={(e) => setCypherQuery(e.target.value)}
          />
          <Button
            className={styles.runButton}
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={handleRunQuery}
            loading={loading}
            disabled={loading}
          >
            运行查询
          </Button>
        </Col>
      </Row>

      {/* 主要内容区域 - resultSection */}
      <Content className={styles.resultSection}>
        {/* 左侧展开按钮 */}
        {/* {leftPanelCollapsed && (
          <Button
            type="text"
            size="middle"
            icon={<RightOutlined />}
            onClick={() => setLeftPanelCollapsed(false)}
            className={styles.leftExpandButton}
            title="展开数据库结构"
          />
        )} */}

        {/* 右侧展开按钮 */}
        {/* {rightPanelCollapsed && (
          <Button
            type="text"
            size="middle"
            icon={<LeftOutlined />}
            onClick={() => setRightPanelCollapsed(false)}
            className={styles.rightExpandButton}
            title="展开属性详情"
          />
        )} */}

        {/* 中间结果展示区域 - 使用Card组件 */}
        <Card
          className={`${styles.centerContent} ${
            showFlow ? styles.flowState : styles.emptyState
          }`}
          variant="borderless"
        >
          {showFlow && canRenderFlow ? (
            <ReactFlow
              cypherData={queryResultData}
              graphType={graphType}
              graphId={databaseId}
              onDataChange={handleRunQuery}
              readonly={readonly}
            />
          ) : showFlow && !canRenderFlow ? (
            <div className={styles.rawDataContainer}>
              <h3>查询结果</h3>
              <pre className={styles.rawData}>
                {JSON.stringify(queryResultData, null, 2)}
              </pre>
            </div>
          ) : (
            <Empty
              className={styles.emptyResult}
              image={
                <TableOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />
              }
              description="输入 Cypher 查询语句开始探索数据"
            />
          )}
        </Card>

        {/* 左侧数据库结构面板 - 使用Card组件作为浮动面板 */}
        {/*
         */}

        {/* 右侧属性详情面板 - 使用Card组件作为浮动面板 */}
        {/* <Card
          className={`${styles.rightPanel} ${
            rightPanelCollapsed ? styles.collapsed : ''
          }`}
          variant="borderless"
          styles={{ body: { padding: 0, height: '100%' } }}
        >
          <PropertyDetail
            collapsed={rightPanelCollapsed}
            onCollapse={() => setRightPanelCollapsed(true)}
          />
        </Card> */}
      </Content>
    </Layout>
  );
}

export default CypherBoard;
