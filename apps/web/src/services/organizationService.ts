import { request } from 'umi'; 
import {
  ApiResultOrganizationVO,
  ApiResultVoid,
  ApiResultListOrganizationVO,
} from './typings';

/**
 * 新建项目 POST /api/v1/organization/save
 */
export async function saveOrganization (
  data?: {
    /** 名称 */
    name?: string;
    /**  */
    description?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultOrganizationVO>(`/api/v1/organization/save`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 修改项目 POST /api/v1/organization/edit
 */
export async function editOrganization (
  data?: {
    /** 项目id */
    id?: string;
    /** 项目名称 */
    name?: string;
    /** 项目描述 */
    description?: string;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/organization/edit`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 删除项目 POST /api/v1/organization/delete
 */
export async function deleteOrganization (
  data?: {
    /** id列表 */
    idList?: string[];
  },
  options?: Record<string, any>,
) {
  return request<ApiResultVoid>(`/api/v1/organization/delete`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}

/**
 * 分页查询项目 GET /api/v1/organization/page
 */
export async function organizationPage (
  params?: {
    /** 当前页码 */
    pageNum?: number;
    /** 每页数量 */
    pageSize?: number;
  },
  options?: Record<string, any>,
) {
  return request<ApiResultListOrganizationVO>(`/api/v1/organization/page`, {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
