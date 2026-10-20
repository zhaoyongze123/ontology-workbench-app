import { request } from 'umi'; 
import {
  ApiResultCollectionMapStringObject,
} from './typings';

/**
 * 图谱查询 POST /api/v1/cypher/query
 */
export async function query (
  data?: {
    /** Cypher语句 */
    cypher?: string;
    /** 图谱id */
    graphId?: string;
    /** 图谱类型 */
    graphType?: ('ONTOLOGY' | 'ENTITY');
  },
  options?: Record<string, any>,
) {
  return request<ApiResultCollectionMapStringObject>(`/api/v1/cypher/query`, {
    method: 'POST',
    data: {
      ...data,
    },
    ...(options || {}),
  });
}
