import { request } from 'umi'; 
import {
  PropertySchema,
  ApiResultVoid,
  ApiResultRelationshipVO,
  ApiResultListRelationshipVO,
  ApiResultListOptionalVO,
} from './typings';

/**
 * 修改关系 PUT /api/v1/relationship/edit
 */
export async function editRelationship (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/edit`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 保存关系 POST /api/v1/relationship/save
 */
export async function saveRelationship (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultRelationshipVO>(`/api/v1/relationship/save`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 分页查询关系 POST /api/v1/relationship/page
 */
export async function pageRelationship (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListRelationshipVO>(`/api/v1/relationship/page`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 删除节点关系对象 POST /api/v1/relationship/delete
 */
export async function deleteRelationship (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 图谱类型 */
    graphType?: ('ONTOLOGY' | 'ENTITY');
    /** 图谱id */
    graphId?: string;
    /**  */
    keyword?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/delete`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 获取关系可选对象 GET /api/v1/relationship/optional
 */
export async function getRelationshipOptional (
  params?: {
    /**  */
    graphId: string;
    /**  */
    graphType: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListOptionalVO>(`/api/v1/relationship/optional`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取关联的本体关系列表 GET /api/v1/relationship/link/getOntologyRelationshipList
 */
export async function getLinkOntologyRelationshipList (
  params?: {
    /** 源节点id */
    sourceNodeId: string;
    /** 目标节点id */
    targetNodeId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListOptionalVO>(`/api/v1/relationship/link/getOntologyRelationshipList`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取关系信息 GET /api/v1/relationship/info
 */
export async function getRelationshipInfo (
  params?: {
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
    /** 关系id */
    relationshipId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultRelationshipVO>(`/api/v1/relationship/info`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
