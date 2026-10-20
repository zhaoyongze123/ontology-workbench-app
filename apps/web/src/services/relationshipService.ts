import { request } from 'umi'; 
import {
  PropertySaveVO,
  ApiResultVoid,
  PropertyImplSaveVO,
  PropertyImplUpdateVO,
  PropertyUpdateVO,
  ApiResultListRelationshipVO,
  ApiResultRelationshipVO,
  ApiResultListRelationshipEntityVO,
} from './typings';

/**
 * 保存本体关系 PUT /api/v1/relationship/save
 */
export async function saveRelationship (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/save`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 保存实体关系 PUT /api/v1/relationship/entity/save
 */
export async function saveRelationshipEntity (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/entity/save`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 修改实体关系 POST /api/v1/relationship/entity/edit
 */
export async function editRelationshipEntity (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/entity/edit`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 批量删除实体关系 POST /api/v1/relationship/entity/deleteBatch
 */
export async function deleteBatchEntity (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 图谱ID */
    atlasId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/entity/deleteBatch`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 修改关系 POST /api/v1/relationship/edit
 */
export async function editRelationship (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/edit`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 批量删除关系 POST /api/v1/relationship/deleteBatch
 */
export async function deleteBatch (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 图谱ID */
    atlasId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/relationship/deleteBatch`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 分页查询关系 GET /api/v1/relationship/page
 */
export async function pageRelationship (
  params?: {
    /** 当前页码 */
    pageNum?: number;
    /** 每页数量 */
    pageSize?: number;
    /** 图谱id */
    atlasId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListRelationshipVO>(`/api/v1/relationship/page`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取两个本体的关联 GET /api/v1/relationship/info
 */
export async function relationshipInfo (
  params?: {
    /** 关系id */
    id: string;
    /** 源本体id */
    sourceId: string;
    /** 目标本体id */
    targetId: string;
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

/**
 * 分页查询实体关系 GET /api/v1/relationship/entity/page
 */
export async function pageRelationshipEntity (
  params?: {
    /** 当前页码 */
    pageNum?: number;
    /** 每页数量 */
    pageSize?: number;
    /** 图谱id */
    atlasId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListRelationshipEntityVO>(`/api/v1/relationship/entity/page`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取可选的实体关系 GET /api/v1/relationship/entity/optional
 */
export async function relationshipEntityOptional (
  params?: {
    /** 源实体id */
    sourceId: string;
    /** 目标实体id */
    targetId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListRelationshipVO>(`/api/v1/relationship/entity/optional`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取实体关系信息 GET /api/v1/relationship/entity/info
 */
export async function relationshipEntityInfo (
  params?: {
    /** 关系id */
    id: string;
    /** 源id实体 */
    sourceId: string;
    /** 目标id实体 */
    targetId: string;
    /** 关系类型 */
    type: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultRelationshipVO>(`/api/v1/relationship/entity/info`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
