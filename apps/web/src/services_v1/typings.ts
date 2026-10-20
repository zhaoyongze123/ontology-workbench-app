export type Addition = {
  /** 总数量 */
  total?: number;
  /** 当前页 */
  pageNum?: number;
  /** 总页数 */
  pageSize?: number;
}

export type ApiResultObject = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: any;
  /**  */
  addition?: Addition;
}

export type PropertySchema = {
  /** 属性名称 */
  name?: string;
  /** 属性类型 */
  type?: ('STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT');
  /** 属性描述 */
  description?: string;
  /** 属性是否必填 */
  required?: ('REQUIRED' | 'NOT_REQUIRED');
}

export type RelationshipUpdateVO = {
  /** 关系id */
  id?: string;
  /** 实体关系关联本体关系id */
  linkOntologyRelationshipId?: string;
  /** 源节点id */
  sourceNodeId?: string;
  /** 目标节点id */
  targetNodeId?: string;
  /** 图谱id */
  graphId?: string;
  /** 关系名称 */
  name?: string;
  /** 关系描述 */
  description?: string;
  /** 本体关系属性Schema（实体关系属性JSON格式） */
  propertySchemaJson?: PropertySchema[];
  /** 实体关系属性Json */
  propertyJson?: string;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
}

export type ApiResultVoid = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: any;
  /**  */
  addition?: Addition;
}

export type OrganizationUpdateVO = {
  /** 项目id */
  id?: string;
  /** 项目名称 */
  name?: string;
  /** 项目描述 */
  description?: string;
}

export type NodeUpdateVO = {
  /** 节点对象id */
  id?: string;
  /** 实体节点关联的本体节点对象id */
  linkOntologyId?: string;
  /** 节点对象名称 */
  name?: string;
  /** 节点对象描述 */
  description?: string;
  /** 节点对象属性JSON格式定义 */
  propertySchemaJson?: PropertySchema[];
  /** 节点对象属性JSON */
  propertyJson?: string;
  /** 图谱id */
  graphId?: string;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
}

export type GraphUpdateVO = {
  /** 图谱id */
  id?: string;
  /** 图谱名称 */
  name?: string;
  /** 图谱描述 */
  description?: string;
  /** 图谱类型（ONTOLOGY: 本体图谱；ENTITY: 实体图谱） */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 实体图谱关联本体图谱id */
  linkGraphId?: string;
  /** 项目id */
  organizationId?: string;
}

export type RelationshipSaveVO = {
  /** 实体关系关联本体关系id */
  linkOntologyRelationshipId?: string;
  /** 源节点id */
  sourceNodeId?: string;
  /** 目标节点id */
  targetNodeId?: string;
  /** 图谱id */
  graphId?: string;
  /** 关系名称 */
  name?: string;
  /** 关系描述 */
  description?: string;
  /** 本体关系属性Schema（实体关系属性JSON格式） */
  propertySchemaJson?: PropertySchema[];
  /** 实体关系属性Json */
  propertyJson?: string;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
}

export type ApiResultRelationshipVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: RelationshipVO;
  /**  */
  addition?: Addition;
}

export type RelationshipVO = {
  /** 关系id */
  id?: string;
  /** 源节点id */
  sourceNodeId?: string;
  /** 源节点名称 */
  sourceNodeName?: string;
  /** 目标节点id */
  targetNodeId?: string;
  /** 目标节点名称 */
  targetNodeName?: string;
  /** 关系名称 */
  name?: string;
  /** 关系描述 */
  description?: string;
  /** 实体关系关联本体id */
  linkOntologyId?: string;
  /** 实体关系关联本体名称 */
  linkOntologyName?: string;
  /** 关系属性Schema（本体关系属性JSON格式） */
  propertySchemaJson?: PropertySchema[];
  /** 实体关系Json */
  propertyJson?: string;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

export type RelationshipQueryVO = {
  /** 当前页码 */
  pageNum?: number;
  /** 每页数量 */
  pageSize?: number;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 图谱id */
  graphId?: string;
  /** 关键词 */
  keyword?: string;
  /** 关系idList */
  relationshipIdList?: string[];
}

export type ApiResultListRelationshipVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: RelationshipVO[];
  /**  */
  addition?: Addition;
}

export type RelationshipDeleteVO = {
  /** id列表 */
  idList?: string[];
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 图谱id */
  graphId?: string;
  /**  */
  keyword?: string;
}

export type OrganizationSaveVO = {
  /** 项目名称 */
  name?: string;
  /** 项目描述 */
  description?: string;
}

export type ApiResultOrganizationVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: OrganizationVO;
  /**  */
  addition?: Addition;
}

