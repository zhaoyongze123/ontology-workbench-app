import { request } from 'umi'; 
import {
  ApiResultAtlasVO,
  ApiResultVoid,
  ApiResultListAtlasVO,
  ApiResultJsonGraphInfo,
} from './typings';

/**
 * 保存图谱 PUT /api/v1/atlas/save
 */
export async function saveAtlas (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultAtlasVO>(`/api/v1/atlas/save`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 导入图谱 POST /api/v1/atlas/import/json
 */
export async function importGraphJson (
  data?: {
    /**  */
    file?: string;
    /**  */
    organizationId?: string;
    /**  */
    graphType?: string;
  },
  options?: Record<string, any>,
) {
  const formData = new FormData();
  if (data?.file) {
    formData.append('file', data.file);
  }
  if (data?.organizationId) {
    formData.append('organizationId', data.organizationId);
  }
  if (data?.graphType) {
    formData.append('graphType', data.graphType);
  }
  return request<ApiResultVoid>(`/api/v1/atlas/import/json`, {
    method: 'POST',
    data: formData,
    requestType: 'form',
    ...(options || {}),
  });
}

/**
 * 修改图谱 POST /api/v1/atlas/edit
 */
export async function edit (
  data?: {
    /** 图谱id */
    id?: string;
    /** 图谱名称 */
    name?: string;
    /** 图谱描述 */
    description?: string;
    /** 所属项目ID */
    organizationId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/atlas/edit`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 批量删除图谱 POST /api/v1/atlas/deleteBatch
 */
export async function deleteBatchAtlas (
  data?: {
    /** id列表 */
    idList?: string[];
    /** 图谱类型 */
    atlasType?: ('ONTOLOGY' | 'ENTITY');
    /** 项目ID */
    organizationId?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/atlas/deleteBatch`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 获取图谱对象信息 GET /api/v1/atlas/{id}
 */
export async function atlasInfo (
  pathVars: {
    /** 图谱id */
    id: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultAtlasVO>(`/api/v1/atlas/${pathVars.id}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/**
 * 分页查询图谱 GET /api/v1/atlas/page
 */
export async function atlasPage (
  params?: {
    /** 当前页码 */
    pageNum?: number;
    /** 每页数量 */
    pageSize?: number;
    /** 图谱类型 */
    atlasType: ('ONTOLOGY' | 'ENTITY');
    /** 项目id */
    organizationId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListAtlasVO>(`/api/v1/atlas/page`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取可选图谱 GET /api/v1/atlas/optional
 */
export async function atlasOptional (
  params?: {
    /** 图谱类型 */
    type: ('ONTOLOGY' | 'ENTITY');
    /** 项目id */
    organizationId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListAtlasVO>(`/api/v1/atlas/optional`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 导出图谱 GET /api/v1/atlas/export/json
 */
export async function exportGraphJson (
  params?: {
    /** 图谱id */
    ontologyGraphId: string;
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultJsonGraphInfo>(`/api/v1/atlas/export/json`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
