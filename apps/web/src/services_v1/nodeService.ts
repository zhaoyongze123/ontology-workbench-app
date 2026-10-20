import { request } from 'umi'; 
import {
  PropertySchema,
  ApiResultVoid,
  ApiResultListNodeVO,
  ApiResultListOptionalVO,
  ApiResultNodeVO,
} from './typings';

/**
 * 修改节点对象 PUT /api/v1/node/editNode
 */
export async function editNode (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/node/editNode`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 保存节点对象 POST /api/v1/node/saveNode
 */
export async function saveNode (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/node/saveNode`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 分页查询节点对象 POST /api/v1/node/page
 */
export async function pageNode (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListNodeVO>(`/api/v1/node/page`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 删除节点对象 POST /api/v1/node/deleteNode
 */
export async function deleteNode (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 图谱类型 */
    graphType?: ('ONTOLOGY' | 'ENTITY');
    /** 图谱id */
    graphId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/node/deleteNode`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 获取节点可选对象 GET /api/v1/node/optional
 */
export async function getNodeOptional (
  params?: {
    /** 图谱id */
    graphId: string;
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListOptionalVO>(`/api/v1/node/optional`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取节点对象信息 GET /api/v1/node/getNodeInfo
 */
export async function getNodeInfo (
  params?: {
    /** 节点id */
    nodeId: string;
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultNodeVO>(`/api/v1/node/getNodeInfo`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
