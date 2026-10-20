import { request } from 'umi'; 
import {
} from './typings';

/**
 *  GET /api/v1/template/ontology
 */
export async function ontologyTemplateDownload (
  options?: Record<string, any>,
) {
  return request<string[]>(`/api/v1/template/ontology`, {
    method: 'GET',
    ...(options || {}),
  });
}
