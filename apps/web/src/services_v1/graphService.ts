import { request } from 'umi'; 
import {
  ApiResultVoid,
  ApiResultGraphVO,
  ApiResultListGraphVO,
  ApiResultListNodeVO,
  ApiResultJsonGraphInfo,
} from './typings';

/**
 * 修改图谱 PUT /api/v1/graph/edit
 */
export async function editGraph (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/graph/edit`, {
    method: 'PUT',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 保存图谱 POST /api/v1/graph/save
 */
export async function saveGraph (
  data?: {
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
  },
  options?: Record<string, any>,
) {
  return request<ApiResultGraphVO>(`/api/v1/graph/save`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 导入图谱JSON POST /api/v1/graph/import/json
 */
export async function importGraph (
  data?: {
    /** 文件 */
    file?: string;
    /** 项目id */
    organizationId?: string;
    /** 图谱类型 */
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
  return request<ApiResultVoid>(`/api/v1/graph/import/json`, {
    method: 'POST',
    data: formData,
    requestType: 'form',
    ...(options || {}),
  });
}

/**
 * 分页查询图谱 GET /api/v1/graph/page
 */
export async function pageGraph (
  params?: {
    /** 当前页码 */
    pageNum?: number;
    /** 每页数量 */
    pageSize?: number;
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
    /** 项目id */
    organizationId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListGraphVO>(`/api/v1/graph/page`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取实体图谱关联的本体图谱的节点列表 GET /api/v1/graph/link/getOntologyNodeList
 */
export async function getLinkOntologyNodeList (
  params?: {
    /** 实体图谱id */
    entityGraphId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListNodeVO>(`/api/v1/graph/link/getOntologyNodeList`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 获取图谱信息 GET /api/v1/graph/getInfo
 */
export async function getGraphInfo (
  params?: {
    /** 图谱id */
    graphId: string;
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultGraphVO>(`/api/v1/graph/getInfo`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 导出图谱JSON GET /api/v1/graph/export/json
 */
export async function exportGraph (
  params?: {
    /** 图谱id */
    graphId: string;
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultJsonGraphInfo>(`/api/v1/graph/export/json`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/**
 * 删除图谱 DELETE /api/v1/graph/delete
 */
export async function deleteGraph (
  params?: {
    /** 图谱id */
    graphId: string;
    /** 图谱类型 */
    graphType: ('ONTOLOGY' | 'ENTITY');
    /** 项目id */
    organizationId: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/graph/delete`, {
    method: 'DELETE',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
