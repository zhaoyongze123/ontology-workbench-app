import { request } from 'umi'; 
import {
  PropertySaveVO,
  ApiResultVoid,
  ApiResultListJsonOntologyInfo,
  PropertyUpdateVO,
  ApiResultOntologyVO,
  ApiResultListOntologyVO,
} from './typings';

/**
 * 保存本体 PUT /api/v1/ontology/save
 */
export async function saveOntology (
  data?: {
    /** 本体名称 */
    name?: string;
    /** 本体描述 */
    description?: string;
    /** 图谱id */
    atlasId?: string;
    /** 属性列表 */
    properties?: PropertySaveVO[];
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/ontology/save`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 导入本体对象 POST /api/v1/ontology/upload/json
 */
export async function importOntology (
  data?: {
    /**  */
    file?: string;
    /**  */
    atlasId?: string;
  },
  options?: Record<string, any>,
) {
  const formData = new FormData();
  if (data?.file) {
    formData.append('file', data.file);
  }
  if (data?.atlasId) {
    formData.append('atlasId', data.atlasId);
  }
  return request<ApiResultVoid>(`/api/v1/ontology/upload/json`, {
    method: 'POST',
    data: formData,
    requestType: 'form',
    ...(options || {}),
  });
}

/**
 * 导出本体对象 POST /api/v1/ontology/export/json
 */
export async function exportOntology (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 图谱id */
    atlasId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListJsonOntologyInfo>(`/api/v1/ontology/export/json`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 修改 POST /api/v1/ontology/edit
 */
export async function ontologyEdit (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/ontology/edit`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 批量删除本体 POST /api/v1/ontology/deleteBatch
 */
export async function deleteBatchOntology (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 本体图谱ID */
    atlasId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/ontology/deleteBatch`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 获取本体对象信息 GET /api/v1/ontology/{id}
 */
export async function ontologyInfo (
  pathVars: {
    /** 本体id */
    id: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultOntologyVO>(`/api/v1/ontology/${pathVars.id}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/**
 * 分页查询本体 GET /api/v1/ontology/page
 */
export async function ontologyPage (
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
  return request<ApiResultListOntologyVO>(`/api/v1/ontology/page`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取可选的本体 GET /api/v1/ontology/optional
 */
export async function ontologyOptional (
  params?: {
    /** 图谱id */
    atlasId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListOntologyVO>(`/api/v1/ontology/optional`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
