import AtlasButtons from '@/components/AtlasButtons';
import SetModalPop from '@/components/SetModalPop';
import CypherBoard from '@/pages/cypherBoard';
import { useProjectAccess } from '@/features/projects/useProjectAccess';
import { getGraphInfo } from '@/services_v1/graphService';
import { useRequest } from '@umijs/max';
import { Layout, Row, Splitter, Tabs } from 'antd';
import React, { useRef, useState } from 'react';
import { useLocation, useParams } from 'umi';
import OntoObjList from './OntoObjList';
import OntoRelationList from './OntoRelationList';
import styles from './index.less';

const { Content, Header } = Layout;

/** 编辑本体图谱 */
const EditOntologyGraph: React.FC = () => {
  const params = useParams<{ id: string; graphId: string }>();
  const location = useLocation();
  const routeReadonly =
    (location.state as { readonly?: boolean })?.readonly ?? false;
  const organizationId = params.id || '';
  const { can } = useProjectAccess(organizationId);
  const canEdit = can('EDIT');
  const readonly = routeReadonly || !canEdit;
  const atlasId = params.graphId || '';
  const [activeTab, setActiveTab] = useState('entity');
  const [settingsModalVisible, setSettingsModalVisible] = useState(false);
  const [showCypherBoard, setShowCypherBoard] = useState(false);

  /** 创建引用以访问子组件的方法 */
  const ontoObjListRef = useRef<{
    refreshOntoData: (nodeIdList?: string[]) => void;
  }>(null);
  const ontoRelationListRef = useRef<{
    refreshRelationData: (relationshipIdList?: string[]) => void;
  }>(null);

  /** 获取本体图谱信息 设置弹窗信息回填 */
  const {
    data: atlasInfoData,
    run: OntoAtlasInfo,
    refresh: refreshAtlasInfo,
  } = useRequest(getGraphInfo, {
    defaultParams: [
      {
        graphId: atlasId,
        graphType: 'ONTOLOGY',
      },
    ],
    formatResult: (res) => res.data,
  });

  /** 处理标签页切换，刷新对应的数据 */
  const handleTabChange = (key: string) => {
    setActiveTab(key);
    /** 根据当前激活的标签页刷新对应的数据 */
    if (key === 'entity' && ontoObjListRef.current) {
      ontoObjListRef.current.refreshOntoData();
    } else if (key === 'relation' && ontoRelationListRef.current) {
      ontoRelationListRef.current.refreshRelationData();
    }
  };

  /** 处理本体图谱按钮点击 */
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
    if (activeTab === 'entity' && ontoObjListRef.current) {
      ontoObjListRef.current.refreshOntoData(nodeIds);
    } else if (activeTab === 'relation' && ontoRelationListRef.current) {
      ontoRelationListRef.current.refreshRelationData(relationshipIds);
    }
  };

  return (
    <Layout className={styles.editOntology}>
      <Header className={styles.header}>
        <AtlasButtons
          type="onto"
          atlasName={atlasInfoData?.name ?? ''}
          onAtlasClick={handleCypherBoardClick}
          onSettingClick={() => {
            OntoAtlasInfo({
              graphId: atlasId,
              graphType: 'ONTOLOGY',
            });
            setSettingsModalVisible(true);
          }}
          className={styles.headerContainer}
          titleClassName={styles.ontoAtlasTitle}
          readonly={!canEdit}
        />
      </Header>

      <Content className={styles.contentContainer}>
        {showCypherBoard ? (
          <CypherBoard databaseId={atlasId} graphType="ONTOLOGY" />
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
                    label: '本体对象',
                    children: (
                      <OntoObjList
                        atlasId={atlasId}
                        readonly={routeReadonly}
                        canMutate={canEdit}
                        ref={ontoObjListRef}
                      />
                    ),
                  },
                  {
                    key: 'relation',
                    label: '本体关系',
                    children: (
                      <OntoRelationList
                        atlasId={atlasId}
                        readonly={routeReadonly}
                        canMutate={canEdit}
                        ref={ontoRelationListRef}
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
                graphType="ONTOLOGY" 
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
        atlasInfo={atlasInfoData}
        refreshAtlasInfo={refreshAtlasInfo}
        type="onto"
        labelText="本体图谱"
        organizationId={organizationId}
        readonly={!canEdit}
      />
    </Layout>
  );
};

export default EditOntologyGraph;
