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

export type PropertySaveVO = {
  /** 属性名称 */
  name?: string;
  /** 属性描述 */
  description?: string;
  /** 是否必填属性 */
  required?: ('REQUIRED' | 'NOT_REQUIRED');
  /** 属性类型 */
  type?: ('STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT');
}

export type RelationshipSaveVO = {
  /** 关系名称 */
  name?: string;
  /** 关系描述 */
  description?: string;
  /** 关系源ID */
  sourceId?: string;
  /** 关系目标ID */
  targetId?: string;
  /** 关系属性 */
  properties?: PropertySaveVO[];
  /** 图谱ID */
  atlasId?: string;
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

export type PropertyImplSaveVO = {
  /** 属性ID */
  id?: string;
  /** 属性名称 */
  name?: string;
  /** 属性描述 */
  description?: string;
  /** 是否必填属性 */
  required?: ('REQUIRED' | 'NOT_REQUIRED');
  /** 属性类型 */
  type?: ('STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT');
  /** 属性值 */
  value?: string;
}

export type RelationshipEntitySaveVO = {
  /** 实体关系ID */
  id?: string;
  /** 实体关系名称 */
  name?: string;
  /** 实体关系描述 */
  description?: string;
  /** 实体关系源ID */
  sourceId?: string;
  /** 实体关系目标ID */
  targetId?: string;
  /** 实体关系ID */
  relationshipId?: string;
  /** 实体关系属性 */
  properties?: PropertyImplSaveVO[];
  /** 图谱ID */
  atlasId?: string;
}

export type OntologySaveVO = {
  /** 本体名称 */
  name?: string;
  /** 本体描述 */
  description?: string;
  /** 图谱id */
  atlasId?: string;
  /** 属性列表 */
  properties?: PropertySaveVO[];
}

export type EntitySaveVO = {
  /** 实体ID */
  id?: string;
  /** 名称 */
  name?: string;
  /** 描述 */
  description?: string;
  /** 关联的本体ID */
  ontologyId?: string;
  /** 图谱ID */
  atlasId?: string;
  /** 属性列表 */
  properties?: PropertyImplSaveVO[];
}

export type AtlasSaveVO = {
  /** 图谱名称 */
  name?: string;
  /** 图谱描述 */
  description?: string;
  /** 关联本体图谱Id */
  linkAtlasId?: string;
  /** 图谱类型 */
  atlasType?: ('ONTOLOGY' | 'ENTITY');
  /** 所属项目Id */
  organizationId?: string;
}

export type ApiResultAtlasVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: AtlasVO;
  /**  */
  addition?: Addition;
}

export type AtlasVO = {
  /** 图谱ID */
  id?: string;
  /** 所属项目ID */
  organizationId?: string;
  /** 图谱名称 */
  name?: string;
  /** 图谱描述 */
  description?: string;
  /** 图谱是否已经关联实例 */
  hasImpl?: boolean;
  /** 关联本体图谱Id */
  linkAtlasId?: string;
  /** 关联本体图谱名称 */
  linkAtlasName?: string;
  /** 图谱数据 */
  atlasData?: any[];
  /** 图谱创建时间 */
  createTime?: string;
  /** 图谱更新时间 */
  updateTime?: string;
  /** 图谱类型 */
  type?: ('ONTOLOGY' | 'ENTITY');
}

export type PropertyImplUpdateVO = {
  /** 属性ID */
  id?: string;
  /** 属性名称 */
  name?: string;
  /** 属性描述 */
  description?: string;
  /** 是否必填属性 */
  required?: ('REQUIRED' | 'NOT_REQUIRED');
  /** 属性类型 */
  type?: ('STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT');
  /** 属性值 */
  value?: string;
  /**  */
  propertyIdMap?: any;
}

export type RelationshipEntityUpdateVO = {
  /** 实体关系ID */
  id?: string;
  /** 实体关系名称 */
  name?: string;
  /** 实体关系描述 */
  description?: string;
  /** 实体关系源ID */
  sourceId?: string;
  /** 实体关系目标ID */
  targetId?: string;
  /** 实体关系关联的本体关系ID */
  relationshipId?: string;
  /** 实体关系更新时间 */
  createTime?: string;
  /** 实体关系属性 */
  properties?: PropertyImplUpdateVO[];
  /** 属性ID映射Map */
  propertyIdMap?: any;
  /** 图谱ID */
  atlasId?: string;
}

export type RelationshipDeleteVO = {
  /** id列表 */
  idList?: string[];
  /** 图谱ID */
  atlasId?: string;
}

