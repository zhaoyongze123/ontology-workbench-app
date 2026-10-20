import AtlasButtons from '@/components/AtlasButtons';
import SetModalPop from '@/components/SetModalPop';
import CypherBoard from '@/pages/cypherBoard';
import { useProjectAccess } from '@/features/projects/useProjectAccess';
import { getGraphInfo } from '@/services_v1/graphService';
import { useRequest } from '@umijs/max';
import { Layout, Row, Splitter, Tabs } from 'antd';
import { useRef, useState } from 'react';
import { useLocation, useParams } from 'umi';
import EntityObjList from './EntityObjList';
import EntityRelationList from './EntityRelationList';
import styles from './index.less';

const { Content, Header } = Layout;

/** 编辑实体图谱 */
const EditEntityGraph: React.FC = () => {
  const params = useParams<{ id: string; graphId: string }>();
  const location = useLocation();
  const routeReadonly =
    (location.state as { readonly?: boolean })?.readonly ?? false;
  const organizationId = params.id || '';
  const { can } = useProjectAccess(organizationId);
  const readonly = routeReadonly || !can('EDIT');
  const atlasId = params.graphId || '';
  const [settingsModalVisible, setSettingsModalVisible] = useState(false);
  const [activeTab, setActiveTab] = useState('entity');
  const [showCypherBoard, setShowCypherBoard] = useState(false);

  /** 创建引用以访问子组件的方法 */
  const entityObjListRef = useRef<{
    refreshEntityData: (nodeIdList?: string[]) => void;
  }>(null);
  const entityRelationListRef = useRef<{
    refreshEntityRelationData: (relationshipIdList?: string[]) => void;
  }>(null);

  /** 获取实体图谱信息 设置弹窗信息回填 */
  const {
    data: entityAtlasInfo,
    run: EntityAtlasInfoRun,
    refresh: refreshAtlasInfo,
  } = useRequest(getGraphInfo, {
    defaultParams: [
      {
        graphId: atlasId,
        graphType: 'ENTITY',
      },
    ],
    formatResult: (res) => res.data,
  });

  /**  处理标签页切换，刷新对应的数据 */
  const handleTabChange = (key: string) => {
    setActiveTab(key);
    /** 根据当前激活的标签页刷新对应的数据 */
    if (key === 'entity' && entityObjListRef.current) {
      entityObjListRef.current.refreshEntityData();
    } else if (key === 'relation' && entityRelationListRef.current) {
      entityRelationListRef.current.refreshEntityRelationData();
    }
  };

  /** 处理实体图谱按钮点击 */
  const handleCypherBoardClick = () => {
    //暂时不需要跳转，先隐藏
    // setShowCypherBoard(!showCypherBoard);
  };

  /** 处理Cypher查询成功后的回调 */
  const handleCypherQuerySuccess = (data: {
    nodeIds: string[];
    relationshipIds: string[];
  }) => {
    const { nodeIds, relationshipIds } = data;

    // 根据当前激活的标签页刷新对应的列表
    if (activeTab === 'entity' && entityObjListRef.current) {
      entityObjListRef.current.refreshEntityData(nodeIds);
    } else if (activeTab === 'relation' && entityRelationListRef.current) {
      entityRelationListRef.current.refreshEntityRelationData(relationshipIds);
    }
  };

  return (
    <Layout className={styles.editEntity}>
      <Header className={styles.header}>
        <AtlasButtons
          type="entity"
          atlasName={entityAtlasInfo?.name || ''}
          onAtlasClick={handleCypherBoardClick}
          onSettingClick={() => {
            EntityAtlasInfoRun({
              graphId: atlasId,
              graphType: 'ENTITY',
            });
            setSettingsModalVisible(true);
          }}
          className={styles.headerContainer}
          titleClassName={styles.entityAtlasTitle}
          readonly={readonly}
        />
      </Header>

      <Content className={styles.contentContainer}>
        {showCypherBoard ? (
          <CypherBoard databaseId={atlasId} graphType="ENTITY" />
        ) : (
          <Splitter className={styles.splitter}>
            <Splitter.Panel className={styles.leftPanel}>
              <Tabs
                activeKey={activeTab}
                onChange={handleTabChange}
                size="small"
                items={[
                  {
                    key: 'entity',
                    label: '实体对象',
                    children: (
                      <EntityObjList atlasId={atlasId} readonly={readonly} ref={entityObjListRef} />
                    ),
                  },
                  {
                    key: 'relation',
                    label: '实体关系',
                    children: (
                      <EntityRelationList
                        atlasId={atlasId}
                        readonly={readonly}
                        ref={entityRelationListRef}
                      />
                    ),
                  },
                ]}
              />
              <Row className={styles.preview}>
                {/* <Button icon={<PlayCircleOutlined />}>预览</Button> */}
              </Row>
            </Splitter.Panel>

            <Splitter.Panel className={styles.rightPanel}>
              <CypherBoard 
                databaseId={atlasId} 
                graphType="ENTITY" 
                onQuerySuccess={handleCypherQuerySuccess}
                readonly={readonly}
              />
            </Splitter.Panel>
          </Splitter>
        )}
      </Content>

      {/* 设置弹窗 */}
      <SetModalPop
        visible={settingsModalVisible}
        onCancel={() => setSettingsModalVisible(false)}
        atlasInfo={entityAtlasInfo}
        refreshAtlasInfo={refreshAtlasInfo}
        type="entity"
        labelText="实体图谱"
        organizationId={organizationId}
        readonly={readonly}
      />
    </Layout>
  );
};

export default EditEntityGraph;