export type OrganizationVO = {
  /** 项目id */
  id?: string;
  /** 项目名称 */
  name?: string;
  /** 项目描述 */
  description?: string;
  /** 本体图谱数量 */
  ontologyGraphTotal?: number;
  /** 实体图谱数量 */
  entityGraphTotal?: number;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

export type NodeSaveVO = {
  /** 节点对象名称 */
  name?: string;
  /** 节点对象描述 */
  description?: string;
  /** 实体节点关联本体节点对象id */
  linkOntologyId?: string;
  /** 本体节点对象属性JSON格式定义 */
  propertySchemaJson?: PropertySchema[];
  /** 实体节点对象属性JSON */
  propertyJson?: string;
  /** 图谱id */
  graphId?: string;
  /** 节点对象类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
}

export type NodeQueryVO = {
  /** 当前页码 */
  pageNum?: number;
  /** 每页数量 */
  pageSize?: number;
  /** 图谱id */
  graphId?: string;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 关键词 */
  keyword?: string;
  /** 节点id列表 */
  nodeIdList?: string[];
  /** 关联本体节点id列表 */
  linkOntologyNodeIdList?: string[];
}

export type ApiResultListNodeVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: NodeVO[];
  /**  */
  addition?: Addition;
}

export type NodeVO = {
  /** 节点对象id */
  id?: string;
  /** 实体对象关联的本体对象id */
  linkOntologyId?: string;
  /** 实体对象关联的本体对象名称 */
  linkOntologyName?: string;
  /** 节点对象名称 */
  name?: string;
  /** 节点对象描述 */
  description?: string;
  /** 节点对象属性JSON格式定义 */
  propertySchemaJson?: PropertySchema[];
  /** 节点对象属性JSON */
  propertyJson?: string;
  /** 图谱id */
  graphId?: string;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

export type NodeDeleteVO = {
  /** id列表 */
  idList?: string[];
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 图谱id */
  graphId?: string;
}

export type GraphSaveVO = {
  /** 图谱名称 */
  name?: string;
  /** 图谱描述 */
  description?: string;
  /** 图谱类型（ONTOLOGY: 本体图谱；ENTITY: 实体图谱） */
  graphType?: ('ONTOLOGY' | 'ENTITY');
  /** 实体图谱关联本体图谱id */
  linkGraphId?: string;
  /** 项目id */
  organizationId?: string;
}

export type ApiResultGraphVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: GraphVO;
  /**  */
  addition?: Addition;
}

export type GraphVO = {
  /** 图谱id */
  id?: string;
  /** 图谱名称 */
  name?: string;
  /** 图谱描述 */
  description?: string;
  /** 图谱类型（ONTOLOGY: 本体图谱；ENTITY: 实体图谱） */
  type?: string;
  /** 实体图谱关联本体图谱id */
  linkGraphId?: string;
  /** 实体图谱关联本体图谱名称 */
  linkGraphName?: string;
  /** 是否含有实体图谱 */
  hasEntity?: boolean;
  /** 项目id */
  organizationId?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

export type CypherQueryVO = {
  /** Cypher语句 */
  cypher?: string;
  /** 图谱id */
  graphId?: string;
  /** 图谱类型 */
  graphType?: ('ONTOLOGY' | 'ENTITY');
}

export type ApiResultCollectionMapStringObject = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: any[];
  /**  */
  addition?: Addition;
}

export type ApiResultListOptionalVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: OptionalVO[];
  /**  */
  addition?: Addition;
}

export type OptionalVO = {
  /** 可选项ID */
  id?: string;
  /** 可选项名称 */
  name?: string;
  /** 可选项描述 */
  description?: string;
}

export type ApiResultListOrganizationVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: OrganizationVO[];
  /**  */
  addition?: Addition;
}

export type ApiResultNodeVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: NodeVO;
  /**  */
  addition?: Addition;
}

export type ApiResultListGraphVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: GraphVO[];
  /**  */
  addition?: Addition;
}

export type ApiResultJsonGraphInfo = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: JsonGraphInfo;
  /**  */
  addition?: Addition;
}

export type Entity = {
  /**  */
  id?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  propertyJson?: string;
  /**  */
  ontologyId?: string;
  /**  */
  graphId?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

export type EntityRelationship = {
  /**  */
  id?: string;
  /**  */
  sourceNodeId?: string;
  /**  */
  targetNodeId?: string;
  /**  */
  graphId?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  propertyJson?: string;
  /**  */
  ontologyRelationshipId?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

export type Graph = {
  /**  */
  id?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  type?: string;
  /**  */
  linkGraphId?: string;
  /**  */
  organizationId?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
  /**  */
  linkOntologyGraph?: Graph;
}

export type JsonGraphInfo = {
  /**  */
  graph?: Graph;
  /**  */
  ontologyList?: Ontology[];
  /**  */
  entityList?: Entity[];
  /**  */
  ontologyRelationshipList?: OntologyRelationship[];
  /**  */
  entityRelationshipList?: EntityRelationship[];
}

export type Ontology = {
  /**  */
  id?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  propertySchemaJson?: string;
  /**  */
  graphId?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

export type OntologyRelationship = {
  /**  */
  id?: string;
  /**  */
  sourceNodeId?: string;
  /**  */
  targetNodeId?: string;
  /**  */
  graphId?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  propertySchemaJson?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