export type PropertyUpdateVO = {
  /**  */
  id?: string;
  /** 属性名称 */
  name?: string;
  /** 属性描述 */
  description?: string;
  /** 是否必填属性 */
  required?: ('REQUIRED' | 'NOT_REQUIRED');
  /** 属性类型 */
  type?: ('STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT');
}

export type RelationshipUpdateVO = {
  /** 关系ID */
  id?: string;
  /** 关系名称 */
  name?: string;
  /** 关系描述 */
  description?: string;
  /** 创建时间 */
  createTime?: string;
  /** 关系源ID */
  sourceId?: string;
  /** 关系目标ID */
  targetId?: string;
  /** 关系属性 */
  properties?: PropertyUpdateVO[];
  /** 图谱ID */
  atlasId?: string;
}

export type OrganizationSaveVO = {
  /** 名称 */
  name?: string;
  /**  */
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
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
  /** 项目内本体图谱数量 */
  ontologyGraphTotal?: number;
  /** 项目内实体图谱数量 */
  entityGraphTotal?: number;
}

export type OrganizationUpdateVO = {
  /** 项目id */
  id?: string;
  /** 项目名称 */
  name?: string;
  /** 项目描述 */
  description?: string;
}

export type CommonDeleteVO = {
  /** id列表 */
  idList?: string[];
}

export type ExportVO = {
  /** id列表 */
  idList?: string[];
  /** 图谱id */
  atlasId?: string;
}

export type ApiResultListJsonOntologyInfo = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: JsonOntologyInfo[];
  /**  */
  addition?: Addition;
}

export type JsonOntologyInfo = {
  /**  */
  ontology?: Ontology;
  /** 本体id */
  id?: string;
  /** 本体名称 */
  name?: string;
  /** 本体描述 */
  description?: string;
  /** 图谱id */
  atlasId?: string;
  /** 本体属性列表 */
  propertyList?: Property[];
}

export type Ontology = {
  /**  */
  id?: string;
  /**  */
  atlasId?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
  /**  */
  ontology?: Ontology;
}

