import { request } from 'umi'; 
import {
  PropertyImplSaveVO,
  ApiResultVoid,
  ApiResultListJsonEntityInfo,
  PropertyImplUpdateVO,
  ApiResultOntologyImplVO,
  ApiResultAtlasVO,
  ApiResultListOntologyVO,
} from './typings';

/**
 * 保存实体 PUT /api/v1/entity/saveEntity
 */
export async function saveEntity (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/entity/saveEntity`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 导入实体对象 POST /api/v1/entity/import/json
 */
export async function importEntity (
  data?: {
    /**  */
    file?: string;
    /**  */
    entityGraphId?: string;
  },
  options?: Record<string, any>,
) {
  const formData = new FormData();
  if (data?.file) {
    formData.append('file', data.file);
  }
  if (data?.entityGraphId) {
    formData.append('entityGraphId', data.entityGraphId);
  }
  return request<ApiResultVoid>(`/api/v1/entity/import/json`, {
    method: 'POST',
    data: formData,
    requestType: 'form',
    ...(options || {}),
  });
}

/**
 * 导出实体对象 POST /api/v1/entity/export/json
 */
export async function exportEntity (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 图谱id */
    atlasId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListJsonEntityInfo>(`/api/v1/entity/export/json`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 修改实体 POST /api/v1/entity/edit
 */
export async function editEntity (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/entity/edit`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 批量删除实体 POST /api/v1/entity/deleteBatch
 */
export async function deleteBatchEntity (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 实体图谱ID */
    atlasId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/entity/deleteBatch`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 获取实体信息 GET /api/v1/entity/{id}
 */
export async function entityInfo (
  pathVars: {
    /** 实体ID */
    id: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultOntologyImplVO>(`/api/v1/entity/${pathVars.id}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/**
 * 分页查询实体 GET /api/v1/entity/page
 */
export async function entityPage (
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
  return request<ApiResultAtlasVO>(`/api/v1/entity/page`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取实体关联的实体 GET /api/v1/entity/optional
 */
export async function entityOptional (
  params?: {
    /** 图谱ID */
    atlasId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListOntologyVO>(`/api/v1/entity/optional`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取实体关联的实体 GET /api/v1/entity/linkList
 */
export async function entityLinkList (
  params?: {
    /** 图谱ID */
    atlasId: string;
    /** 源实体ID */
    sourceId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListOntologyVO>(`/api/v1/entity/linkList`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
