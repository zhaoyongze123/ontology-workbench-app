// 定义节点属性接口
export interface NodeProperties {
  [key: string]: any; // 允许任意属性
}

// 定义自定义节点数据接口
export interface NodeData extends Record<string, unknown> {
  identity: number;
  labels: string[];
  properties: NodeProperties;
}

// 定义自定义边数据接口
export interface EdgeData extends Record<string, unknown> {
  label?: string; // 使label字段可选
  id?: string;
  offset?: number; // 添加偏移量属性
  totalEdges?: number; // 添加总边数属性
  edgeIndex?: number; // 添加边索引属性
  properties?: Record<string, any>; // 允许任意属性
}