export type Property = {
  /**  */
  id?: string;
  /**  */
  name?: string;
  /**  */
  type?: number;
  /**  */
  required?: number;
  /**  */
  description?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

export type OntologyUpdateVO = {
  /** 本体id */
  id?: string;
  /** 本体名称 */
  name?: string;
  /** 本体描述 */
  description?: string;
  /** 图谱id */
  atlasId?: string;
  /** 属性列表 */
  properties?: PropertyUpdateVO[];
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

export type OntologyDeleteVO = {
  /** id列表 */
  idList?: string[];
  /** 本体图谱ID */
  atlasId?: string;
}

export type ApiResultListJsonEntityInfo = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: JsonEntityInfo[];
  /**  */
  addition?: Addition;
}

export type JsonEntityInfo = {
  /**  */
  entity?: OntologyImpl;
  /** 实体属性列表 */
  propertyList?: PropertyImpl[];
}

export type OntologyImpl = {
  /**  */
  id?: string;
  /**  */
  ontologyId?: string;
  /**  */
  atlasId?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

export type PropertyImpl = {
  /**  */
  id?: string;
  /**  */
  name?: string;
  /**  */
  value?: string;
  /**  */
  propertyId?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
  /**  */
  propertyType?: ('STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT');
}

export type EntityUpdateVO = {
  /** 实体ID */
  id?: string;
  /** 名称 */
  name?: string;
  /** 描述 */
  description?: string;
  /** 关联的本体ID */
  ontologyId?: string;
  /** 图谱ID */
  atlasId?: string;
  /** 属性列表 */
  properties?: PropertyImplUpdateVO[];
  /** 创建时间 */
  createTime?: string;
}

export type EntityDeleteVO = {
  /** id列表 */
  idList?: string[];
  /** 实体图谱ID */
  atlasId?: string;
}

export type CypherQueryVO = {
  /** Cypher语句 */
  cypher?: string;
  /** 图谱id */
  databaseId?: string;
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

export type AtlasUpdateVO = {
  /** 图谱id */
  id?: string;
  /** 图谱名称 */
  name?: string;
  /** 图谱描述 */
  description?: string;
  /** 所属项目ID */
  organizationId?: string;
}

export type AtlasDeleteVO = {
  /** id列表 */
  idList?: string[];
  /** 图谱类型 */
  atlasType?: ('ONTOLOGY' | 'ENTITY');
  /** 项目ID */
  organizationId?: string;
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

export type PropertyVO = {
  /** id(属性、本体、实体、关系) */
  id?: string;
  /** 实体属性ID */
  propertyImplId?: string;
  /** 本体属性ID */
  propertyId?: string;
  /** 属性名称 */
  name?: string;
  /** 属性描述 */
  description?: string;
  /** 属性值 */
  value?: any;
  /** 属性类型 */
  type?: ('STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT');
  /** 是否必填属性(0: 非必填; 1: 必填) */
  required?: number;
  /** 创建时间 */
  createTime?: string;
}

export type RelationshipVO = {
  /** 关系id */
  id?: string;
  /** 源id(包括本体、实体) */
  sourceId?: string;
  /** 源名称 */
  sourceName?: string;
  /** 本体关系id */
  relationshipId?: string;
  /** 目标id(包括本体、实体) */
  targetId?: string;
  /** 目标名称 */
  targetName?: string;
  /** 关系名称 */
  name?: string;
  /** 关系描述 */
  description?: string;
  /** 关系属性 */
  properties?: PropertyVO[];
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
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

export type ApiResultListRelationshipEntityVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: RelationshipEntityVO[];
  /**  */
  addition?: Addition;
}

export type RelationshipEntityVO = {
  /**  */
  id?: string;
  /** 实体关系名称 */
  name?: string;
  /** 实体关系描述 */
  description?: string;
  /** 实体关系源ID */
  sourceId?: string;
  /** 实体关系源名称 */
  sourceName?: string;
  /** 实体关系目标ID */
  targetId?: string;
  /** 实体关系目标名称 */
  targetName?: string;
  /** 实体关系ID */
  relationshipId?: string;
  /** 实体关系属性 */
  properties?: PropertyVO[];
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
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

export type ApiResultOntologyVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: OntologyVO;
  /**  */
  addition?: Addition;
}

export type OntologyVO = {
  /** id */
  id?: string;
  /** 名称 */
  name?: string;
  /** 描述 */
  description?: string;
  /** 图谱ID */
  atlasId?: string;
  /** 图谱名称 */
  atlasName?: string;
  /** 属性列表 */
  properties?: PropertyVO[];
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

export type ApiResultListOntologyVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: OntologyVO[];
  /**  */
  addition?: Addition;
}

export type ApiResultOntologyImplVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /**  */
  data?: OntologyImplVO;
  /**  */
  addition?: Addition;
}

export type OntologyImplVO = {
  /** id */
  id?: string;
  /** 名称 */
  name?: string;
  /** 描述 */
  description?: string;
  /** 图谱ID */
  atlasId?: string;
  /** 图谱名称 */
  atlasName?: string;
  /** 属性列表 */
  properties?: PropertyVO[];
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
  /** 本体ID */
  ontologyId?: string;
  /** 本体名称 */
  ontologyName?: string;
}

export type ApiResultListAtlasVO = {
  /** 响应是否成功 */
  success?: boolean;
  /** 响应码 */
  code?: number;
  /** 响应信息 */
  message?: string;
  /** 响应数据 */
  data?: AtlasVO[];
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

export type Atlas = {
  /**  */
  id?: string;
  /**  */
  organizationId?: string;
  /**  */
  name?: string;
  /**  */
  description?: string;
  /**  */
  type?: number;
  /**  */
  linkAtlasId?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

export type JsonEntityRelationshipInfo = {
  /**  */
  relationship?: RelationshipImpl;
  /**  */
  propertyList?: PropertyImpl[];
}

export type JsonGraphInfo = {
  /**  */
  atlas?: Atlas;
  /** 本体列表 */
  ontologyList?: JsonOntologyInfo[];
  /** 本体关系列表 */
  relationshipInfoList?: JsonOntologyRelationshipInfo[];
  /** 实体列表 */
  jsonEntityInfoList?: JsonEntityInfo[];
  /** 实体关系列表 */
  jsonEntityRelationshipInfoList?: JsonEntityRelationshipInfo[];
}

export type JsonOntologyRelationshipInfo = {
  /** 本体关系id */
  id?: string;
  /** 本体关系源id */
  sourceId?: string;
  /** 本体关系目标id */
  targetId?: string;
  /** 本体关系名称 */
  name?: string;
  /** 本体关系描述 */
  description?: string;
  /** 本体关系创建时间 */
  createTime?: string;
  /** 本体关系更新时间 */
  updateTime?: string;
  /** 本体关系属性列表 */
  propertyList?: Property[];
}

export type RelationshipImpl = {
  /**  */
  id?: string;
  /**  */
  sourceImplId?: string;
  /**  */
  targetImplId?: string;
  /**  */
  relationshipId?: string;
  /**  */
  description?: string;
  /**  */
  createTime?: string;
  /**  */
  updateTime?: string;
}

