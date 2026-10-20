import {
  Background,
  Controls,
  MarkerType,
  MiniMap,
  ReactFlow,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
  type EdgeMouseHandler,
  type Node,
  type NodeMouseHandler,
  type OnNodeDrag,
  type ReactFlowInstance,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { message, Row } from 'antd';
import {
  forceCenter,
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  forceX,
  forceY,
  type Simulation,
  type SimulationNodeDatum,
} from 'd3-force';
import { useCallback, useEffect, useRef, useState } from 'react';
import { extractNodesAndRelationships } from '../../utils/util';
import CustomEdge from './components/CustomEdge';
import CustomNode from './components/CustomNode';
import EdgeCard from './components/EdgeDrawer';
import NodeCard from './components/NodeDrawer';
import styles from './index.less';
import type { EdgeData, NodeData } from './type';

/** 力导向布局配置 */
const FORCE_CONFIG = {
  chargeStrength: -300, // 斥力强度，负值越大越排斥
  linkDistance: 180, // 连线距离
  center: { x: 500, y: 300 }, // 布局中心点
  connectedStrength: 0.02, // 已连接节点的向心力强度
  isolatedStrength: 0.04, // 孤立节点的向心力强度
  collideRadius: 60, // 碰撞半径，防止节点重叠
  simulationIterations: 300, // 模拟迭代次数
} as const;

/** 默认边样式配置 */
const DEFAULT_EDGE_STYLE = {
  strokeWidth: 1.5,
} as const;

/** 默认箭头配置 */
const DEFAULT_MARKER_END = {
  type: MarkerType.ArrowClosed,
  width: 20,
  height: 20,
} as const;

/** 模拟节点类型 */
interface SimulationNode extends SimulationNodeDatum {
  id: string;
  x: number;
  y: number;
  fx?: number | null;
  fy?: number | null;
}

const NODE_TYPES = { customNode: CustomNode } as const; // 节点类型映射
const EDGE_TYPES = { customEdge: CustomEdge } as const; // 边类型映射
/** 初始状态 */
const INITIAL_NODES: Node<NodeData>[] = [];
const INITIAL_EDGES: Edge<EdgeData>[] = [];

/** 为边添加默认样式 */
const createStyledEdge = (edge: Edge<EdgeData>): Edge<EdgeData> => ({
  ...edge,
  type: 'customEdge',
  markerEnd: DEFAULT_MARKER_END,
  labelShowBg: false,
  style: DEFAULT_EDGE_STYLE,
});
/** 生成唯一ID */
const generateId = (): string =>
  `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
/** 获取节点间连接的唯一键 */
const getEdgeKey = (source: string, target: string): string =>
  [source, target].sort().join('-');
/** 获取已连接节点ID集合 */
const getConnectedNodeIds = (edges: Edge<EdgeData>[]): Set<string> => {
  const ids = new Set<string>();
  edges.forEach((edge) => {
    ids.add(edge.source);
    ids.add(edge.target);
  });
  return ids;
};

/** 创建力导向模拟器 */
const createForceSimulation = (
  nodes: SimulationNode[],
  edges: { source: string; target: string }[],
  connectedNodeIds: Set<string>,
  onTick?: () => void,
): Simulation<SimulationNode, undefined> => {
  const {
    chargeStrength,
    linkDistance,
    center,
    connectedStrength,
    isolatedStrength,
    collideRadius,
  } = FORCE_CONFIG;

  const getStrength = (d: SimulationNodeDatum) =>
    connectedNodeIds.has((d as SimulationNode).id)
      ? connectedStrength
      : isolatedStrength;
  const simulation = forceSimulation<SimulationNode>(nodes)
    .force('charge', forceManyBody().strength(chargeStrength))
    .force(
      'link',
      forceLink(edges)
        .id((d: any) => d.id)
        .distance(linkDistance),
    )
    .force('center', forceCenter(center.x, center.y))
    .force('x', forceX(center.x).strength(getStrength))
    .force('y', forceY(center.y).strength(getStrength))
    .force('collide', forceCollide(collideRadius));
  if (onTick) {
    simulation.on('tick', onTick);
  }
  return simulation;
};

/** 计算静态力导向布局 */
const getLayoutedElements = (
  nodes: Node<NodeData>[],
  edges: Edge<EdgeData>[],
): { nodes: Node<NodeData>[]; edges: Edge<EdgeData>[] } => {
  const simulationNodes: SimulationNode[] = nodes.map((node) => ({
    id: node.id,
    x: node.position.x || 0,
    y: node.position.y || 0,
  }));

  const simulationEdges = edges.map((edge) => ({
    source: edge.source,
    target: edge.target,
  }));

  const connectedNodeIds = getConnectedNodeIds(edges);
  const simulation = createForceSimulation(
    simulationNodes,
    simulationEdges,
    connectedNodeIds,
  );
  simulation.stop();
  /** 预热模拟，计算最终位置 */
  for (let i = 0; i < FORCE_CONFIG.simulationIterations; i++) {
    simulation.tick();
  }
  const nodePositionMap = new Map(
    simulationNodes.map((n) => [n.id, { x: n.x, y: n.y }]),
  );
  const layoutedNodes = nodes.map((node) => ({
    ...node,
    position: nodePositionMap.get(node.id) || node.position,
  }));
  return { nodes: layoutedNodes, edges };
};

function FlowComponent({
  cypherData,
  graphType,
  graphId,
  onDataChange,
  readonly,
}: {
  cypherData: any;
  graphType?: 'ONTOLOGY' | 'ENTITY';
  graphId?: string;
  onDataChange?: () => void;
  readonly?: boolean;
}) {
  const [nodes, setNodes, onNodesChange] = useNodesState(INITIAL_NODES);
  const [edges, setEdges, onEdgesChange] = useEdgesState(INITIAL_EDGES);
  const [rfInstance, setRfInstance] = useState<ReactFlowInstance<
    Node<NodeData>,
    Edge<EdgeData>
  > | null>(null);
  /** 抽屉状态 */
  const [selectedNode, setSelectedNode] = useState<Node<NodeData> | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<Edge<EdgeData> | null>(null);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [activeDrawerType, setActiveDrawerType] = useState<
    'node' | 'edge' | null
  >(null);
  /** 工具状态 */
  const [messageApi, contextHolder] = message.useMessage();
  const simulationRef = useRef<Simulation<SimulationNode, undefined> | null>(
    null,
  );
  /** 用于避免 useEffect 依赖完整对象 */
  const nodesRef = useRef(nodes);
  const edgesRef = useRef(edges);
  nodesRef.current = nodes;
  edgesRef.current = edges;

  /** 初始化/更新力导向模拟 */
  useEffect(() => {
    if (nodes.length === 0) return;
    const simulationNodes: SimulationNode[] = nodes.map((node) => ({
      id: node.id,
      x: node.position.x || 0,
      y: node.position.y || 0,
    }));
    const simulationEdges = edges.map((edge) => ({
      source: edge.source,
      target: edge.target,
    }));
    const connectedNodeIds = getConnectedNodeIds(edges);
    const nodeMap = new Map(simulationNodes.map((n) => [n.id, n]));
    simulationRef.current?.stop(); // 停止旧模拟
    /** 创建新模拟 */
    simulationRef.current = createForceSimulation(
      simulationNodes,
      simulationEdges,
      connectedNodeIds,
      () => {
        setNodes((nds) =>
          nds.map((node) => {
            const d3Node = nodeMap.get(node.id);
            if (!d3Node) return node;
            // 如果节点正在被拖拽（有 fx/fy），则保留其位置
            if (d3Node.fx != null) return node;
            return {
              ...node,
              position: { x: d3Node.x, y: d3Node.y },
            };
          }),
        );
      },
    );
    return () => {
      simulationRef.current?.stop();
    };
  }, [nodes.length, edges.length, setNodes]);

  /** 查找模拟中的节点 */
  const findSimulationNode = (nodeId: string): SimulationNode | undefined => {
    return simulationRef.current?.nodes().find((n) => n.id === nodeId);
  };

  /** 节点拖拽开始 */
  const onNodeDragStart = useCallback<OnNodeDrag<Node<NodeData>>>((_, node) => {
    if (!simulationRef.current) return;
    simulationRef.current.alphaTarget(0.3).restart();
    const d3Node = findSimulationNode(node.id);
    if (d3Node) {
      d3Node.fx = node.position.x;
      d3Node.fy = node.position.y;
    }
  }, []);

  /** 节点拖拽中 */
  const onNodeDrag = useCallback<OnNodeDrag<Node<NodeData>>>((_, node) => {
    const d3Node = findSimulationNode(node.id);
    if (d3Node) {
      d3Node.fx = node.position.x;
      d3Node.fy = node.position.y;
    }
  }, []);

  /** 节点拖拽结束 */
  const onNodeDragStop = useCallback<OnNodeDrag<Node<NodeData>>>((_, node) => {
    if (!simulationRef.current) return;
    simulationRef.current.alphaTarget(0);
    const d3Node = findSimulationNode(node.id);
    if (d3Node) {
      // 保持 fx/fy 固定位置，让节点停留在拖拽结束的位置
      d3Node.fx = node.position.x;
      d3Node.fy = node.position.y;
    }
  }, []);

  /** 连接节点 */
  const onConnect = useCallback(
    (params: Connection) => {
      if (!params.source || !params.target) return;
      // 使用函数式更新获取当前 edges 状态，避免依赖完整 edges 对象
      setEdges((eds) => {
        const edgeKey = getEdgeKey(params.source!, params.target!);
        const existingEdges = eds.filter(
          (edge) => getEdgeKey(edge.source, edge.target) === edgeKey,
        );
        const offset = existingEdges.length;
        const newEdge: Edge<EdgeData> = createStyledEdge({
          id: `e-${generateId()}`,
          source: params.source!,
          target: params.target!,
          sourceHandle: params.sourceHandle,
          targetHandle: params.targetHandle,
          data: {
            label: 'Relationship',
            offset,
            totalEdges: offset + 1,
            edgeIndex: offset,
          },
          label: 'Relationship',
        });
        return [...eds, newEdge];
      });
    },
    [setEdges],
  );

  /** 更新边的 totalEdges 信息（处理平行边） */
  useEffect(() => {
    if (edges.length === 0) return;
    const timer = setTimeout(() => {
      setEdges((prevEdges) => {
        const groupCounts = new Map<string, number>();
        const edgeIndices = new Map<string, number>();
        // 1. 第一遍遍历：计算分组总数和每条边的索引
        prevEdges.forEach((edge) => {
          const key = getEdgeKey(edge.source, edge.target);
          const count = groupCounts.get(key) || 0;
          edgeIndices.set(edge.id, count);
          groupCounts.set(key, count + 1);
        });
        // 2. 检查是否有边需要更新
        const needsUpdate = prevEdges.some((edge) => {
          const key = getEdgeKey(edge.source, edge.target);
          const currentTotal = groupCounts.get(key);
          const currentIndex = edgeIndices.get(edge.id);
          const { totalEdges, edgeIndex } = edge.data || {};
          return totalEdges !== currentTotal || edgeIndex !== currentIndex;
        });
        if (!needsUpdate) return prevEdges;
        // 3. 生成新数组
        return prevEdges.map((edge) => {
          const key = getEdgeKey(edge.source, edge.target);
          const totalEdges = groupCounts.get(key)!;
          const edgeIndex = edgeIndices.get(edge.id)!;
          let label = '';
          if (typeof edge.data?.label === 'string') {
            label = edge.data.label;
          } else if (typeof edge.label === 'string') {
            label = edge.label;
          }
          return {
            ...edge,
            data: {
              ...(edge.data || {}),
              totalEdges,
              edgeIndex,
              label,
            },
          };
        });
      });
    }, 100);

    return () => clearTimeout(timer);
  }, [edges.length, setEdges]);

  /** 数据初始化：加载 cypherData */
  useEffect(() => {
    if (!Array.isArray(cypherData) || cypherData.length === 0) return;
    try {
      const { nodes: extractedNodes, relationships: extractedRelationships } =
        extractNodesAndRelationships(cypherData);
      if (!extractedNodes?.length && !extractedRelationships?.length) {
        messageApi.warning('数据格式不支持图形化展示，将显示原始数据');
        return;
      }
      // 1. 处理节点 (使用 Map 去重)
      const nodeMap = new Map<string, Node<NodeData>>();
      extractedNodes?.forEach((node) => {
        const id = String(node.identity);
        if (!nodeMap.has(id)) {
          nodeMap.set(id, {
            id,
            type: 'customNode',
            data: {
              identity: node.identity,
              labels: node.labels || [],
              properties: node.properties || {},
            },
            position: {
              x: Math.random() * 400 + 50,
              y: Math.random() * 400 + 50,
            },
          });
        }
      });
      // 2. 处理关系 (分组计算 totalEdges)
      const edges: Edge<EdgeData>[] = [];
      const edgeGroups = new Map<string, any[]>();
      extractedRelationships?.forEach((rel) => {
        const key = getEdgeKey(String(rel.start), String(rel.end));
        const group = edgeGroups.get(key) || [];
        group.push(rel);
        edgeGroups.set(key, group);
      });
      edgeGroups.forEach((rels) => {
        const totalEdges = rels.length;
        rels.forEach((rel, index) => {
          edges.push(
            createStyledEdge({
              id: String(rel.identity),
              label: rel.type ?? '',
              source: String(rel.start),
              target: String(rel.end),
              data: {
                label: rel.type ?? '',
                offset: index,
                totalEdges,
                edgeIndex: index,
                properties: rel.properties || {},
              },
            }),
          );
        });
      });
      // 3. 应用布局
      const { nodes: layoutedNodes, edges: layoutedEdges } =
        getLayoutedElements(Array.from(nodeMap.values()), edges);
      setNodes(layoutedNodes);
      setEdges(layoutedEdges);
      // 4. 适应视图
      requestAnimationFrame(() => {
        rfInstance?.fitView({ padding: 0.2, maxZoom: 0.6 });
      });
    } catch (error) {
      console.error('Graph Data Load Error:', error);
      messageApi.error('加载节点关系数据失败');
    }
  }, [cypherData, rfInstance, setNodes, setEdges, messageApi]);

  /** 打开抽屉的通用方法（普通函数，不直接传递给子组件） */
  const openDrawer = (
    type: 'node' | 'edge',
    item: Node<NodeData> | Edge<EdgeData>,
  ) => {
    setSelectedNode(type === 'node' ? (item as Node<NodeData>) : null);
    setSelectedEdge(type === 'edge' ? (item as Edge<EdgeData>) : null);
    setActiveDrawerType(type);
    setDrawerVisible(true);
  };

  /** 关闭抽屉的通用方法 */
  const closeDrawer = useCallback(() => {
    setDrawerVisible(false);
    setSelectedNode(null);
    setSelectedEdge(null);
    setActiveDrawerType(null);
  }, []);

  /** 双击节点 */
  const onNodeDoubleClick = useCallback<NodeMouseHandler>(
    (_, node) => openDrawer('node', node as Node<NodeData>),
    [],
  );

  /** 双击边 */
  const onEdgeDoubleClick = useCallback<EdgeMouseHandler>(
    (_, edge) => openDrawer('edge', edge as Edge<EdgeData>),
    [],
  );

  /** 处理节点删除 */
  const handleNodeDeleted = useCallback(
    (nodeId: string) => {
      setNodes((nds) => nds.filter((node) => node.id !== nodeId));
      setEdges((eds) =>
        eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId),
      );
    },
    [setNodes, setEdges],
  );

  /** 处理边删除 */
  const handleEdgeDeleted = useCallback(
    (edgeId: string) => {
      setEdges((eds) => eds.filter((edge) => edge.id !== edgeId));
    },
    [setEdges],
  );

  return (
    <Row className={styles.flowContainer}>
      {contextHolder}
      <Row className={styles.flowContent}>
        <ReactFlow
          onInit={setRfInstance}
          nodes={nodes}
          edges={edges}
          nodeTypes={NODE_TYPES}
          edgeTypes={EDGE_TYPES}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onNodeDragStart={onNodeDragStart}
          onNodeDrag={onNodeDrag}
          onNodeDragStop={onNodeDragStop}
          onNodeDoubleClick={onNodeDoubleClick}
          onEdgeDoubleClick={onEdgeDoubleClick}
          style={{ backgroundColor: '#F5F5F5' }}
          defaultViewport={{ x: 500, y: 50, zoom: 0.6 }}
          minZoom={0.1}
          fitView
          deleteKeyCode={null}
          proOptions={{ hideAttribution: true }}
          onlyRenderVisibleElements
        >
          <Controls />
          <MiniMap
            style={{ backgroundColor: '#F5F5F5' }}
            nodeColor="#7f4cef"
            nodeStrokeColor="#fff"
            nodeStrokeWidth={2}
            nodeComponent={({ x, y, width, height }) => (
              <circle
                cx={x + width / 2}
                cy={y + height / 2}
                r={Math.min(width, height) / 2}
                fill="#7f4cef"
                stroke="#fff"
                strokeWidth="2"
              />
            )}
            pannable
            zoomable
          />
          <Background />
        </ReactFlow>
      </Row>
      {/* 节点卡片 */}
      <NodeCard
        visible={drawerVisible && activeDrawerType === 'node'}
        selectedNode={selectedNode}
        onClose={closeDrawer}
        graphType={graphType}
        graphId={graphId}
        onNodeDeleted={handleNodeDeleted}
        onDataChange={onDataChange}
        readonly={readonly}
      />
      {/* 边卡片 */}
      <EdgeCard
        visible={drawerVisible && activeDrawerType === 'edge'}
        selectedEdge={selectedEdge}
        onClose={closeDrawer}
        graphType={graphType}
        graphId={graphId}
        onEdgeDeleted={handleEdgeDeleted}
        onDataChange={onDataChange}
        readonly={readonly}
      />
    </Row>
  );
}

export default FlowComponent;
